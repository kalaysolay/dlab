package kz.damulab.speaking;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Один постоянный module-row сериализует короткие резервы; сеть под этой блокировкой не выполняется. */
interface SpeakingBudgetRepository extends JpaRepository<SpeakingBudgetAccount,String>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from SpeakingBudgetAccount a where a.key='module'")
    SpeakingBudgetAccount lockModule();
    @Modifying @Query("delete from SpeakingBudgetAccount a where a.studentId is not null and a.day<:cutoff")
    int purge(@Param("cutoff") LocalDate cutoff);
}
