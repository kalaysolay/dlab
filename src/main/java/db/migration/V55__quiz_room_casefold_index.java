package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * QA-05: индексирует case-insensitive поиск комнаты в PostgreSQL.
 * Индекс не уникальный: исторические коды и строки не нормализуются/не удаляются.
 * H2 не поддерживает индексы выражений; там проверяется функциональный контракт.
 */
public class V55__quiz_room_casefold_index extends BaseJavaMigration {
    private static final String SQL="create index idx_quiz_rooms_code_lower on quiz_rooms (lower(code))";
    private static final String CONTRACT="PostgreSQL:"+SQL+";H2:no-expression-index;other:reject;v1";

    /** Фиксирует текст и ветвление миграции для Flyway validate. */
    @Override public Integer getChecksum() {
        var checksum=new CRC32(); checksum.update(CONTRACT.getBytes(StandardCharsets.UTF_8)); return (int)checksum.getValue();
    }

    /** Добавляет индекс без изменения данных; неизвестный dialect не пропускается молча. */
    @Override public void migrate(Context context) throws Exception {
        String product=context.getConnection().getMetaData().getDatabaseProductName();
        if(product.equals("H2")) return;
        if(!product.equals("PostgreSQL")) throw new IllegalStateException("Unsupported quiz index database: "+product);
        // Transactional CREATE INDEX: время блокировки на реальной копии измеряет QA-06 до развёртывания.
        try(var statement=context.getConnection().createStatement()){statement.execute(SQL);}
    }
}
