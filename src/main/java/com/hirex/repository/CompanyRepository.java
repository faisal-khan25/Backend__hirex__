package com.hirex.repository;

import com.hirex.entity.Company;
import com.hirex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByManager(User manager);
    List<Company> findAll();
}
