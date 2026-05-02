package kz.damulab.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, Long> {

    Optional<ParentProfile> findByUserEmailIgnoreCase(String email);
}
