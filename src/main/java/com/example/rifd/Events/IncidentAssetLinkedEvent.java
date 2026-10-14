package com.example.rifd.Events;

// published when an accessibility asset gets linked to an incident (on create OR on a later merge)
public record IncidentAssetLinkedEvent(Long incidentId, Long assetId) {
}
