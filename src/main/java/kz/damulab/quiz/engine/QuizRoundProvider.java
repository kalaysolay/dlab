package kz.damulab.quiz.engine;

import java.util.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;

/** Контракт банка: подготавливает пригодные уникальные источники в транзакции фабрики, ничего не сохраняет. */
public interface QuizRoundProvider {
    /** Возвращает обслуживаемый тип игры. */
    QuizGameType gameType();
    /** Проверяет текущую публикацию и все требуемые классы; возвращает закрытые сборщики снимков. */
    List<Source> prepare(QuizRoom room, Set<Long> requiredGradeIds);

    /** Достаточность контента без создания сборщиков всех снимков; возвращает точное число при нехватке. */
    default int countUpTo(QuizRoom room, Set<Long> requiredGradeIds, int limit) {
        return Math.min(limit,prepare(room,requiredGradeIds).size());
    }

    /** Источник версии и сборщик; фабрика выбирает источник ровно один раз и назначает общий порядок. */
    record Source(Long versionId, Builder builder) {}
    /** Создаёт полный несохранённый снимок; ошибка откатывает весь набор. */
    @FunctionalInterface
    interface Builder {
        /** Собирает раунд с заданным номером и серверным перемешиванием. */
        QuizRound build(int orderNo, Random random);
    }
}
