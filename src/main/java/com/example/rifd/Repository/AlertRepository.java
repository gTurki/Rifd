package com.example.rifd.Repository;

import com.example.rifd.Entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
}

