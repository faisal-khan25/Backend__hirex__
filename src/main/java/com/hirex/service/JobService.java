package com.hirex.service;

import com.hirex.dto.JobDto.JobRequest;
import com.hirex.dto.JobDto.JobResponse;
import com.hirex.entity.Company;
import com.hirex.entity.Job;
import com.hirex.entity.User;
import com.hirex.repository.CompanyRepository;
import com.hirex.repository.JobRepository;
import com.hirex.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;

    public JobService(JobRepository jobRepo, CompanyRepository companyRepo, UserRepository userRepo) {
        this.jobRepo = jobRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
    }
    public List<JobResponse> searchManagerJobs(String keyword, String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        Company company = companyRepo.findByManager(manager).orElseThrow();
        return jobRepo.findByCompanyAndTitleContainingIgnoreCase(company, keyword)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public JobResponse createJob(JobRequest req, String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        Company company = companyRepo.findByManager(manager)
                .orElseThrow(() -> new RuntimeException("Create company profile first"));

        Job job = new Job();
        job.setTitle(req.getTitle());
        job.setDescription(req.getDescription());
        job.setSkills(req.getSkills());
        job.setSalary(req.getSalary());
        job.setLocation(req.getLocation());
        job.setJobType(req.getJobType());
        job.setExperience(req.getExperience());
        job.setEducation(req.getEducation());
        job.setCompany(company);

        return toResponse(jobRepo.save(job));
    }

    public JobResponse updateJob(Long jobId, JobRequest req, String managerEmail) {
        Job job = jobRepo.findById(jobId).orElseThrow();
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        Company company = companyRepo.findByManager(manager).orElseThrow();

        if (!job.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Not authorized");
        }

        job.setTitle(req.getTitle());
        job.setDescription(req.getDescription());
        job.setSkills(req.getSkills());
        job.setSalary(req.getSalary());
        job.setLocation(req.getLocation());
        job.setJobType(req.getJobType());
        job.setExperience(req.getExperience());
        job.setEducation(req.getEducation());

        return toResponse(jobRepo.save(job));
    }

    public void deleteJob(Long jobId, String managerEmail) {
        Job job = jobRepo.findById(jobId).orElseThrow();
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        Company company = companyRepo.findByManager(manager).orElseThrow();

        if (!job.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Not authorized");
        }
        job.setActive(false);
        jobRepo.save(job);
    }

    public List<JobResponse> getAllActiveJobs() {
        return jobRepo.findByActiveTrue().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<JobResponse> searchJobs(String keyword) {
        return jobRepo.findByTitleContainingIgnoreCaseAndActiveTrue(keyword)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<JobResponse> getManagerJobs(String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        Company company = companyRepo.findByManager(manager).orElseThrow();
        return jobRepo.findByCompany(company).stream().map(this::toResponse).collect(Collectors.toList());
    }

    private JobResponse toResponse(Job job) {
        JobResponse r = new JobResponse();
        r.setId(job.getId());
        r.setTitle(job.getTitle());
        r.setDescription(job.getDescription());
        r.setSkills(job.getSkills());
        r.setSalary(job.getSalary());
        r.setLocation(job.getLocation());
        r.setJobType(job.getJobType());
        r.setActive(job.isActive());
        r.setPostedAt(job.getPostedAt() != null ? job.getPostedAt().toString() : "");
        r.setCompanyName(job.getCompany().getName());
        r.setCompanyLogo(job.getCompany().getLogoUrl());
        r.setCompanyId(job.getCompany().getId());
        r.setExperience(job.getExperience());
        r.setEducation(job.getEducation());
        return r;
    }
}
