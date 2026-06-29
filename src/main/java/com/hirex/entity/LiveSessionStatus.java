package com.hirex.entity;

/**
 * Lifecycle states for a LiveInterviewSession.
 *
 *  WAITING    – session created; neither party has joined yet.
 *  ACTIVE     – both parties connected; interview is in progress.
 *  ENDED      – recruiter clicked "End Interview"; session closed normally.
 *  ABANDONED  – candidate disconnected unexpectedly and never reconnected.
 */
public enum LiveSessionStatus {
    WAITING,
    ACTIVE,
    ENDED,
    ABANDONED
}