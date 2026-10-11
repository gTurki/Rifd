package com.example.rifd.Repository;

import com.example.rifd.Entity.Notification;
import com.example.rifd.Enum.StaffSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // this query retrieves notifications for a specific staff member or specialty, with an option to filter for unread notifications only
    @Query("""
           SELECT n FROM Notification n
           WHERE (n.recipientStaff.id = :staffId OR n.recipientSpecialty = :specialty)
             AND (:unreadOnly = false OR n.readAt IS NULL)
           ORDER BY n.sentAt DESC
           """)
    List<Notification> findForRecipient(@Param("staffId") Long staffId,
                                        @Param("specialty") StaffSpecialty specialty,
                                        @Param("unreadOnly") boolean unreadOnly);

    List<Notification> findAllByOrderBySentAtDesc();
}