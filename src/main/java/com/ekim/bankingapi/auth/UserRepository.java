package com.ekim.bankingapi.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCustomerId(Long customerId);

    boolean existsByEmail(String email);

    boolean existsByCustomerId(Long customerId);

    boolean existsByRole(Role role);
}