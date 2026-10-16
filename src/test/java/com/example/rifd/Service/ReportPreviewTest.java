package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.ReportConfirmDTOIn;
import com.example.rifd.Entity.MediaFile;
import com.example.rifd.Repository.MediaFileRepository;
import com.example.rifd.Repository.ZoneRepository;
import com.example.rifd.Telegram.GeminiGatekeeper;
import com.example.rifd.Telegram.GeminiGatekeeper.Transcription;
import com.example.rifd.Telegram.GeminiGatekeeper.VoiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportPreviewTest {

    private MediaFileRepository files;
    private GeminiGatekeeper gemini;
    private MediaReportService media;
    private ReportDraftService drafts;

    @BeforeEach
    void setUp() {
        files = mock(MediaFileRepository.class);
        when(files.save(any(MediaFile.class))).thenAnswer(inv -> {
            MediaFile f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        gemini = mock(GeminiGatekeeper.class);
        VisionClient vision = mock(VisionClient.class);
        media = new MediaReportService(files, gemini, vision);
        drafts = new ReportDraftService(media, gemini, mock(ZoneRepository.class));
    }

    private static MockMultipartFile wav(int bytes) {
        return new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[bytes]);
    }

    @Test
    void silentRecordingIsRejectedAndNothingIsStored() {
        assertThrows(ApiException.class, () -> media.analyze(null, null, wav(44), "t"));
        verify(gemini, never()).transcribeVoice(any(), anyString());
        verify(files, never()).save(any());
    }

    @Test
    void noSpeechIsRejectedAndNothingIsStored() {
        when(gemini.transcribeVoice(any(), anyString())).thenReturn(new Transcription(VoiceStatus.NO_SPEECH, null));
        assertThrows(ApiException.class, () -> media.analyze(null, null, wav(64_000), "t"));
        verify(files, never()).save(any());
    }

    // Gemini down: we cannot tell if anyone spoke -> the report goes on with the file attached
    @Test
    void transcriptionFailureKeepsTheReport() {
        when(gemini.transcribeVoice(any(), anyString())).thenReturn(new Transcription(VoiceStatus.FAILED, null));
        MediaReportService.Result r = media.process(null, null, wav(64_000), "t");
        assertTrue(r.text().contains("تعذّر تفريغه"));
        assertEquals("up:1", r.attachmentUrl());
    }

    @Test
    void confirmUsesTheCorrectedTranscriptOnce() {
        when(gemini.transcribeVoice(any(), anyString())).thenReturn(new Transcription(VoiceStatus.HEARD, "دخان عند البوابه"));
        ReportDraftService.Draft d = drafts.create(ReportDraftService.Kind.VISITOR, null, "ar", null, null, "MP",
                null, null, wav(64_000));

        ReportDraftService.Confirmed c = drafts.confirm(ReportDraftService.Kind.VISITOR,
                new ReportConfirmDTOIn(d.id(), "دخان عند البوابة 1", "mp"));
        assertEquals("التفريغ الصوتي: دخان عند البوابة 1", c.text());
        assertEquals("MP", c.zoneCode());

        // a draft is used once, and only by its own page
        assertThrows(ApiException.class, () -> drafts.confirm(ReportDraftService.Kind.VISITOR,
                new ReportConfirmDTOIn(d.id(), null, null)));
        ReportDraftService.Draft other = drafts.create(ReportDraftService.Kind.STAFF, "text", "ar", null, null, "MP",
                "ref", null, null);
        assertThrows(ApiException.class, () -> drafts.confirm(ReportDraftService.Kind.VISITOR,
                new ReportConfirmDTOIn(other.id(), null, null)));
    }

    @Test
    void blankCorrectionKeepsWhatWeHeard() {
        when(gemini.transcribeVoice(any(), anyString())).thenReturn(new Transcription(VoiceStatus.HEARD, "زحمة"));
        ReportDraftService.Draft d = drafts.create(ReportDraftService.Kind.STAFF, "بلاغ", "ar", null, null, null,
                null, List.of(), wav(64_000));
        ReportDraftService.Confirmed c = drafts.confirm(ReportDraftService.Kind.STAFF,
                new ReportConfirmDTOIn(d.id(), "  ", null));
        assertEquals("بلاغ\nالتفريغ الصوتي: زحمة", c.text());
    }
}
