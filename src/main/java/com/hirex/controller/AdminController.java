package com.hirex.controller;

import com.hirex.service.AdminService;
import com.hirex.service.AdminService.DashboardStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ── Overview ──────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> dashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // ── Users ────────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> allUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // ── Jobs ─────────────────────────────────────────────────────
    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> allJobs() {
        return ResponseEntity.ok(adminService.getAllJobs());
    }

    // ── Applications ─────────────────────────────────────────────
    @GetMapping("/applications")
    public ResponseEntity<List<Map<String, Object>>> allApplications() {
        return ResponseEntity.ok(adminService.getAllApplications());
    }
}
