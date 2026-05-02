package kz.damulab.parentlink;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkCodeRepository extends JpaRepository<LinkCode, Long> {

    Optional<LinkCode> findByCode(String code);

    boolean existsByCode(String code);
}
