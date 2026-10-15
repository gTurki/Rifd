package com.example.rifd.Repository;

import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByUsername(String username);

    List<Staff> findByRole(StaffRole role);

    // to get all staff, shift & distance filters will be in the service layer
    List<Staff> findByRoleAndStatusAndSpecialtyAndLevel(StaffRole role, StaffStatus status,
                                                        StaffSpecialty specialty, StaffLevel level);

    List<Staff> findByRoleAndStatusAndSpecialty(StaffRole role, StaffStatus status, StaffSpecialty specialty);

    @Query("""
           SELECT s FROM Staff s
           WHERE (:status IS NULL OR s.status = :status)
             AND (:specialty IS NULL OR s.specialty = :specialty)
             AND (:role IS NULL OR s.role = :role)
           ORDER BY s.id
           """)
    List<Staff> search(@Param("status") StaffStatus status,
                       @Param("specialty") StaffSpecialty specialty,
                       @Param("role") StaffRole role);

    // atomic claim: only succeeds if the staff member is still in the expected status (two operators,
    // or the escalation job, can never take the same person). Returns 1 on success, 0 if someone was faster.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Staff s SET s.status = :to, s.version = s.version + 1 WHERE s.id = :id AND s.status = :from")
    int transitionStatus(@Param("id") Long id, @Param("from") StaffStatus from, @Param("to") StaffStatus to);

    // /simulation/reset: bulk update (bumps version) so a running scheduler can never make the reset fail
    @Modifying(flushAutomatically = true)
    @Query("""
           UPDATE Staff s SET s.status = :status, s.currentX = :x, s.currentY = :y,
                  s.lastLocationUpdate = :now, s.version = s.version + 1
           WHERE s.id = :id
           """)
    int resetStaff(@Param("id") Long id, @Param("status") StaffStatus status,
                   @Param("x") Double x, @Param("y") Double y, @Param("now") LocalDateTime now);

    boolean existsByEmployeeNumber(String employeeNumber);
}
