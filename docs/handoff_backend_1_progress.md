# Developer 1 — Progress Handoff

**Status:** Phase 1 complete; Phase 2 started. No callable CRUD endpoints yet.

**Scope:** Zone, Event, Alert, Incident, ActivityLog. Full requirements: [original handoff](handoff_backend_1_intake.md).

## Completed

- Added five JPA repositories with inherited database CRUD operations.
- Added five service and controller scaffolds with constructor injection and `/api` base paths. They have no business methods yet.
- Added five response DTOs as Java records; relationships are represented by IDs.
- Added `IntakeMapper` to convert entities into response DTOs.
- Filled the existing global `ControllerAdvice`, including validation handling.
- Removed the duplicate advice, error DTO, and custom intake exceptions. Use existing `ApiException` for business errors.
- Clean build and 17 HTTP error-handler tests passed using JDK 21, targeting Java 17. MySQL/application startup has not been tested.

## Remaining

1. **Phase 2:** Confirm CRUD scope; add request DTOs, validation, repository queries, service methods, endpoints, and tests.
2. **Phase 3:** Incident acknowledgement, severity/status changes, resolution/false alarms, activity reads, and `ActivityLogService.log(...)`.
3. **Phase 4:** Alert classification, AI timeout/fallback, merging, event severity boost, accessibility linkage, and integration events.
4. **Phase 5:** WebSocket publishing, statistics, zone/event seed data, simulation, coordinated reset, and full demo verification.

**Pending CRUD proposal (not confirmed):** full CRUD for Zones/Events; create/read Alerts; read-only Incidents/ActivityLogs until workflow endpoints are implemented.

## Decisions and Working Rules

- Keep Spring Boot 4.1.1, MySQL, and current layer-based packages.
- **User override:** all advice handlers return HTTP **400** with `{ "message": "..." }`, replacing the original handoff's error format/status approach.
- Make changes only after user approval; ask about important ambiguities.
- Coordinate shared model/contract changes with Developer 2. Use their nearest-asset service; response/dispatch/routing work belongs to them.
- Before integration: settle bilingual fallback merge scoring, manual severity overrides/event boosting, decision counters, and simulation reset coordination. Derive the latest alert time; `Incident` has no `lastAlertAt` field.

## Verification

Set `JAVA_HOME` to JDK 21, then run:

```sh
sh mvnw clean -Dtest=ControllerAdviceTests test
```

The default JDK 27 failed the baseline compile. The command above tests HTTP error handling without starting MySQL or the application.
