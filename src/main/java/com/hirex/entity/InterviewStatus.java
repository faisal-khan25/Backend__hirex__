package com.hirex.entity;

// ADDED: Interview Status Enum
public enum InterviewStatus {
    // Original...
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED, NO_SHOW,

    // NEW: Add these three lines
    PASSED,           // Overall Score 80-100 ✅
    UNDER_REVIEW,     // Overall Score 60-79 🟡
    FAILED            // Overall Score < 60 ❌
}