package com.hirex.service;

import com.hirex.entity.Application;
import com.hirex.entity.Job;
import com.hirex.entity.Role;
import com.hirex.entity.User;
import com.hirex.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final JobRepository jobRepo;
    private final ApplicationRepository appRepo;

    public AdminService(UserRepository userRepo, CompanyRepository companyRepo,
                        JobRepository jobRepo, ApplicationRepository appRepo) {
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
        this.jobRepo = jobRepo;
        this.appRepo = appRepo;
    }

    // ── Dashboard Stats ───────────────────────────────────────────

    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();
        stats.setTotalJobSeekers(userRepo.countByRole(Role.JOBSEEKER));
        stats.setTotalManagers(userRepo.countByRole(Role.MANAGER));
        stats.setTotalCompanies(companyRepo.count());
        stats.setTotalJobs(jobRepo.count());
        stats.setTotalApplications(appRepo.count());
        stats.setMonthlyGrowth(getMonthlyGrowth());
        stats.setCompanyHiringTrends(getCompanyHiring());
        return stats;
    }

    private List<Map<String, Object>> getMonthlyGrowth() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 5; i >= 0; i--) {
            LocalDateTime from = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime to = from.plusMonths(1);

            List<User> managers = userRepo.findByRoleAndCreatedAtAfter(Role.MANAGER, from);
            long count = managers.stream().filter(u -> u.getCreatedAt().isBefore(to)).count();

            Map<String, Object> point = new HashMap<>();
            point.put("month", from.getMonth().name().substring(0, 3));
            point.put("managers", count);
            result.add(point);
        }
        return result;
    }

    private List<Map<String, Object>> getCompanyHiring() {
        List<Object[]> raw = appRepo.countApplicationsPerCompany();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("company", row[0]);
            item.put("applications", row[1]);
            result.add(item);
        }
        return result;
    }

    // ── All Users ────────────────────────────────────────────────

    public List<Map<String, Object>> getAllUsers() {
        List<User> users = userRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getId());
            item.put("name", u.getName());
            item.put("email", u.getEmail());
            item.put("role", u.getRole().name());
            item.put("createdAt", u.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    // ── All Jobs ─────────────────────────────────────────────────

    public List<Map<String, Object>> getAllJobs() {
        List<Job> jobs = jobRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Job j : jobs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", j.getId());
            item.put("title", j.getTitle());
            item.put("company", j.getCompany() != null ? j.getCompany().getName() : "—");
            item.put("location", j.getLocation());
            item.put("jobType", j.getJobType());
            item.put("active", j.isActive());
            item.put("postedAt", j.getPostedAt());
            result.add(item);
        }
        return result;
    }

    // ── All Applications ─────────────────────────────────────────

    public List<Map<String, Object>> getAllApplications() {
        List<Application> apps = appRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Application a : apps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("applicantName",  a.getApplicant() != null ? a.getApplicant().getName()  : "—");
            item.put("applicantEmail", a.getApplicant() != null ? a.getApplicant().getEmail() : "—");
            item.put("jobTitle",  a.getJob() != null ? a.getJob().getTitle() : "—");
            item.put("company",   a.getJob() != null && a.getJob().getCompany() != null
                    ? a.getJob().getCompany().getName() : "—");
            item.put("status",    a.getStatus() != null ? a.getStatus().name() : "—");
            item.put("appliedAt", a.getAppliedAt());
            result.add(item);
        }
        return result;
    }

    // ── DashboardStats DTO ────────────────────────────────────────

    public static class DashboardStats {
        private long totalJobSeekers;
        private long totalManagers;
        private long totalCompanies;
        private long totalJobs;
        private long totalApplications;
        private List<Map<String, Object>> monthlyGrowth;
        private List<Map<String, Object>> companyHiringTrends;

        public long getTotalJobSeekers() { return totalJobSeekers; }
        public void setTotalJobSeekers(long v) { this.totalJobSeekers = v; }

        public long getTotalManagers() { return totalManagers; }
        public void setTotalManagers(long v) { this.totalManagers = v; }

        public long getTotalCompanies() { return totalCompanies; }
        public void setTotalCompanies(long v) { this.totalCompanies = v; }

        public long getTotalJobs() { return totalJobs; }
        public void setTotalJobs(long v) { this.totalJobs = v; }

        public long getTotalApplications() { return totalApplications; }
        public void setTotalApplications(long v) { this.totalApplications = v; }

        public List<Map<String, Object>> getMonthlyGrowth() { return monthlyGrowth; }
        public void setMonthlyGrowth(List<Map<String, Object>> v) { this.monthlyGrowth = v; }

        public List<Map<String, Object>> getCompanyHiringTrends() { return companyHiringTrends; }
        public void setCompanyHiringTrends(List<Map<String, Object>> v) { this.companyHiringTrends = v; }
    }
}
