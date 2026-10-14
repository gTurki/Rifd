package com.example.rifd.Enum;

public enum TaskStatus {
    PENDING,
    ACCEPTED,
    EN_ROUTE,
    ON_SITE,
    DONE,
    REJECTED,
    CANCELLED   // the incident was closed while the task was still open (not the staff member's decision)
}
