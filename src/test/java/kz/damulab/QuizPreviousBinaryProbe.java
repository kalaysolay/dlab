package kz.damulab;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.quiz.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

/** Запускается в отдельном JVM только с классами/библиотеками предыдущего bootJar, без нового application classpath. */
public class QuizPreviousBinaryProbe {
    /** Проверяет реальный старый Spring/JPA/сервисы; каждую ошибку фиксирует отдельно, не скрывая несовместимость. */
    public static void main(String[] args) throws Exception {
        var mapper=new ObjectMapper(); var result=new LinkedHashMap<String,Object>();
        Path output=Path.of(System.getProperty("quiz.probe.output"));
        var input=mapper.readTree(Files.readString(Path.of(System.getProperty("quiz.probe.input"))));
        try (var context=new SpringApplicationBuilder(DamulabApplication.class).profiles("test").run(args)) {
            result.put("startup","PASS");
            result.put("quizServiceCodeSource",QuizService.class.getProtectionDomain().getCodeSource().getLocation().toString());
            var service=context.getBean(QuizService.class); var repo=context.getBean(QuizRoomRepository.class); var jdbc=context.getBean(JdbcTemplate.class);
            var cells=new ArrayList<Map<String,Object>>();
            for(var cell:input.path("cells")) {
                String code=cell.path("code").asText(); var row=new LinkedHashMap<String,Object>(); row.put("type",cell.path("type").asText()); row.put("status",cell.path("status").asText()); row.put("code",code);
                row.put("jpa",check(()->repo.findByCodeIgnoreCase(code).orElseThrow().getStatus().name()));
                row.put("read",check(()->service.room(code,"migration@example.test")));
                if(cell.path("status").asText().equals("WAITING") && !cell.path("type").asText().equals("LEGACY"))
                    row.put("start",check(()->service.start(code,"migration@example.test")));
                if(cell.path("status").asText().equals("FINISHED")) row.put("results",check(()->service.results(code,"migration@example.test")));
                cells.add(row);
            }
            result.put("cells",cells);
            var request=new CreateQuizRoomRequest(); request.setSubjectId(jdbc.queryForObject("select id from subjects where code='math'",Long.class));
            request.setGradeId(jdbc.queryForObject("select id from grades where grade_no=4",Long.class)); request.setQuestionCount(1); request.setRoundSeconds(20); request.setMaxPlayers(4); request.setLanguage("ru");
            result.put("legacyCreateAndStart",check(()->{var created=service.createRoom("migration@example.test",request); return service.start(created.code(),"migration@example.test");}));
            Long before=jdbc.queryForObject("select count(*) from quiz_answers a join quiz_rounds r on r.id=a.round_id join quiz_rooms q on q.id=r.room_id where q.game_version_id is not null",Long.class);
            result.put("scheduler",check(()->{service.enforceActiveRoomTimeouts(); return "done";}));
            Long after=jdbc.queryForObject("select count(*) from quiz_answers a join quiz_rounds r on r.id=a.round_id join quiz_rooms q on q.id=r.room_id where q.game_version_id is not null",Long.class);
            result.put("newAnswersBefore",before); result.put("newAnswersAfter",after);
            result.put("foreignKeys",check(()->jdbc.queryForObject("select count(*) from information_schema.table_constraints where constraint_schema=current_schema() and constraint_type='FOREIGN KEY'",Long.class)));
        } catch(Throwable ex) { result.put("startup",failure(ex)); }
        Files.writeString(output,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
    private static String check(java.util.concurrent.Callable<?> operation) {
        try {operation.call(); return "PASS";} catch(Throwable ex) {return failure(ex);}
    }
    private static String failure(Throwable ex) {
        Throwable root=ex; while(root.getCause()!=null && root.getCause()!=root) root=root.getCause();
        return "FAIL: "+root.getClass().getSimpleName()+": "+Objects.toString(root.getMessage(),"");
    }
}
