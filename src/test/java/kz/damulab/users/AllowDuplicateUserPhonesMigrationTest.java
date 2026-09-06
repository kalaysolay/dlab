package kz.damulab.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AllowDuplicateUserPhonesMigrationTest {

    @Test
    void dropsExistingUniqueConstraintWithoutChangingPhoneData() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:allow-duplicate-user-phones;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table app_users (
                            id bigint primary key,
                            phone varchar(64),
                            constraint uq_app_users_phone unique (phone)
                        )
                        """);
                statement.execute("insert into app_users (id, phone) values (10, '+77073041412')");
            }

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V35__allow_duplicate_user_phones.sql"));

            try (Statement statement = connection.createStatement()) {
                statement.execute("insert into app_users (id, phone) values (20, '+77073041412')");
                try (ResultSet phones = statement.executeQuery("""
                        select phone, count(*) as phone_count
                        from app_users
                        group by phone
                        """)) {
                    assertThat(phones.next()).isTrue();
                    assertThat(phones.getString("phone")).isEqualTo("+77073041412");
                    assertThat(phones.getInt("phone_count")).isEqualTo(2);
                    assertThat(phones.next()).isFalse();
                }
            }
        }
    }
}
