package kz.damulab.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class UniqueUserPhoneMigrationTest {

    @Test
    void preservesHistoricalDuplicatesAfterPhoneNormalization() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:unique-user-phone;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table app_users (
                            id bigint primary key,
                            phone varchar(64),
                            created_at timestamp with time zone not null
                        )
                        """);
                statement.execute("""
                        create table parent_profiles (
                            user_id bigint not null unique,
                            phone varchar(64)
                        )
                        """);
                statement.execute("""
                        insert into app_users (id, phone, created_at) values
                            (10, '8 (707) 304-14-12', timestamp with time zone '2025-01-01 00:00:00+00'),
                            (20, '+77073041412', timestamp with time zone '2025-02-01 00:00:00+00'),
                            (30, null, timestamp with time zone '2025-03-01 00:00:00+00')
                        """);
                statement.execute("insert into parent_profiles (user_id, phone) values (30, '+77001112233')");
            }

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V31__unique_user_phone.sql"));

            try (Statement statement = connection.createStatement();
                 ResultSet phones = statement.executeQuery("select phone from app_users order by id")) {
                assertThat(phones.next()).isTrue();
                assertThat(phones.getString("phone")).isEqualTo("+77073041412");
                assertThat(phones.next()).isTrue();
                assertThat(phones.getString("phone")).isEqualTo("+77073041412");
                assertThat(phones.next()).isTrue();
                assertThat(phones.getString("phone")).isEqualTo("+77001112233");
                assertThat(phones.next()).isFalse();
            }

            try (Statement statement = connection.createStatement();
                 ResultSet columns = statement.executeQuery("""
                         select count(*)
                         from information_schema.columns
                         where table_name = 'parent_profiles' and column_name = 'phone'
                         """)) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt(1)).isZero();
            }
        }
    }
}
