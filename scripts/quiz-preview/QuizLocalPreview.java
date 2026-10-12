import java.nio.file.*;
import java.util.*;
import kz.damulab.DamulabApplication;
import kz.damulab.quiz.catalog.QuizStarterContentService;
import kz.damulab.users.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Явный операторский загрузчик локального стенда; не входит в application scan или bootJar. */
public class QuizLocalPreview {
    /** Создаёт только локальные проверочные аккаунты и публикует проверенный ресурс в собственной схеме. */
    public static void main(String[] args) throws Exception {
        String schema=System.getenv("QUIZ_PREVIEW_SCHEMA"), url=System.getenv("DB_URL");
        if(schema==null || !schema.matches("quiz_preview_[a-z0-9_]+") || url==null
                || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/quiz_test"))
            throw new IllegalStateException("Разрешена только собственная схема локальной quiz_test");
        String password=Objects.requireNonNull(System.getenv("QUIZ_PREVIEW_PASSWORD"));
        boolean animalsOnly="true".equals(System.getenv("QUIZ_PREVIEW_ANIMALS_ONLY"));
        try(var context=new SpringApplicationBuilder(DamulabApplication.class).run(args)) {
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status->{
                var users=context.getBean(AppUserRepository.class);
                var roles=context.getBean(RoleRepository.class);
                var profiles=context.getBean(StudentProfileRepository.class);
                var encoder=context.getBean(PasswordEncoder.class);
                for(String email:animalsOnly?List.<String>of():List.of("quiz-author@local.test","quiz-reviewer@local.test","quiz-host@local.test","quiz-guest@local.test")) {
                    if(users.existsByEmailIgnoreCase(email)) throw new IllegalStateException("Локальные аккаунты уже существуют; повторная загрузка запрещена");
                    boolean student=email.contains("host") || email.contains("guest");
                    var user=new AppUser(email,encoder.encode(password),email,null);
                    user.addRole(roles.findByCode(student?RoleCode.STUDENT:RoleCode.ADMIN).orElseThrow());
                    users.save(user);
                    if(student) profiles.save(new StudentProfile(user,5,email.contains("guest")?"kk":"ru"));
                }
                // Публикация явно проходит обычный строгий редакционный контракт двух разных ADMIN.
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "quiz-author@local.test",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                try {
                    var content=context.getBean(QuizStarterContentService.class);
                    // Дополнение уже подготовленного стенда сохраняет аккаунты, старые игры и историю.
                    if(!animalsOnly) content.install("quiz-reviewer@local.test");
                    content.installAnimalGames("quiz-reviewer@local.test");
                }
                finally { SecurityContextHolder.clearContext(); }
            });
            Files.writeString(Path.of(System.getenv("QUIZ_PREVIEW_READY")),schema);
        }
    }
}
