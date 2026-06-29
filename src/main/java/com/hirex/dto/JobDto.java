package com.hirex.dto;

public class JobDto {

    public static class JobRequest {
        private String title;
        private String description;
        private String skills;
        private String salary;
        private String location;
        private String jobType;
        private String experience;
        private String education;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getSkills() { return skills; }
        public void setSkills(String skills) { this.skills = skills; }

        public String getSalary() { return salary; }
        public void setSalary(String salary) { this.salary = salary; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getJobType() { return jobType; }
        public void setJobType(String jobType) { this.jobType = jobType; }

        public String getExperience() { return experience; }
        public void setExperience(String experience) { this.experience = experience; }

        public String getEducation() { return education; }
        public void setEducation(String education) { this.education = education; }
    }

    public static class JobResponse {
        private Long id;
        private String title;
        private String description;
        private String skills;
        private String salary;
        private String location;
        private String jobType;
        private boolean active;
        private String postedAt;
        private String companyName;
        private String companyLogo;
        private Long companyId;
        private String experience;
        private String education;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getSkills() { return skills; }
        public void setSkills(String skills) { this.skills = skills; }

        public String getSalary() { return salary; }
        public void setSalary(String salary) { this.salary = salary; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getJobType() { return jobType; }
        public void setJobType(String jobType) { this.jobType = jobType; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public String getPostedAt() { return postedAt; }
        public void setPostedAt(String postedAt) { this.postedAt = postedAt; }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getCompanyLogo() { return companyLogo; }
        public void setCompanyLogo(String companyLogo) { this.companyLogo = companyLogo; }

        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }

        public String getExperience() { return experience; }
        public void setExperience(String experience) { this.experience = experience; }

        public String getEducation() { return education; }
        public void setEducation(String education) { this.education = education; }
    }
}
