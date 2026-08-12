package com.ekim.bankingapi.notification;

public enum NotificationType {
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    DEPOSIT,
    WITHDRAWAL,
    LIMIT_EXCEEDED,
    ACCOUNT_LOCKED,
    SCHEDULED_TRANSFER_EXECUTED,
    SCHEDULED_TRANSFER_FAILED
}
