package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Добавляет явный вид комнаты, сохраняя исходные ответы и результаты прежних игр. */
public class V56__student_quiz_rooms extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 5601; }

    @Override public void migrate(Context context) throws Exception {
        try (var sql = context.getConnection().createStatement()) {
            // Активная игра старой версии должна закончиться до обновления: у неё нет снимков
            // нового движка и достоверной длительности ответов. Историю не реконструируем.
            try (var rows = sql.executeQuery("select count(*) from quiz_rooms where game_version_id is null and status='ACTIVE'")) {
                rows.next();
                if (rows.getLong(1) != 0) throw new IllegalStateException("V56: завершите активные предметные викторины предыдущей версией перед обновлением");
            }
            sql.execute("alter table quiz_rooms add column room_kind varchar(32) not null default 'HISTORY'");
            sql.execute("update quiz_rooms set room_kind='CATALOG_GAME' where game_version_id is not null");
            // В старой WAITING раунды уже созданы без снимков. Не удаляем их и не выдаём
            // за новые: закрываем только лобби, исходные вопросы/участники остаются в истории.
            sql.execute("update quiz_rooms set status='EXPIRED' where game_version_id is null and status='WAITING'");
            sql.execute("alter table quiz_rooms add constraint ck_quiz_room_kind check (room_kind in ('HISTORY','CATALOG_GAME','CUSTOM_SUBJECT'))");
            sql.execute("alter table quiz_rooms add constraint ck_quiz_room_kind_source check ((room_kind='CATALOG_GAME' and game_version_id is not null) or (room_kind in ('HISTORY','CUSTOM_SUBJECT') and game_version_id is null))");
        }
    }
}
