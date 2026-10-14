package com.example.rifd.Service;

import com.example.rifd.Entity.AccessibleRoute;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Enum.AssetStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

// Dijkstra over zones (NOT AI). Nodes = zones, edges = accessible routes whose assets all work, both directions.
@Component
public class RouteFinder {

    // one leg, in the direction it is walked
    public record Leg(AccessibleRoute route, Long fromZoneId, Long toZoneId) {
    }

    public record Path(List<Leg> legs, double totalMeters, int estimatedMinutes) {
    }

    private record Edge(AccessibleRoute route, Long to, double weight) {
    }

    private record Node(Long zoneId, double dist) {
    }

    // wheelchair-friendly pace used only when a route has no estimatedMinutes
    private static final double ACCESSIBLE_SPEED_MPS = 1.0;

    // routes must be loaded WITH their assets (AccessibleRouteRepository.findAllWithAssets)
    public static boolean isUsable(AccessibleRoute route) {
        return route.getAssets().stream().allMatch(a -> a.getStatus() == AssetStatus.OPERATIONAL);
    }

    public Optional<Path> findPath(List<AccessibleRoute> routes, Long fromZoneId, Long toZoneId) {
        if (Objects.equals(fromZoneId, toZoneId)) return Optional.of(new Path(List.of(), 0, 0));

        Map<Long, List<Edge>> graph = new HashMap<>();
        for (AccessibleRoute r : routes) {
            if (!isUsable(r)) continue;
            Long a = r.getFromZone().getId();
            Long b = r.getToZone().getId();
            double w = weight(r);
            graph.computeIfAbsent(a, k -> new ArrayList<>()).add(new Edge(r, b, w));
            graph.computeIfAbsent(b, k -> new ArrayList<>()).add(new Edge(r, a, w));
        }

        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Leg> previous = new HashMap<>();
        Set<Long> done = new HashSet<>();
        PriorityQueue<Node> queue = new PriorityQueue<>((x, y) -> Double.compare(x.dist(), y.dist()));
        dist.put(fromZoneId, 0.0);
        queue.add(new Node(fromZoneId, 0));

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (!done.add(node.zoneId())) continue;
            if (node.zoneId().equals(toZoneId)) break;

            for (Edge e : graph.getOrDefault(node.zoneId(), List.of())) {
                double candidate = node.dist() + e.weight();
                if (candidate < dist.getOrDefault(e.to(), Double.MAX_VALUE)) {
                    dist.put(e.to(), candidate);
                    previous.put(e.to(), new Leg(e.route(), node.zoneId(), e.to()));
                    queue.add(new Node(e.to(), candidate));
                }
            }
        }
        if (!dist.containsKey(toZoneId)) return Optional.empty();

        List<Leg> legs = new ArrayList<>();
        for (Long at = toZoneId; !at.equals(fromZoneId); at = previous.get(at).fromZoneId()) {
            legs.add(previous.get(at));
        }
        Collections.reverse(legs);

        int minutes = legs.stream().mapToInt(l -> l.route().getEstimatedMinutes() != null
                ? l.route().getEstimatedMinutes()
                : (int) Math.ceil(weight(l.route()) / ACCESSIBLE_SPEED_MPS / 60.0)).sum();
        return Optional.of(new Path(legs, Math.round(dist.get(toZoneId) * 10) / 10.0, minutes));
    }

    // lengthMeters, or the straight line between the zone centers when it is missing
    private static double weight(AccessibleRoute r) {
        if (r.getLengthMeters() != null && r.getLengthMeters() > 0) return r.getLengthMeters();
        Zone a = r.getFromZone();
        Zone b = r.getToZone();
        if (a.getCenterX() != null && a.getCenterY() != null && b.getCenterX() != null && b.getCenterY() != null) {
            return AccessibilityService.distance(a.getCenterX(), a.getCenterY(), b.getCenterX(), b.getCenterY());
        }
        return 1000; // unknown: usable but discouraged
    }
}
