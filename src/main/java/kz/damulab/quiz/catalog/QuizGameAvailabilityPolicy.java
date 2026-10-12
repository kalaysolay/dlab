package kz.damulab.quiz.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Единые правила видимости для будущих каталога/create/replay и уже созданной WAITING-комнаты. */
@Service
public class QuizGameAvailabilityPolicy {
    private final QuizGameRepository games;
    private final QuizWordGameConfigRepository words;
    private final QuizFactGameConfigRepository facts;

    public QuizGameAvailabilityPolicy(QuizGameRepository games, QuizWordGameConfigRepository words, QuizFactGameConfigRepository facts) {
        this.games = games; this.words = words; this.facts = facts;
    }

    /** Разрешает новую комнату или replay только по видимой текущей публикации; прямой POST не обходит правило. */
    @Transactional(readOnly = true)
    public QuizGameVersion requireNewRoom(String code) {
        QuizGame game = games.findByCode(code).orElseThrow(() -> new ContentValidationException("Игра не найдена"));
        if (!visible(game)) throw new ContentValidationException("Игра недоступна для новых комнат");
        requireWaitingStart(game.getPublishedVersion()); return game.getPublishedVersion();
    }

    /** Проверяет архив игры/источника для закреплённой версии. Скрытие не запрещает прежний WAITING start. */
    @Transactional(readOnly = true)
    public void requireWaitingStart(QuizGameVersion pinned) {
        if (pinned.getGame().getStatus() == EditorialStatus.ARCHIVED || pinned.getStatus() != EditorialStatus.PUBLISHED || pinned.getPublishedAt() == null) throw new ContentValidationException("Игра архивирована или версия не опубликована");
        if (pinned.getGameType() == QuizGameType.WORD_TRANSLATION) {
            VocabularySet set = words.findById(pinned.getId()).orElseThrow(() -> new ContentValidationException("Источник отсутствует")).getVocabularySet();
            if (set.getStatus() == EditorialStatus.ARCHIVED || set.getPublishedVersion() == null || set.getPublishedVersion().getStatus() != EditorialStatus.PUBLISHED) throw new ContentValidationException("Словарный набор недоступен");
        } else if (pinned.getGameType() == QuizGameType.TRUE_FALSE) {
            FactSet set = facts.findById(pinned.getId()).orElseThrow(() -> new ContentValidationException("Источник отсутствует")).getFactSet();
            if (set.getStatus() != EditorialStatus.PUBLISHED) throw new ContentValidationException("Набор утверждений недоступен");
        }
    }

    /** Возвращает административную доступность карточки тем же правилом, что и новая комната. */
    public boolean visible(QuizGame game) {
        return game.isCatalogVisible() && game.getStatus() != EditorialStatus.ARCHIVED
                && game.getPublishedVersion() != null && game.getPublishedVersion().getStatus() == EditorialStatus.PUBLISHED
                && game.getPublishedVersion().getPublishedAt() != null;
    }
}
