package com.hirex.controller;

import com.hirex.entity.JobSeekerProfile;
import com.hirex.entity.User;
import com.hirex.repository.JobSeekerProfileRepository;
import com.hirex.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/jobseeker/profile")
public class ProfileController {

    private final JobSeekerProfileRepository profileRepo;
    private final UserRepository userRepo;

    public ProfileController(JobSeekerProfileRepository profileRepo, UserRepository userRepo) {
        this.profileRepo = profileRepo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public ResponseEntity<JobSeekerProfile> get(Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        return ResponseEntity.ok(profileRepo.findByUser(user).orElse(new JobSeekerProfile()));
    }

    @PostMapping
    public ResponseEntity<JobSeekerProfile> save(@RequestBody ProfileRequest req, Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        JobSeekerProfile profile = profileRepo.findByUser(user).orElse(new JobSeekerProfile());

        profile.setUser(user);
        profile.setSkills(req.getSkills());
        profile.setExperience(req.getExperience());
        profile.setResumeUrl(req.getResumeUrl());
        profile.setBio(req.getBio());
        profile.setLocation(req.getLocation());
        profile.setEducation(req.getEducation());

        return ResponseEntity.ok(profileRepo.save(profile));
    }

    public static class ProfileRequest {
        private String skills;
        private String experience;
        private String resumeUrl;
        private String bio;
        private String location;
        private String education;

        public String getSkills() { return skills; }
        public void setSkills(String skills) { this.skills = skills; }

        public String getExperience() { return experience; }
        public void setExperience(String experience) { this.experience = experience; }

        public String getResumeUrl() { return resumeUrl; }
        public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }

        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getEducation() { return education; }
        public void setEducation(String education) { this.education = education; }
    }
}
