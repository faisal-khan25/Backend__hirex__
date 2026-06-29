package com.hirex.repository;

import com.hirex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRole(com.hirex.entity.Role role);
    // for monthly growth - count managers registered after date
    List<User> findByRoleAndCreatedAtAfter(com.hirex.entity.Role role, LocalDateTime date);
}
