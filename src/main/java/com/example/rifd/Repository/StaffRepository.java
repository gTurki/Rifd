package com.example.rifd.Repository;

import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByUsername(String username);

    List<Staff> findByRole(StaffRole role);

    // to get all staff, shift & distance filters will be in the service layer
    List<Staff> findByRoleAndStatusAndSpecialtyAndLevel(StaffRole role, StaffStatus status,
                                                        StaffSpecialty specialty, StaffLevel level);
}