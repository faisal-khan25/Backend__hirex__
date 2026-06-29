package com.hirex.service;

import com.hirex.entity.Company;
import com.hirex.entity.User;
import com.hirex.repository.CompanyRepository;
import com.hirex.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;

    public CompanyService(CompanyRepository companyRepo, UserRepository userRepo) {
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
    }

    public CompanyResponse createOrUpdateCompany(CompanyRequest req, String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();

        Company company = companyRepo.findByManager(manager).orElse(new Company());
        company.setManager(manager);
        company.setName(req.getName());
        company.setDescription(req.getDescription());
        company.setWebsite(req.getWebsite());
        company.setLocation(req.getLocation());
        company.setIndustry(req.getIndustry());
        company.setLogoUrl(req.getLogoUrl());
        company.setSize(req.getSize());

        return toResponse(companyRepo.save(company));
    }

    public CompanyResponse getMyCompany(String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        Company company = companyRepo.findByManager(manager)
                .orElseThrow(() -> new RuntimeException("No company profile found"));
        return toResponse(company);
    }

    private CompanyResponse toResponse(Company c) {
        CompanyResponse r = new CompanyResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setDescription(c.getDescription());
        r.setWebsite(c.getWebsite());
        r.setLocation(c.getLocation());
        r.setIndustry(c.getIndustry());
        r.setLogoUrl(c.getLogoUrl());
        r.setSize(c.getSize());
        return r;
    }

    // request/response - no lombok
    public static class CompanyRequest {
        private String name;
        private String description;
        private String website;
        private String location;
        private String industry;
        private String logoUrl;
        private int size;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }

        public String getLogoUrl() { return logoUrl; }
        public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }

    public static class CompanyResponse {
        private Long id;
        private String name;
        private String description;
        private String website;
        private String location;
        private String industry;
        private String logoUrl;
        private int size;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }

        public String getLogoUrl() { return logoUrl; }
        public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }
}
