package kz.damulab.speaking;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Исторические тесты держат словарную схему на V58, но запускают современный Hibernate validate.
 * Добавляем только независимые таблицы speaking из настоящего DDL, не применяя V59+ к проверяемому словарю.
 */
public final class HistoricalSpeakingSchema {
    private HistoricalSpeakingSchema() {}
    public static void install(String url, String user, String password, String schema) {
        try(var connection=DriverManager.getConnection(url,user,password)) {
            connection.setSchema(schema);
            try(var tables=connection.getMetaData().getTables(null,schema,"speaking_sessions",new String[]{"TABLE"})) {
                if(tables.next()) return;
            }
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V67__speaking_module.sql"),
                    new ClassPathResource("db/migration/V68__speaking_voice.sql")).populate(connection);
        } catch(SQLException exception) {
            throw new IllegalStateException("Cannot prepare historical speaking schema",exception);
        }
    }
}
