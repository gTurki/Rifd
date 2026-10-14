package com.example.rifd.Repository;

import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.TaskStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByIncidentId(Long incidentId);

    List<Task> findByStaffId(Long staffId);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByStaffIdAndStatus(Long staffId, TaskStatus status);

    // to find all non-closed tasks
    List<Task> findByIncidentIdAndStatusNotIn(Long incidentId, Collection<TaskStatus> statuses);

    // to find overdue pending tasks then escalate them
    List<Task> findByStatusAndIsEscalatedFalseAndAssignedAtBefore(TaskStatus status, LocalDateTime before);

    // to find tasks by incident and status to assign them to a staff member
    List<Task> findByIncidentIdAndStatus(Long incidentId, TaskStatus status);

    List<Task> findByIncidentIdOrderByAssignedAtAsc(Long incidentId);

    List<Task> findByStatusIn(Collection<TaskStatus> statuses);

    List<Task> findByStaffIdAndStatusIn(Long staffId, Collection<TaskStatus> statuses);

    boolean existsByStaffIdAndStatusIn(Long staffId, Collection<TaskStatus> statuses);

    boolean existsByIncidentIdAndStaffIdAndStatusIn(Long incidentId, Long staffId, Collection<TaskStatus> statuses);

    long countByIncidentIdAndStatusIn(Long incidentId, Collection<TaskStatus> statuses);

    @Query("""
           SELECT t FROM Task t
           WHERE (:staffId IS NULL OR t.staff.id = :staffId)
             AND (:status IS NULL OR t.status = :status)
           ORDER BY t.assignedAt DESC
           """)
    List<Task> search(@Param("staffId") Long staffId, @Param("status") TaskStatus status);
}
