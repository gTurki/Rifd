package com.example.rifd.Service;

import com.example.rifd.DTOOut.AlertDTOOut;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public List<AlertDTOOut> search(AlertStatus status, Long incidentId) {
        return alertRepository.search(status, incidentId).stream().map(DTOMapper::toDTO).toList();
    }
}