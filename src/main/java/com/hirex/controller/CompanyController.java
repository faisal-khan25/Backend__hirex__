package com.hirex.controller;

import com.hirex.service.CompanyService;
import com.hirex.service.CompanyService.CompanyRequest;
import com.hirex.service.CompanyService.CompanyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/manager/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> save(@RequestBody CompanyRequest req, Principal principal) {
        return ResponseEntity.ok(companyService.createOrUpdateCompany(req, principal.getName()));
    }

    @GetMapping
    public ResponseEntity<CompanyResponse> get(Principal principal) {
        return ResponseEntity.ok(companyService.getMyCompany(principal.getName()));
    }
}
