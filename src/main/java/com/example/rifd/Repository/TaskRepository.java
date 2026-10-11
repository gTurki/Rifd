package com.example.rifd.Repository;

import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.TaskStatus;
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
}