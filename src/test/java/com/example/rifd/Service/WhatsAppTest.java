package com.example.rifd.Service;

import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Repository.StaffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void normalizeSaudiFormats() {
        assertEquals("+966549931017", WhatsAppClient.normalize("0549931017"));
        assertEquals("+966549931017", WhatsAppClient.normalize("054 993 1017"));
        assertEquals("+966549931017", WhatsAppClient.normalize("+966549931017"));
        assertEquals("+966549931017", WhatsAppClient.normalize("966549931017"));
        assertEquals("+966549931017", WhatsAppClient.normalize("00966549931017"));
        // typed without the leading 0: still a Saudi mobile, not +54 (Argentina)
        assertEquals("+966549931017", WhatsAppClient.normalize("549931017"));
        assertNull(WhatsAppClient.normalize("   "));
        assertNull(WhatsAppClient.normalize("12345"));
    }

    // the data is already committed: a failure handing the message over must not reach the caller
    @Test
    void afterCommitFailureDoesNotPropagate() {
        WhatsAppClient client = mock(WhatsAppClient.class);
        when(client.isEnabled()).thenReturn(true);
        doThrow(new RuntimeException("executor rejected")).when(client).send(anyString(), anyString());
        WhatsAppService service = new WhatsAppService(client, mock(StaffRepository.class), "");

        TransactionSynchronizationManager.initSynchronization();
        service.taskAssigned(staff("0549931017"), highIncident(1L));

        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit));
    }

    @Test
    void taskAssignedOncePerPhonePerIncident() {
        WhatsAppClient client = mock(WhatsAppClient.class);
        when(client.isEnabled()).thenReturn(true);
        WhatsAppService service = new WhatsAppService(client, mock(StaffRepository.class), "");

        service.taskAssigned(staff("0549931017"), highIncident(1L));
        service.taskAssigned(staff("+966549931017"), highIncident(1L)); // same number, same incident
        service.taskAssigned(staff("0549931017"), highIncident(2L));    // another incident

        verify(client, times(2)).send(anyString(), anyString());
    }

    private static Staff staff(String phone) {
        Staff s = new Staff();
        s.setName("Test");
        s.setPhone(phone);
        return s;
    }

    private static Incident highIncident(Long id) {
        Incident i = new Incident();
        i.setId(id);
        i.setCode("INC-" + id);
        i.setSeverity(Severity.HIGH);
        return i;
    }
}
