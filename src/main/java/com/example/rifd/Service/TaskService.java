package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.TaskDTOOut;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.*;
import com.example.rifd.Events.IncidentResolvedEvent;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.TaskRepository;
import com.example.rifd.Security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final IncidentService incidentService;
    private final StaffService staffService;
    private final AssignmentService assignmentService;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;
    private final CurrentUser currentUser;
    private final NotificationService notificationService;

    // ---------------- read ----------------

    @Transactional(readOnly = true)
    public List<TaskDTOOut> search(Long staffId, TaskStatus status) {
        // field staff only ever see their own tasks
        Long effectiveStaffId = currentUser.isField() ? currentUser.staffId().orElse(staffId) : staffId;
        return taskRepository.search(effectiveStaffId, status).stream().map(DTOMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDTOOut> byIncident(Long incidentId) {
        incidentService.getIncident(incidentId); // 400 if missing
        return taskRepository.findByIncidentIdOrderByAssignedAtAsc(incidentId).stream().map(DTOMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public TaskDTOOut getById(Long taskId) {
        Task task = getTask(taskId);
        currentUser.requireSelfIfField(task.getStaff().getId());
        return DTOMapper.toDTO(task);
    }

    // ---------------- staff actions ----------------

    @Transactional
    public TaskDTOOut accept(Long taskId, Long requestedActorId) {
        Task task = getActionableTask(taskId);
        Long actorId = actorFor(task, requestedActorId);

        if (task.getStatus() == TaskStatus.ACCEPTED) return DTOMapper.toDTO(task); // idempotent
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new ApiException("Only a pending task can be accepted");
        }
        task.setStatus(TaskStatus.ACCEPTED);
        task.setAcceptedAt(LocalDateTime.now());
        save(task, ActionType.ACCEPTED, ActorType.HUMAN, actorId, TaskStatus.PENDING, "Staff accepted the task");
        return DTOMapper.toDTO(task);
    }

    // PENDING or ACCEPTED (not yet moving) -> REJECTED, staff released, nearest next person assigned
    @Transactional
    public TaskDTOOut reject(Long taskId, String reason, Long requestedActorId) {
        Task task = getActionableTask(taskId);
        Long actorId = actorFor(task, requestedActorId);

        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.ACCEPTED) {
            throw new ApiException("Only a pending or accepted task can be rejected");
        }
        TaskStatus old = task.getStatus();
        task.setStatus(TaskStatus.REJECTED);
        task.setRejectionReason(reason.trim());
        save(task, ActionType.REJECTED, ActorType.HUMAN, actorId, old, "Rejected: " + reason.trim());

        staffService.releaseIfIdle(task.getStaff());
        assignmentService.reassign(task, "rejected by staff");
        incidentService.reopenIfUnstaffed(task.getIncident().getId());
        return DTOMapper.toDTO(task);
    }

    // EN_ROUTE / ON_SITE / DONE
    @Transactional
    public TaskDTOOut updateStatus(Long taskId, TaskStatus newStatus, String notes, Long requestedActorId) {
        Task task = getActionableTask(taskId);
        Long actorId = actorFor(task, requestedActorId);
        return DTOMapper.toDTO(move(task, newStatus, notes, ActorType.HUMAN, actorId));
    }

    // operator pulls a staff member off a task (any open state), e.g. severity lowered or needed elsewhere
    @Transactional
    public TaskDTOOut cancel(Long taskId, String reason, Long requestedActorId) {
        Task task = getActionableTask(taskId);
        Long actorId = currentUser.actorId(requestedActorId);
        incidentService.validateActor(actorId);

        TaskStatus old = task.getStatus();
        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        task.setRejectionReason(reason.trim());
        save(task, ActionType.STATUS_CHANGED, ActorType.HUMAN, actorId, old, "Cancelled by operator: " + reason.trim());

        staffService.releaseIfIdle(task.getStaff());
        notificationService.notifyStaff(task.getStaff().getId(), task.getIncident().getId(), NotificationType.TASK_CANCELLED,
                "Task " + task.getIncident().getCode() + " cancelled: " + reason.trim(), Severity.LOW);
        incidentService.reopenIfUnstaffed(task.getIncident().getId());
        return DTOMapper.toDTO(task);
    }

    // ---------------- system actions ----------------

    // StaffMovementScheduler: the staff member reached the incident
    @Transactional
    public void markArrived(Long taskId) {
        Task task = getTask(taskId);
        lockIncident(task);
        if (task.getStatus() == TaskStatus.EN_ROUTE && !IncidentService.isClosed(task.getIncident().getStatus())) {
            move(task, TaskStatus.ON_SITE, null, ActorType.SYSTEM, null);
        }
    }

    // EscalationScheduler: PENDING too long -> REJECTED (not accepted in time) + re-assign + supervisors
    @Transactional
    public void escalateIfOverdue(Long taskId, LocalDateTime deadline) {
        Task task = getTask(taskId);
        lockIncident(task);
        // re-check inside the transaction: the staff member may have accepted a second ago
        if (task.getStatus() != TaskStatus.PENDING || Boolean.TRUE.equals(task.getIsEscalated())
                || task.getAssignedAt() == null || !task.getAssignedAt().isBefore(deadline)) {
            return;
        }
        Incident incident = task.getIncident();
        task.setIsEscalated(true);
        task.setStatus(TaskStatus.REJECTED);
        task.setRejectionReason("Not accepted in time");
        save(task, ActionType.ESCALATED, ActorType.SYSTEM, null, TaskStatus.PENDING,
                "Not accepted in time by " + task.getStaff().getName());

        staffService.releaseIfIdle(task.getStaff());
        notificationService.notifyStaff(task.getStaff().getId(), incident.getId(), NotificationType.TASK_CANCELLED,
                "Task " + incident.getCode() + " was re-assigned: not accepted in time", incident.getSeverity());
        if (IncidentService.isClosed(incident.getStatus())) return;

        assignmentService.escalate(incident, "Task #" + task.getId() + " was not accepted in time by "
                + task.getStaff().getName());
        assignmentService.reassign(task, "not accepted in time");
        incidentService.reopenIfUnstaffed(incident.getId());
    }

    // incident closed -> its open tasks are cancelled and their staff released
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onIncidentResolved(IncidentResolvedEvent event) {
        try {
            List<Task> open = taskRepository.findByIncidentIdAndStatusNotIn(event.incidentId(),
                    List.of(TaskStatus.DONE, TaskStatus.REJECTED, TaskStatus.CANCELLED));
            for (Task task : open) {
                TaskStatus old = task.getStatus();
                task.setStatus(TaskStatus.CANCELLED);
                task.setCompletedAt(LocalDateTime.now());
                save(task, ActionType.STATUS_CHANGED, ActorType.SYSTEM, null, old,
                        "Incident closed as " + event.finalStatus());
                staffService.releaseIfIdle(task.getStaff());
                notificationService.notifyStaff(task.getStaff().getId(), event.incidentId(), NotificationType.TASK_CANCELLED,
                        "Task cancelled: " + task.getIncident().getCode() + " was closed", Severity.LOW);
            }
        } catch (Exception e) {
            log.error("Could not close the tasks of incident #{}", event.incidentId(), e);
        }
    }

    // ---------------- state machine ----------------

    // forward only: ACCEPTED -> EN_ROUTE -> ON_SITE -> DONE (skipping forward is allowed, e.g. ACCEPTED -> ON_SITE)
    private Task move(Task task, TaskStatus next, String notes, ActorType actorType, Long actorId) {
        TaskStatus current = task.getStatus();
        if (next == current) return task; // idempotent
        if (next == TaskStatus.ACCEPTED) throw new ApiException("Use POST /tasks/{id}/accept");
        if (next == TaskStatus.REJECTED) throw new ApiException("Use POST /tasks/{id}/reject");
        if (next != TaskStatus.EN_ROUTE && next != TaskStatus.ON_SITE && next != TaskStatus.DONE) {
            throw new ApiException("Invalid task status: " + next);
        }
        if (current == TaskStatus.PENDING) throw new ApiException("The task must be accepted first");
        if (next.ordinal() < current.ordinal()) {
            throw new ApiException("Invalid transition " + current + " -> " + next);
        }

        LocalDateTime now = LocalDateTime.now();
        if (next == TaskStatus.ON_SITE || (next == TaskStatus.DONE && task.getArrivedAt() == null)) task.setArrivedAt(now);
        if (next == TaskStatus.DONE) task.setCompletedAt(now);
        if (StringUtils.hasText(notes)) task.setNotes(notes.trim());
        task.setStatus(next);

        save(task, ActionType.STATUS_CHANGED, actorType, actorId, current, null);

        Long incidentId = task.getIncident().getId();
        if (next == TaskStatus.EN_ROUTE || next == TaskStatus.ON_SITE || next == TaskStatus.DONE) {
            incidentService.changeStatus(incidentId, IncidentStatus.IN_PROGRESS, actorType, actorId);
        }
        if (next == TaskStatus.DONE) {
            staffService.releaseIfIdle(task.getStaff()); // the incident itself is closed manually by the operator
            if (taskRepository.countByIncidentIdAndStatusIn(incidentId, StaffService.ACTIVE_TASK) == 0) {
                activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, ActionType.STATUS_CHANGED,
                        ActorType.SYSTEM, null, null, null, "All tasks are done: ready to be resolved");
            }
        }
        return task;
    }

    // ---------------- helpers ----------------

    private Task getTask(Long taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> new ApiException("Task not found"));
    }

    // lock the incident row first (same order everywhere -> no deadlocks with the pipeline)
    private void lockIncident(Task task) {
        incidentService.getIncidentForUpdate(task.getIncident().getId());
    }

    private Task getActionableTask(Long taskId) {
        Task task = getTask(taskId);
        currentUser.requireSelfIfField(task.getStaff().getId());
        lockIncident(task);
        if (IncidentService.isClosed(task.getIncident().getStatus())) {
            throw new ApiException("Incident is already closed");
        }
        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.REJECTED
                || task.getStatus() == TaskStatus.DONE) {
            throw new ApiException("Task is already " + task.getStatus());
        }
        return task;
    }

    // the task's own staff member acts unless someone else (operator) is identified
    private Long actorFor(Task task, Long requested) {
        Long actorId = currentUser.actorId(requested);
        incidentService.validateActor(actorId);
        return actorId != null ? actorId : task.getStaff().getId();
    }

    private void save(Task task, ActionType action, ActorType actorType, Long actorId, TaskStatus old, String details) {
        taskRepository.save(task);
        Staff staff = task.getStaff();
        activityLogService.log(EntityType.TASK, task.getId(), task.getIncident().getId(), action, actorType, actorId,
                old == null ? null : old.name(), task.getStatus().name(),
                details != null ? details : staff.getName() + ": " + old + " -> " + task.getStatus());
        broadcastService.send(BroadcastService.TASKS, "TASK_UPDATED", DTOMapper.toDTO(task));
    }
}
