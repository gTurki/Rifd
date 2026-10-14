package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.AccessibilityAssetDTOOut;
import com.example.rifd.DTOOut.AccessibleRouteDTOOut;
import com.example.rifd.DTOOut.RouteAlternativeDTOOut;
import com.example.rifd.DTOOut.RouteSegmentDTOOut;
import com.example.rifd.Entity.AccessibilityAsset;
import com.example.rifd.Entity.AccessibleRoute;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.AssetStatus;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.NotificationType;
import com.example.rifd.Enum.RouteStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Events.IncidentAssetLinkedEvent;
import com.example.rifd.Events.IncidentResolvedEvent;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.AccessibilityAssetRepository;
import com.example.rifd.Repository.AccessibleRouteRepository;
import com.example.rifd.Repository.IncidentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccessibilityService {

    private static final List<IncidentStatus> CLOSED = List.of(IncidentStatus.RESOLVED, IncidentStatus.FALSE_ALARM);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final AccessibilityAssetRepository assetRepository;
    private final AccessibleRouteRepository routeRepository;
    private final IncidentRepository incidentRepository;
    private final RouteFinder routeFinder;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final BroadcastService broadcastService;
    private final AiClient aiClient;
    private final TransactionTemplate tx;

    // the route graph is global: one change at a time (listeners are async)
    private final ReentrantLock lock = new ReentrantLock(true);

    @Value("${accessibility.expected-restore-minutes:45}")
    private long expectedRestoreMinutes;

    public AccessibilityService(AccessibilityAssetRepository assetRepository, AccessibleRouteRepository routeRepository,
                                IncidentRepository incidentRepository, RouteFinder routeFinder,
                                ActivityLogService activityLogService, NotificationService notificationService,
                                BroadcastService broadcastService, AiClient aiClient,
                                PlatformTransactionManager transactionManager) {
        this.assetRepository = assetRepository;
        this.routeRepository = routeRepository;
        this.incidentRepository = incidentRepository;
        this.routeFinder = routeFinder;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
        this.broadcastService = broadcastService;
        this.aiClient = aiClient;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------- used when creating incidents ----------------

    @Transactional(readOnly = true)
    public Optional<AccessibilityAsset> findNearestAsset(Double x, Double y, Long zoneId, double radius) {
        if (x == null || y == null) return Optional.empty();

        if (zoneId != null) {
            Optional<AccessibilityAsset> inZone = nearest(assetRepository.findByZoneId(zoneId), x, y, radius);
            if (inZone.isPresent()) return inZone;
        }
        return nearest(assetRepository.findAll(), x, y, radius);
    }

    // ---------------- listeners ----------------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssetLinked(IncidentAssetLinkedEvent event) {
        try {
            locked(() -> tx.execute(s -> {
                Incident incident = incidentRepository.findById(event.incidentId()).orElse(null);
                if (incident == null || IncidentService.isClosed(incident.getStatus())) return null;
                AccessibilityAsset asset = getAsset(event.assetId());
                takeOutOfService(asset, AssetStatus.OUT_OF_SERVICE, incident, ActorType.SYSTEM, null);
                return null;
            }));
        } catch (Exception e) {
            log.error("Accessibility handling failed for incident #{}", event.incidentId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentResolved(IncidentResolvedEvent event) {
        try {
            locked(() -> tx.execute(s -> {
                Incident incident = incidentRepository.findById(event.incidentId()).orElse(null);
                if (incident == null || incident.getAccessibilityAsset() == null) return null;
                AccessibilityAsset asset = incident.getAccessibilityAsset();

                // a manual MAINTENANCE stays; and another open incident may still need it out of service
                if (asset.getStatus() != AssetStatus.OUT_OF_SERVICE) return null;
                if (incidentRepository.existsByAccessibilityAssetIdAndStatusNotInAndIdNot(asset.getId(), CLOSED, incident.getId())) {
                    activityLogService.log(EntityType.ASSET, asset.getId(), incident.getId(), ActionType.STATUS_CHANGED,
                            ActorType.SYSTEM, null, null, null,
                            asset.getName() + " stays out of service: another open incident uses it");
                    return null;
                }
                restore(asset, incident.getId(), ActorType.SYSTEM, null,
                        "Incident " + incident.getCode() + " closed as " + event.finalStatus());
                return null;
            }));
        } catch (Exception e) {
            log.error("Accessibility restore failed for incident #{}", event.incidentId(), e);
        }
    }

    // ---------------- API ----------------

    @Transactional(readOnly = true)
    public List<AccessibilityAssetDTOOut> getAssets() {
        return assetRepository.findAll().stream()
                .sorted(Comparator.comparing(AccessibilityAsset::getId))
                .map(DTOMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<AccessibleRouteDTOOut> getRoutes() {
        return routeRepository.findAllWithAssets().stream().map(DTOMapper::toDTO).toList();
    }

    // manual change (field report / plan B for the demo); same logic as the automatic one
    public AccessibilityAssetDTOOut updateAssetStatus(Long assetId, AssetStatus status, Long actorId) {
        return locked(() -> tx.execute(s -> {
            AccessibilityAsset asset = getAsset(assetId);
            if (asset.getStatus() == status) return DTOMapper.toDTO(asset);

            if (status == AssetStatus.OPERATIONAL) {
                restore(asset, null, ActorType.HUMAN, actorId, "Manual change");
            } else {
                takeOutOfService(asset, status, null, ActorType.HUMAN, actorId);
            }
            return DTOMapper.toDTO(asset);
        }));
    }

    @Transactional(readOnly = true)
    public RouteAlternativeDTOOut getAlternative(Long routeId) {
        List<AccessibleRoute> routes = routeRepository.findAllWithAssets();
        AccessibleRoute route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst()
                .orElseThrow(() -> new ApiException("Route not found"));

        if (RouteFinder.isUsable(route)) { // the route itself works: it is its own "alternative"
            RouteSegmentDTOOut self = new RouteSegmentDTOOut(route.getId(), route.getName(), route.getFromZone().getId(),
                    route.getToZone().getId(), route.getPathCoordinates(), route.getLengthMeters());
            return new RouteAlternativeDTOOut(route.getId(), route.getStatus(), true, List.of(self),
                    route.getLengthMeters(), route.getEstimatedMinutes(), route.getPublicMessageAr(), route.getPublicMessageEn());
        }

        Optional<RouteFinder.Path> path = routeFinder.findPath(routes, route.getFromZone().getId(), route.getToZone().getId());
        String[] messages = StringUtils.hasText(route.getPublicMessageAr())
                ? new String[]{route.getPublicMessageAr(), route.getPublicMessageEn()}
                : templateMessages(route, path);
        return new RouteAlternativeDTOOut(route.getId(), route.getStatus(), path.isPresent(),
                path.map(p -> p.legs().stream().map(AccessibilityService::toSegment).toList()).orElse(List.of()),
                path.map(RouteFinder.Path::totalMeters).orElse(null),
                path.map(RouteFinder.Path::estimatedMinutes).orElse(null),
                messages[0], messages[1]);
    }

    // used by /simulation/reset
    @Transactional
    public void resetAll() {
        for (AccessibleRoute r : routeRepository.findAll()) {
            r.setStatus(RouteStatus.ACTIVE);
            r.setAlternativeRoute(null);
            r.setPublicMessageAr(null);
            r.setPublicMessageEn(null);
            r.setLastUpdated(LocalDateTime.now());
        }
        for (AccessibilityAsset a : assetRepository.findAll()) {
            a.setStatus(AssetStatus.OPERATIONAL);
            a.setLastStatusChange(null);
            a.setExpectedRestoreTime(null);
        }
    }

    // ---------------- core ----------------

    private void takeOutOfService(AccessibilityAsset asset, AssetStatus status, Incident incident,
                                  ActorType actorType, Long actorId) {
        Long incidentId = incident == null ? null : incident.getId();
        AssetStatus old = asset.getStatus();

        if (old != status) {
            asset.setStatus(status);
            asset.setLastStatusChange(LocalDateTime.now());
            asset.setExpectedRestoreTime(LocalDateTime.now().plusMinutes(expectedRestoreMinutes));
            assetRepository.save(asset);
            activityLogService.log(EntityType.ASSET, asset.getId(), incidentId, ActionType.STATUS_CHANGED, actorType, actorId,
                    old.name(), status.name(), asset.getName() + (incident == null ? "" : " (" + incident.getCode() + ")"));
            broadcastService.send(BroadcastService.ROUTES, "ASSET_UPDATED", DTOMapper.toDTO(asset));
        }

        // the accessibility team is told even if the asset was already down (new incident on it)
        notificationService.notifySpecialty(StaffSpecialty.ACCESSIBILITY, incidentId, NotificationType.ACCESSIBILITY_ALERT,
                asset.getName() + " is " + status + (incident == null ? "" : " (" + incident.getCode() + ")")
                        + (asset.getExpectedRestoreTime() == null ? ""
                        : ". Expected back around " + asset.getExpectedRestoreTime().format(HH_MM)),
                incident == null || incident.getSeverity() == null ? Severity.MEDIUM : incident.getSeverity());

        refreshRoutes(incidentId, actorType, actorId);
    }

    private void restore(AccessibilityAsset asset, Long incidentId, ActorType actorType, Long actorId, String reason) {
        AssetStatus old = asset.getStatus();
        asset.setStatus(AssetStatus.OPERATIONAL);
        asset.setLastStatusChange(LocalDateTime.now());
        asset.setExpectedRestoreTime(null);
        assetRepository.save(asset);
        activityLogService.log(EntityType.ASSET, asset.getId(), incidentId, ActionType.STATUS_CHANGED, actorType, actorId,
                old.name(), AssetStatus.OPERATIONAL.name(), asset.getName() + " back in service: " + reason);
        broadcastService.send(BroadcastService.ROUTES, "ASSET_UPDATED", DTOMapper.toDTO(asset));

        refreshRoutes(incidentId, actorType, actorId);
    }

    // recompute EVERY route: a route is DISRUPTED while any of its assets is not operational, and the
    // alternatives of all disrupted routes are recomputed (an earlier alternative may have just broken,
    // or a better one may be back)
    private void refreshRoutes(Long incidentId, ActorType actorType, Long actorId) {
        List<AccessibleRoute> routes = routeRepository.findAllWithAssets();

        for (AccessibleRoute route : routes) {
            RouteStatus oldStatus = route.getStatus();

            if (RouteFinder.isUsable(route)) {
                if (oldStatus != RouteStatus.DISRUPTED) continue;
                route.setStatus(RouteStatus.ACTIVE);
                route.setAlternativeRoute(null);
                route.setPublicMessageAr(null);
                route.setPublicMessageEn(null);
                route.setLastUpdated(LocalDateTime.now());
                routeRepository.save(route);
                logRoute(route, incidentId, actorType, actorId, oldStatus, "Route restored");
                notificationService.notifySpecialty(StaffSpecialty.ACCESSIBILITY, incidentId, NotificationType.ROUTE_CHANGE,
                        "Accessible route '" + route.getName() + "' is open again", Severity.LOW);
                broadcastService.send(BroadcastService.ROUTES, "ROUTE_UPDATED", DTOMapper.toDTO(route));
                continue;
            }

            Optional<RouteFinder.Path> path = routeFinder.findPath(routes, route.getFromZone().getId(), route.getToZone().getId());
            AccessibleRoute newAlt = path.filter(p -> !p.legs().isEmpty()).map(p -> p.legs().get(0).route()).orElse(null);
            boolean altChanged = !Objects.equals(idOf(route.getAlternativeRoute()), idOf(newAlt));
            if (oldStatus == RouteStatus.DISRUPTED && !altChanged && StringUtils.hasText(route.getPublicMessageAr())) {
                continue; // nothing new for this route
            }

            String[] messages = buildMessages(route, path);
            route.setStatus(RouteStatus.DISRUPTED);
            route.setAlternativeRoute(newAlt);
            route.setPublicMessageAr(messages[0]);
            route.setPublicMessageEn(messages[1]);
            route.setLastUpdated(LocalDateTime.now());
            routeRepository.save(route);

            String altText = path.map(p -> describe(p) + String.format(" (%.0f m, ~%d min)", p.totalMeters(), p.estimatedMinutes()))
                    .orElse("no accessible alternative");
            logRoute(route, incidentId, actorType, actorId, oldStatus, "Alternative: " + altText);
            notificationService.notifySpecialty(StaffSpecialty.ACCESSIBILITY, incidentId, NotificationType.ROUTE_CHANGE,
                    "Accessible route '" + route.getName() + "' disrupted. " + "Alternative: " + altText, Severity.MEDIUM);
            if (path.isEmpty()) {
                notificationService.notifySupervisors(incidentId,
                        "No accessible alternative for '" + route.getName() + "': send visitors to the visitor services desk",
                        Severity.HIGH);
            }
            broadcastService.send(BroadcastService.ROUTES, "ROUTE_UPDATED", DTOMapper.toDTO(route));
        }
    }

    private void logRoute(AccessibleRoute route, Long incidentId, ActorType actorType, Long actorId,
                          RouteStatus oldStatus, String details) {
        activityLogService.log(EntityType.ROUTE, route.getId(), incidentId, ActionType.STATUS_CHANGED, actorType, actorId,
                oldStatus.name(), route.getStatus().name(), route.getName() + ": " + details);
    }

    // AI wording if available, otherwise the fixed bilingual template
    private String[] buildMessages(AccessibleRoute route, Optional<RouteFinder.Path> path) {
        String broken = route.getAssets().stream().filter(a -> a.getStatus() != AssetStatus.OPERATIONAL)
                .map(AccessibilityAsset::getName).collect(Collectors.joining(", "));
        if (path.isPresent()) {
            Optional<AiClient.RouteMessageResponse> ai = aiClient.routeMessage(new AiClient.RouteMessageRequest(
                    broken, route.getFromZone().getName(), route.getToZone().getName(), describe(path.get())));
            if (ai.isPresent() && StringUtils.hasText(ai.get().ar()) && StringUtils.hasText(ai.get().en())) {
                return new String[]{ai.get().ar().trim(), ai.get().en().trim()};
            }
        }
        return templateMessages(route, path);
    }

    private static String[] templateMessages(AccessibleRoute route, Optional<RouteFinder.Path> path) {
        String from = route.getFromZone().getName();
        String to = route.getToZone().getName();
        if (path.isEmpty()) {
            return new String[]{
                    "المسار المهيأ من " + from + " إلى " + to + " متوقف مؤقتًا ولا يوجد مسار بديل مهيأ حاليًا. يرجى التوجه لأقرب مكتب خدمة الزوار للمساعدة.",
                    "The accessible route from " + from + " to " + to + " is temporarily closed and no accessible alternative is available right now. Please visit the nearest visitor services desk for help."
            };
        }
        RouteFinder.Path p = path.get();
        String alt = describe(p);
        return new String[]{
                "المسار المهيأ من " + from + " إلى " + to + " متوقف مؤقتًا. يرجى استخدام " + alt + " (حوالي " + p.estimatedMinutes() + " دقائق).",
                "The accessible route from " + from + " to " + to + " is temporarily closed. Please use " + alt + " (about " + p.estimatedMinutes() + " min)."
        };
    }

    private static String describe(RouteFinder.Path path) {
        return path.legs().stream().map(l -> l.route().getName()).collect(Collectors.joining(" → "));
    }

    private static RouteSegmentDTOOut toSegment(RouteFinder.Leg leg) {
        AccessibleRoute r = leg.route();
        return new RouteSegmentDTOOut(r.getId(), r.getName(), leg.fromZoneId(), leg.toZoneId(),
                r.getPathCoordinates(), r.getLengthMeters());
    }

    // ---------------- helpers ----------------

    private AccessibilityAsset getAsset(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> new ApiException("Asset not found"));
    }

    private <T> T locked(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    private static Long idOf(AccessibleRoute route) {
        return route == null ? null : route.getId();
    }

    private Optional<AccessibilityAsset> nearest(List<AccessibilityAsset> assets, double x, double y, double radius) {
        return assets.stream()
                .filter(a -> a.getX() != null && a.getY() != null)
                .filter(a -> distance(a.getX(), a.getY(), x, y) <= radius)
                .min(Comparator.comparingDouble(a -> distance(a.getX(), a.getY(), x, y)));
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }
}
