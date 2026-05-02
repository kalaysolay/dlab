package kz.damulab.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserEmailIgnoreCase(String email);
}
