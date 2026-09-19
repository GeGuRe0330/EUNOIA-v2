package com.eunoia.identity.infrastructure.repository;

import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findByStatus(Status status);
}
