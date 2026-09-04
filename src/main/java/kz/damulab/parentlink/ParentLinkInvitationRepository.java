package kz.damulab.parentlink;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import kz.damulab.users.ParentProfile;
import kz.damulab.users.StudentProfile;

public interface ParentLinkInvitationRepository extends JpaRepository<ParentLinkInvitation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ParentLinkInvitation i where i.parentProfile = :parent and i.studentProfile = :student")
    Optional<ParentLinkInvitation> findPairForUpdate(
            @Param("parent") ParentProfile parent,
            @Param("student") StudentProfile student
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ParentLinkInvitation i where i.tokenHash = :tokenHash")
    Optional<ParentLinkInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

}
