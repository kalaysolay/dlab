-- Считаем посещения отдельно от результатов: отсутствие тестов не означает отсутствие входов.
create table student_visit_days (
    student_id bigint not null references student_profiles(id) on delete cascade,
    visit_day date not null,
    primary key (student_id, visit_day)
);
create table parent_report_activity_tracking (
    id integer primary key,
    started_at timestamp with time zone not null
);
insert into parent_report_activity_tracking values (1, current_timestamp);
