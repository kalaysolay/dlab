package kz.damulab.parentlink;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kz.damulab.users.ParentProfile;
import kz.damulab.users.StudentProfile;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, Long> {

    List<ParentStudentLink> findByParentProfileOrderByCreatedAtDesc(ParentProfile parentProfile);

    Optional<ParentStudentLink> findByParentProfileAndStudentProfile(ParentProfile parentProfile, StudentProfile studentProfile);

    boolean existsByParentProfileAndStudentProfile(ParentProfile parentProfile, StudentProfile studentProfile);

    void deleteByParentProfileAndStudentProfile(ParentProfile parentProfile, StudentProfile studentProfile);
}
