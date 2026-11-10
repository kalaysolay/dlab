package kz.damulab.parentreports;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Считает все завершённые занятия за полуоткрытый период, без ограничения «последние 10». */
@Service
public class ParentReportBuilder {
    private final JdbcTemplate db;
    public ParentReportBuilder(JdbcTemplate db) { this.db = db; }
    public record Report(String fullText, String messageText) {}

    /** Родители получают оба языка без отдельного выбора; три записи оставляют запас до 4096. */
    public Report bilingual(long student, LocalDate week, ZoneId zone) {
        var ru = buildReport(student, week, zone, "ru", 3);
        var kk = buildReport(student, week, zone, "kk", 3);
        return new Report("Қазақша\n" + kk.fullText() + "\nРусский\n" + ru.fullText(),
                "Қазақша\n" + kk.messageText() + "\n\nРусский\n" + ru.messageText());
    }

    /** Полная и краткая версии строятся одновременно из одних данных. */
    public Report build(long student, LocalDate week, ZoneId zone, String language) {
        return buildReport(student, week, zone, language, 5);
    }
    private Report buildReport(long student, LocalDate week, ZoneId zone, String language, int limit) {
        boolean kk = "kk".equals(language);
        var start = week.atStartOfDay(zone).toOffsetDateTime();
        var end = week.plusWeeks(1).atStartOfDay(zone).toOffsetDateTime();
        String name = db.queryForObject("select u.full_name from student_profiles s join app_users u on u.id=s.user_id where s.id=?", String.class, student);
        String title = kk ? "coalesce(v.title_kk,v.title_ru,'Сабақ')" : "coalesce(v.title_ru,v.title_kk,'Урок')";
        var lessons = db.queryForList("select " + title + " as title from student_lecture_progress p join lectures l on l.id=p.lecture_id "
                + "left join lecture_versions v on v.id=l.current_version_id where p.student_profile_id=? and p.status='DONE' "
                + "and p.completed_at>=? and p.completed_at<? order by p.completed_at,p.id", student, start, end);
        String subject = kk ? "coalesce(s.title_kk,s.title_ru,'Тест')" : "coalesce(s.title_ru,s.title_kk,'Тест')";
        var tests = db.queryForList("select " + subject + " as title,r.correct_answers,r.total_questions from test_results r "
                + "join test_sessions t on t.id=r.session_id left join subjects s on s.id=t.subject_id where t.student_profile_id=? "
                + "and coalesce(t.finished_at,r.created_at)>=? and coalesce(t.finished_at,r.created_at)<? order by r.created_at,r.id", student,start,end);
        String game = kk ? "coalesce(g.title_kk,g.title_ru,s.title_kk,s.title_ru,'Викторина')" : "coalesce(g.title_ru,g.title_kk,s.title_ru,s.title_kk,'Викторина')";
        var quizzes = db.queryForList("select " + game + " as title,r.correct_answers,r.total_questions from quiz_results r "
                + "join quiz_participants p on p.id=r.participant_id join quiz_rooms q on q.id=r.room_id "
                + "left join subjects s on s.id=q.subject_id left join quiz_game_versions g on g.id=q.game_version_id where p.student_profile_id=? and r.created_at>=? and r.created_at<? order by r.created_at,r.id",student,start,end);
        String period = week.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "–"
                + week.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String header = kk ? "Сәлеметсіз бе!\n" + clean(name,100) + " есімді балаңыздың Damulab-тағы " + period + " кезеңіндегі оқу нәтижелері туралы есепті ұсынамыз.\n"
                : "Здравствуйте!\nОтправляем вам отчёт о занятиях ребёнка «" + clean(name,100) + "» в Damulab за период " + period + ".\n";
        StringBuilder full = new StringBuilder(header), brief = new StringBuilder(header);
        section(full,brief,kk ? "📚 Аяқталған сабақтар" : "📚 Завершённые уроки",lessons,false,kk,limit);
        section(full,brief,kk ? "✅ Тесттер" : "✅ Тесты",tests,true,kk,limit);
        section(full,brief,kk ? "🎮 Викториналар" : "🎮 Викторины",quizzes,true,kk,limit);
        if (lessons.isEmpty() && tests.isEmpty() && quizzes.isEmpty()) {
            long visits = db.queryForObject("select count(*) from student_visit_days where student_id=? and visit_day>=? and visit_day<?",Long.class,student,week,week.plusWeeks(1));
            var tracking = db.queryForObject("select started_at from parent_report_activity_tracking where id=1",OffsetDateTime.class);
            String empty;
            if (visits > 0) {
                empty = kk ? "\nОсы кезеңде балаңыз Damulab-қа кірген, бірақ аяқталған сабақтар, тесттер немесе викториналар тіркелмеді.\n"
                        : "\nЗа этот период ребёнок заходил в Damulab, но завершённых уроков, тестов или викторин пока нет.\n";
            } else if (!tracking.isAfter(start)) {
                empty = kk ? "\nОсы кезеңде балаңыздың Damulab-қа кіргені тіркелмеді. Аяқталған сабақтар, тесттер немесе викториналар жоқ.\n"
                        : "\nЗа этот период в Damulab не зафиксировано посещений ребёнка. Завершённых уроков, тестов или викторин нет.\n";
            } else {
                // Исторические недели до включения учёта не позволяют утверждать «не заходил».
                empty = kk ? "\nОсы аптада Damulab-та аяқталған сабақтар тіркелмеді. Бұл кезеңдегі кірулер туралы толық дерек жоқ.\n"
                        : "\nЗа эту неделю в Damulab не зафиксировано завершённых занятий. Полных данных о посещениях за этот период ещё нет.\n";
            }
            full.append(empty); brief.append(empty);
        }
        brief.append(kk ? "\nЕсептерді тоқтату: /stop" : "\nОстановить отчёты: /stop");
        return new Report(full.toString(),brief.toString());
    }

    private static void section(StringBuilder full, StringBuilder brief, String label, List<Map<String,Object>> rows, boolean scores, boolean kk, int limit) {
        if (rows.isEmpty()) return;
        String heading = "\n" + label + ": " + rows.size() + "\n";
        full.append(heading); brief.append(heading);
        int correct=0,total=0;
        for (int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            String line="• " + clean(Objects.toString(row.get("title"),""),100);
            if (scores) {
                int c=((Number)row.get("correct_answers")).intValue(), t=((Number)row.get("total_questions")).intValue();
                correct+=c; total+=t; line+=" — " + c + "/" + t;
            }
            full.append(line).append('\n'); if(i<limit) brief.append(line).append('\n');
        }
        if(rows.size()>limit) brief.append(kk ? "Толық есепте тағы: " : "Ещё в полном отчёте: ").append(rows.size()-limit).append('\n');
        if(scores && total>0) {
            String sum=(kk ? "Дұрыс жауаптар: " : "Верных ответов: ")+correct+"/"+total+" ("+Math.round(100.0*correct/total)+"%)\n";
            full.append(sum); brief.append(sum);
        }
    }
    private static String clean(String value,int max) {
        String text=value.replaceAll("[\\p{Cntrl}]"," ");
        return text.length()>max ? text.substring(0,max)+"…" : text;
    }
}
