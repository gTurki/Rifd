package com.example.rifd.DTOOut;

// preview of a web report before it is sent: what we heard (editable) + where we think it is.
// hasVoice && transcript == null -> the voice note could not be transcribed (the sender may type it)
// zoneCode == null -> needsLocation: ask the sender (they may still answer "I don't know")
public record ReportPreviewDTOOut(
        String draftId,
        boolean hasVoice,
        String transcript,
        String zoneCode,
        String zoneName,
        boolean zoneGuessed,
        boolean needsLocation
) {
}
