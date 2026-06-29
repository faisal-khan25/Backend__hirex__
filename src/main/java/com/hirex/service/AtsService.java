package com.hirex.service;

import com.hirex.dto.AtsResultDto;
import com.hirex.entity.Job;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Job-Specific ATS Scoring Service
 *
 * Scores a resume against the requirements of a SPECIFIC job.
 * Keywords are derived dynamically from the job's skills, title,
 * description, experience, and education — never from a hardcoded list.
 *
 * Scoring breakdown (100 pts total):
 *   Skills match   : up to 60 pts  (each skill weighted equally)
 *   Experience      : 15 pts
 *   Education       : 10 pts
 *   Projects mention: 10 pts
 *   Certifications  :  5 pts
 */
@Service
public class AtsService {

    public static final int ATS_THRESHOLD = 80; // >= 80 → SHORTLISTED

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Score a resume against a specific job's requirements.
     */
    public AtsResultDto checkForJob(String resumeText, Job job) {
        if (resumeText == null || resumeText.isBlank()) {
            return emptyResult(job);
        }

        List<String> requiredSkills = parseSkills(job.getSkills());
        String lower = resumeText.toLowerCase();

        // ── Skills (60 pts) ───────────────────────────────────────────
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : requiredSkills) {
            if (lower.contains(skill.toLowerCase())) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int skillScore = 0;
        if (!requiredSkills.isEmpty()) {
            skillScore = (int) Math.round((matched.size() / (double) requiredSkills.size()) * 60);
        }

        // ── Experience (15 pts) ───────────────────────────────────────
        int expScore = scoreExperience(resumeText, job.getExperience());

        // ── Education (10 pts) ────────────────────────────────────────
        int eduScore = scoreEducation(lower, job.getEducation());

        // ── Projects (10 pts) ─────────────────────────────────────────
        int projectScore = lower.contains("project") ? 10 : 0;

        // ── Certifications (5 pts) ────────────────────────────────────
        int certScore = (lower.contains("certif") || lower.contains("certified")) ? 5 : 0;

        int total = skillScore + expScore + eduScore + projectScore + certScore;
        total = Math.min(total, 100); // cap at 100

        String status = total >= ATS_THRESHOLD ? "SHORTLISTED" : "REJECTED";
        List<String> suggestions = buildSuggestions(missing, expScore, eduScore, projectScore, certScore, total);

        AtsResultDto result = new AtsResultDto(total, matched, missing, suggestions);
        result.setJobTitle(job.getTitle());
        return result;
    }

    /**
     * Generic check (no job context) — kept for backward compatibility.
     */
    public AtsResultDto check(String resumeText) {
        // Minimal generic scoring — delegates to a dummy job
        Job dummy = new Job();
        dummy.setTitle("General Position");
        dummy.setSkills("Java,Spring Boot,REST API,MySQL,Git,Docker,Hibernate,JPA,Maven,React,JavaScript");
        dummy.setExperience("1+ Years");
        dummy.setEducation("Bachelor's Degree");
        return checkForJob(resumeText, dummy);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    public List<String> parseSkills(String skillsStr) {
        if (skillsStr == null || skillsStr.isBlank()) return Collections.emptyList();
        List<String> skills = new ArrayList<>();
        for (String s : skillsStr.split("[,;|\\n]+")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) skills.add(trimmed);
        }
        return skills;
    }

    private int scoreExperience(String resumeText, String requiredExp) {
        if (requiredExp == null || requiredExp.isBlank()) return 8; // partial credit if no requirement stated
        // Extract minimum years from strings like "2+ Years", "3 years"
        try {
            String digits = requiredExp.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                int required = Integer.parseInt(digits);
                String lower = resumeText.toLowerCase();
                // Look for year mentions in resume (e.g. "3 years", "2+ years experience")
                for (int y = required + 5; y >= required; y--) {
                    if (lower.contains(y + " year") || lower.contains(y + "+ year")) {
                        return 15;
                    }
                }
                // Check for generic experience sections
                if (lower.contains("experience") || lower.contains("work history")) {
                    return 10;
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private int scoreEducation(String lowerResume, String requiredEdu) {
        if (requiredEdu == null || requiredEdu.isBlank()) return 5; // partial if not specified
        String lowerReq = requiredEdu.toLowerCase();
        // Match degree levels
        if (lowerReq.contains("phd") || lowerReq.contains("doctorate")) {
            return lowerResume.contains("phd") || lowerResume.contains("doctorate") ? 10 : 0;
        }
        if (lowerReq.contains("master") || lowerReq.contains("m.tech") || lowerReq.contains("mba")) {
            if (lowerResume.contains("master") || lowerResume.contains("m.tech") || lowerResume.contains("mba")) return 10;
            if (lowerResume.contains("bachelor") || lowerResume.contains("b.tech") || lowerResume.contains("b.e")) return 5;
        }
        if (lowerReq.contains("bachelor") || lowerReq.contains("b.tech") || lowerReq.contains("b.e") || lowerReq.contains("degree")) {
            if (lowerResume.contains("bachelor") || lowerResume.contains("b.tech") || lowerResume.contains("b.e")
                    || lowerResume.contains("b.sc") || lowerResume.contains("degree")) return 10;
        }
        // fallback: any education mention
        return (lowerResume.contains("education") || lowerResume.contains("university") || lowerResume.contains("college")) ? 5 : 0;
    }

    private List<String> buildSuggestions(List<String> missing, int expScore, int eduScore,
                                           int projectScore, int certScore, int total) {
        List<String> suggestions = new ArrayList<>();

        if (total >= ATS_THRESHOLD) {
            suggestions.add("🎉 Excellent match! Resume meets the job requirements.");
        } else if (total >= 60) {
            suggestions.add("👍 Good profile. Address the missing areas to cross the shortlisting threshold.");
        } else if (total >= 40) {
            suggestions.add("⚠️ Average match. Expand your technical skills and experience sections.");
        } else {
            suggestions.add("❌ Low match. Consider upskilling in the required technologies.");
        }

        for (String skill : missing) {
            suggestions.add("Add skill to resume: " + skill);
        }
        if (expScore < 10) {
            suggestions.add("Mention your years of experience more explicitly (e.g. '3 years of Java development').");
        }
        if (eduScore < 5) {
            suggestions.add("Include your educational qualifications clearly.");
        }
        if (projectScore == 0) {
            suggestions.add("Add a Projects section describing relevant work.");
        }
        if (certScore == 0) {
            suggestions.add("Include any certifications relevant to the role.");
        }
        return suggestions;
    }

    private AtsResultDto emptyResult(Job job) {
        List<String> all = parseSkills(job.getSkills());
        AtsResultDto r = new AtsResultDto(0, List.of(), all,
                List.of("Resume text is empty. Please upload a valid resume."));
        r.setJobTitle(job.getTitle());
        return r;
    }
}
