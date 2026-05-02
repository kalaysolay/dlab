package kz.damulab.testing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestTemplateRepository extends JpaRepository<TestTemplate, Long> {

    Optional<TestTemplate> findFirstByTestTypeAndActiveTrue(TestType testType);
}
