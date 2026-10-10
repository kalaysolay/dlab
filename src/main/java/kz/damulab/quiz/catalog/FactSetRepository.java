package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к FactSet; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface FactSetRepository extends JpaRepository<FactSet, Long> {
    List<FactSet> findAllByOrderByIdAsc();
    boolean existsByCodeIgnoreCase(String code);
}
