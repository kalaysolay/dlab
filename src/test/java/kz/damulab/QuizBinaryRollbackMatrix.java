package kz.damulab;

import java.nio.file.*;
import java.sql.DriverManager;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.users.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Воспроизводимая отдельная матрица старого bootJar на синтетических PostgreSQL-схемах, без clean/delete/down-migration. */
public class QuizBinaryRollbackMatrix {
    /** Требует явный предыдущий JAR и loopback PostgreSQL; создаёт две собственные схемы и сохраняет данные/отчёты. */
    public static void main(String[] args) throws Exception {
        String url=System.getenv("QUIZ_TEST_DB_URL"), user=System.getenv("QUIZ_TEST_DB_USER"), password=System.getenv("QUIZ_TEST_DB_PASSWORD");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/quiz_test")) throw new IllegalArgumentException("Нужен изолированный loopback quiz_test PostgreSQL");
        Path jar=Path.of(System.getenv("QUIZ_PREVIOUS_JAR")).toAbsolutePath();
        Path root=Path.of(".run-logs/quiz-stage7-rollback-"+UUID.randomUUID()).toAbsolutePath(); Files.createDirectories(root);
        Path extracted=root.resolve("previous");
        try(var zip=new JarFile(jar.toFile())) {
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement(); String name=entry.getName();
                if(entry.isDirectory() || !(name.startsWith("BOOT-INF/classes/")||name.startsWith("BOOT-INF/lib/"))) continue;
                Path path=extracted.resolve(name).normalize(); if(!path.startsWith(extracted)) throw new IllegalArgumentException("Небезопасный путь JAR");
                Files.createDirectories(path.getParent()); try(var stream=zip.getInputStream(entry)){Files.copy(stream,path);}
            }
        }
        // Probe-каталог содержит только один класс, чтобы старый component scan не видел новый код или тестовые конфигурации.
        Path probe=root.resolve("probe/kz/damulab"); Files.createDirectories(probe);
        // Ресурс не зависит от build directory: отдельная QA-сборка также должна изолировать старый classpath.
        try(var stream=QuizPreviousBinaryProbe.class.getResourceAsStream("/kz/damulab/QuizPreviousBinaryProbe.class")) {
            if(stream==null) throw new IllegalStateException("Отсутствует скомпилированный previous binary probe");
            Files.copy(stream,probe.resolve("QuizPreviousBinaryProbe.class"));
        }
        var mapper=new ObjectMapper(); var report=new LinkedHashMap<String,Object>();
        report.put("artifact",jar.toString()); report.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        report.put("previousCommit","cb97aac1f652d3b082771be6876d6fd2e5639f6b");
        for(String phase:List.of("before-new-records","after-new-records")) {
            String schema="quiz_binary_"+UUID.randomUUID().toString().replace("-","");
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("46").load().migrate();
            try(var c=DriverManager.getConnection(url,user,password)) {
                c.setSchema(schema); QuizFoundationMigrationTest.seedLegacy(c);
                // В отличие от минимальной history-фикстуры, legacy create требует опубликованный материал с grade link и вариантами.
                try(var sql=c.createStatement()) {
                    sql.executeUpdate("insert into question_version_grades(question_version_id,grade_id) select 900001,id from grades where grade_no=5");
                    sql.executeUpdate("update question_versions set options_json='[{\"label\":\"A\",\"textRu\":\"2\",\"textKk\":\"2\"},{\"label\":\"B\",\"textRu\":\"3\",\"textKk\":\"3\"}]', answer_key_json='[\"A\"]' where id=900001");
                }
            }
            var cells=new ArrayList<Map<String,String>>();
            for(int i=0;i<3;i++) cells.add(Map.of("type","LEGACY","status",List.of("WAITING","ACTIVE","FINISHED").get(i),"code","LEGACY"+i));
            String[] options={"--server.address=127.0.0.1","--server.port=0","--spring.datasource.url="+url,"--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username="+user,"--spring.datasource.password="+password,"--spring.datasource.hikari.schema="+schema,"--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,
                "--damulab.ai.real-providers-enabled=false","--damulab.quiz.timeout-worker-enabled=false","--damulab.quiz.timeout-worker-delay-ms=600000","--damulab.quiz.game-catalog-enabled=false"};
            try(var context=new SpringApplicationBuilder(MatrixApplication.class).profiles("test").run(options)) {
                report.put("schemaVersion",context.getBean(Flyway.class).info().current().getVersion().toString());
                if(phase.equals("after-new-records")) {
                    var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                    var users=context.getBean(AppUserRepository.class); var roles=context.getBean(RoleRepository.class);
                    tx.execute(s->{var reviewer=new AppUser("reviewer@rollback.test","test-only","Проверяющий",null); reviewer.getRoles().add(roles.findByCode(RoleCode.ADMIN).orElseThrow()); users.save(reviewer); return null;});
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@damulab.kz",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
                    try {context.getBean(QuizStarterContentService.class).install("reviewer@rollback.test");} finally {SecurityContextHolder.clearContext();}
                    var jdbc=context.getBean(JdbcTemplate.class); jdbc.update("update student_profiles set grade_no=5 where id=900001");
                    var rooms=context.getBean(QuizRoomService.class);
                    for(String gameCode:List.of(QuizStarterContentService.CODES.get(0),QuizStarterContentService.CODES.get(2),QuizStarterContentService.CODES.get(3))) {
                        for(String status:List.of("WAITING","ACTIVE","FINISHED","CANCELLED","EXPIRED")) {
                            var room=rooms.create("migration@example.test",new QuizGameRoomRequest.Create(gameCode,null,null,null,UUID.randomUUID().toString()));
                            if(status.equals("ACTIVE")||status.equals("FINISHED")) {
                                rooms.start(room.code(),"migration@example.test");
                                jdbc.update("update quiz_rooms set started_at=current_timestamp - interval '10 minutes' where code=?",room.code());
                                if(status.equals("FINISHED")) rooms.results(room.code(),"migration@example.test");
                            } else if(status.equals("CANCELLED")) rooms.cancel(room.code(),"migration@example.test");
                            else if(status.equals("EXPIRED")) {jdbc.update("update quiz_rooms set expires_at=current_timestamp - interval '1 second' where code=?",room.code()); rooms.state(room.code(),"migration@example.test");}
                            cells.add(Map.of("type",room.gameType(),"status",status,"code",room.code()));
                        }
                    }
                }
            }
            Path input=root.resolve(phase+"-input.json"), output=root.resolve(phase+"-output.json");
            Files.writeString(input,mapper.writeValueAsString(Map.of("schema",schema,"cells",cells)));
            String cp=extracted.resolve("BOOT-INF/classes")+java.io.File.pathSeparator+extracted.resolve("BOOT-INF/lib")+java.io.File.separator+"*"+java.io.File.pathSeparator+root.resolve("probe");
            var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Xmx256m","-XX:ActiveProcessorCount=2","-Dquiz.probe.input="+input,"-Dquiz.probe.output="+output,"-cp",cp,"kz.damulab.QuizPreviousBinaryProbe"));
            command.addAll(Arrays.asList(options));
            // Старый scheduler запускается явно probe, периодические вызовы задержаны на 10 минут.
            var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(root.resolve(phase+"-previous.log").toFile()).start();
            if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroy(); throw new IllegalStateException("Старый probe не завершился за 120 секунд");}
            if(process.exitValue()!=0 || !Files.exists(output)) throw new IllegalStateException("Старый probe не создал отчёт: "+root);
            var observed=mapper.readTree(Files.readString(output));
            if(!observed.path("startup").asText().equals("PASS")) throw new IllegalStateException("Не удалось проверить старый runtime: "+output);
            if(!observed.path("legacyCreateAndStart").asText().equals("PASS")) throw new IllegalStateException("Legacy create/start не прошёл: "+output);
            if(phase.equals("before-new-records") && (!observed.path("scheduler").asText().equals("PASS") ||
                    java.util.stream.StreamSupport.stream(observed.path("cells").spliterator(),false).anyMatch(v->!v.path("jpa").asText().equals("PASS")||!v.path("read").asText().equals("PASS"))))
                throw new IllegalStateException("Базовая матрица старого приложения не прошла: "+output);
            // Отрицательные ячейки являются ожидаемым доказательством запрета binary rollback, а не поводом стирать записи.
            if(phase.equals("after-new-records") && java.util.stream.StreamSupport.stream(observed.path("cells").spliterator(),false)
                    .noneMatch(v->!v.path("jpa").asText().equals("PASS")||!v.path("read").asText().equals("PASS")))
                throw new IllegalStateException("Ожидаемые несовместимости не обнаружены; матрицу нужно пересмотреть: "+output);
            try(var c=DriverManager.getConnection(url,user,password)) {
                c.setSchema(schema);
                try(var sql=c.createStatement(); var row=sql.executeQuery("select count(*) from quiz_rooms where code in ('LEGACY0','LEGACY1','LEGACY2')")) {
                    row.next(); if(row.getInt(1)!=3) throw new IllegalStateException("Старые комнаты потеряны");
                }
            }
            report.put(phase,observed); report.put(phase+"Schema",schema);
        }
        Path output=root.resolve("matrix.json"); Files.writeString(output,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("Матрица binary rollback: "+output);
    }
    /** Поднимает текущий runtime, исключая все тестовые конфигурации/часы из component scan. */
    @TestConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages="kz.damulab",excludeFilters={
        @ComponentScan.Filter(type=FilterType.ANNOTATION,classes=TestComponent.class),
        @ComponentScan.Filter(type=FilterType.REGEX,pattern="kz\\.damulab\\..*Test(\\$.*)?"),
        @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=DamulabApplication.class)})
    static class MatrixApplication {}
}
