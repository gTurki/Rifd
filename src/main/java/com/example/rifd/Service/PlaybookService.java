package com.example.rifd.Service;

import com.example.rifd.DTOOut.PlaybookDTOOut;
import com.example.rifd.Entity.Playbook;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.PlaybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaybookService {

    private final PlaybookRepository playbookRepository;

    @Transactional(readOnly = true)
    public List<PlaybookDTOOut> getAll(Boolean active) {
        List<Playbook> playbooks = Boolean.TRUE.equals(active)
                ? playbookRepository.findByIsActiveTrue()
                : playbookRepository.findAll();
        return playbooks.stream()
                .sorted(Comparator.comparing(Playbook::getCategory).thenComparing(p -> p.getMinSeverity().ordinal()))
                .map(DTOMapper::toDTO)
                .toList();
    }

    // active playbooks of the category whose minSeverity <= severity (compared by ordinal)
    @Transactional(readOnly = true)
    public List<Playbook> applicable(IncidentCategory category, Severity severity) {
        int level = severity == null ? 0 : severity.ordinal();
        return playbookRepository.findByCategoryAndIsActiveTrue(category).stream()
                .filter(p -> p.getMinSeverity() == null || p.getMinSeverity().ordinal() <= level)
                .sorted(Comparator.comparingInt((Playbook p) -> p.getMinSeverity() == null ? 0 : p.getMinSeverity().ordinal())
                        .reversed())
                .toList();
    }

    // the most specific one = highest minSeverity
    @Transactional(readOnly = true)
    public Optional<Playbook> best(IncidentCategory category, Severity severity) {
        return applicable(category, severity).stream().findFirst();
    }

    // who handles a category when there is no specific playbook (the generic OTHER playbook must not
    // send a visitor-services person to a fire)
    public static StaffSpecialty defaultSpecialty(IncidentCategory category) {
        if (category == null) return StaffSpecialty.VISITOR_SERVICES;
        return switch (category) {
            case CROWD -> StaffSpecialty.CROWD_MANAGEMENT;
            case MEDICAL -> StaffSpecialty.MEDICAL;
            case FIRE -> StaffSpecialty.FIRE_SAFETY;
            case SECURITY -> StaffSpecialty.SECURITY;
            case TECHNICAL_FAILURE, ACCESSIBILITY -> StaffSpecialty.TECHNICAL;
            case LOST_PERSON, CLEANLINESS, OTHER -> StaffSpecialty.VISITOR_SERVICES;
        };
    }
}
