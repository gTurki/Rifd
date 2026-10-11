package com.example.rifd.Repository;

import com.example.rifd.Entity.Playbook;
import com.example.rifd.Enum.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaybookRepository extends JpaRepository<Playbook, Long> {

    List<Playbook> findByCategoryAndIsActiveTrue(IncidentCategory category);

    List<Playbook> findByIsActiveTrue();
}