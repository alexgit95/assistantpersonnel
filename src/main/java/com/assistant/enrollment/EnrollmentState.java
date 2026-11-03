package com.assistant.enrollment;

public enum EnrollmentState {
    WAITING,
    COUNTDOWN,
    CAPTURING,
    REVIEW,
    DONE,
    CANCELLED,
    EXPIRED;

    public boolean terminal() {
        return this == DONE || this == CANCELLED || this == EXPIRED;
    }
}
