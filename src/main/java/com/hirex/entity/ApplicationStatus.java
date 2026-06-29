package com.hirex.entity;

//public enum ApplicationStatus {
//    APPLIED,
//    SHORTLISTED,
//    REJECTED,
//    HIRED,
//    INTERVIEW_SCHEDULED,
//    INTERVIEW_COMPLETED
//}
public enum ApplicationStatus {
    APPLIED,
    SHORTLISTED,
    REJECTED,
    HIRED,
    INTERVIEW_SCHEDULED,
    INTERVIEW_COMPLETED,
    INTERVIEW_PASSED,    // ★ NEW — PASS candidates ready for recruiter review
    INTERVIEW_FAILED     // ★ NEW — FAIL candidates (remain visible with result)
}
