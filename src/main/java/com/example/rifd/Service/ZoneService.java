package com.example.rifd.Service;

import com.example.rifd.DTOOut.ZoneDTOOut;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<ZoneDTOOut> getAll() {
        return zoneRepository.findAllByOrderByCodeAsc().stream().map(DTOMapper::toDTO).toList();
    }
}