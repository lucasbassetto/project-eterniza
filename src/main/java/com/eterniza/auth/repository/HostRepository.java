package com.eterniza.auth.repository;

import com.eterniza.auth.domain.Host;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HostRepository extends JpaRepository<Host, UUID> {
    Optional<Host> findByEmail(String email);
    boolean existsByEmail(String email);
}
