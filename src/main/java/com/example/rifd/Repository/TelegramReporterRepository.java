package com.example.rifd.Repository;

import com.example.rifd.Entity.TelegramReporter;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramReporterRepository extends JpaRepository<TelegramReporter, Long> {

    Optional<TelegramReporter> findByTelegramUserId(Long telegramUserId);

    List<TelegramReporter> findAllByOrderByLastMessageAtDesc();

    List<TelegramReporter> findByBlockedOrderByLastMessageAtDesc(Boolean blocked);

    // name + last seen only: never touches blocked/phone, so it can't undo an operator's change
    @Modifying
    @Transactional
    @Query("update TelegramReporter r set r.firstName = :firstName, r.username = :username, r.lastMessageAt = :at "
            + "where r.telegramUserId = :telegramUserId")
    int touch(@Param("telegramUserId") Long telegramUserId, @Param("firstName") String firstName,
              @Param("username") String username, @Param("at") LocalDateTime at);

    @Query("select count(a) from Alert a where a.reporter.id = :reporterId")
    long countReports(@Param("reporterId") Long reporterId);

    // dismissed by the operator (incident is null then: left join), or part of an incident closed as a false alarm
    @Query("select count(a) from Alert a left join a.incident i where a.reporter.id = :reporterId "
            + "and (a.status = :dismissed or i.status = :falseAlarm)")
    long countFalseReports(@Param("reporterId") Long reporterId,
                           @Param("dismissed") AlertStatus dismissed,
                           @Param("falseAlarm") IncidentStatus falseAlarm);

    @Query("select count(a) from Alert a join a.incident i where a.reporter.id = :reporterId "
            + "and a.status <> :dismissed and i.status = :resolved")
    long countConfirmedReports(@Param("reporterId") Long reporterId,
                               @Param("dismissed") AlertStatus dismissed,
                               @Param("resolved") IncidentStatus resolved);
}