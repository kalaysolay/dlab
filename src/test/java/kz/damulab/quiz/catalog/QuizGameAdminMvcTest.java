package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import kz.damulab.quiz.QuizRoomRepository;

/** Реальная Thymeleaf/MVC-приёмка форм, полного workflow, CSRF, ролей и безопасного предпросмотра. */
@AutoConfigureMockMvc
@Transactional
class QuizGameAdminMvcTest extends QuizGameTestSupport {
    @Autowired MockMvc mvc;
    @Autowired QuizRoomRepository rooms;

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void publishedGameWithNewDraftRendersAfterServiceTransactionCloses() throws Exception {
        Long id = publishGame(factForm(1)).getId();
        try {
            QuizGameForm f = games.get(id).form(); f.setTitleRu("Отдельная новая редакция"); games.saveDraft(id, f, admin());
            for (String suffix : new String[]{"edit", "content", "publish"}) {
                mvc.perform(get("/admin/quiz-games/{id}/{page}", id, suffix).with(user("admin@damulab.kz").roles("ADMIN")))
                        .andExpect(status().isOk()).andExpect(content().string(containsString("Отдельная новая редакция")));
            }
            assertThat(games.get(id).game().getPublishedVersion().getTitleRu()).isEqualTo("Тестовая игра");
        } finally { games.archive(id, revision(id), admin()); }
    }

    @Test
    void allPagesRenderAndGetDoesNotCreateDraftOrRoom() throws Exception {
        QuizGame game = games.create(factForm(1), admin()); long rev = revision(game.getId()), roomCount = rooms.count(), versionCount = versions.count();
        for (String path : new String[]{"/admin/quiz-games", "/admin/quiz-games/new", "/admin/quiz-games/" + game.getId() + "/edit", "/admin/quiz-games/" + game.getId() + "/content", "/admin/quiz-games/" + game.getId() + "/publish"}) {
            mvc.perform(get(path).with(user("admin@damulab.kz").roles("ADMIN"))).andExpect(status().isOk());
        }
        assertThat(revision(game.getId())).isEqualTo(rev); assertThat(rooms.count()).isEqualTo(roomCount); assertThat(versions.count()).isEqualTo(versionCount);
    }

    @Test
    void adminCreatesReviewsPublishesAndHidesThroughPostPages() throws Exception {
        FactSet set = factSet(1); String code = code();
        mvc.perform(post("/admin/quiz-games").with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                .param("code", code).param("gameType", "TRUE_FALSE").param("factSetId", set.getId().toString())
                .param("titleRu", "MVC игра").param("titleKk", "MVC ойыны").param("descriptionRu", "Описание").param("descriptionKk", "Сипаттама")
                .param("defaultQuestionCount", "1").param("maxQuestionCount", "1"))
                .andExpect(status().is3xxRedirection());
        QuizGame game = gameRepository.findByCode(code).orElseThrow();
        action(game, "submit"); action(game, "approve"); action(game, "publish");
        assertThat(game.getPublishedVersion()).isNotNull();
        mvc.perform(post("/admin/quiz-games/{id}/visibility", game.getId()).with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                .param("expectedRevision", Long.toString(revision(game.getId()))).param("visible", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(game.isCatalogVisible()).isFalse();
    }

    @Test
    void studentParentAndAnonymousCannotMutateAndCsrfIsRequired() throws Exception {
        QuizGame game = games.create(factForm(1), admin()); long rev = revision(game.getId());
        for (String role : new String[]{"STUDENT", "PARENT"}) {
            mvc.perform(get("/admin/quiz-games").with(user("other@example.test").roles(role))).andExpect(redirectedUrl("/access-denied"));
            mvc.perform(post("/admin/quiz-games/{id}/archive", game.getId()).with(user("other@example.test").roles(role)).with(csrf())
                    .param("expectedRevision", Long.toString(rev))).andExpect(redirectedUrl("/access-denied"));
        }
        mvc.perform(get("/admin/quiz-games")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/quiz-games/{id}/archive", game.getId()).with(user("admin@damulab.kz").roles("ADMIN"))
                .param("expectedRevision", Long.toString(rev))).andExpect(redirectedUrl("/access-denied"));
        assertThat(revision(game.getId())).isEqualTo(rev);
    }

    @Test
    void directPostCannotPublishUnreviewedOrInsufficientContentAndChangingGetDoesNotExist() throws Exception {
        QuizGame game = games.create(factForm(0), admin());
        mvc.perform(post("/admin/quiz-games/{id}/publish", game.getId()).with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                .param("expectedRevision", Long.toString(revision(game.getId()))).param("confirmWarnings", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("formErrors"));
        assertThat(game.getPublishedVersion()).isNull();
        mvc.perform(get("/admin/quiz-games/{id}/archive", game.getId()).with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
        assertThat(game.getStatus()).isEqualTo(EditorialStatus.DRAFT);
    }

    @Test
    void enteredHtmlIsEscapedInListFormAndPreviewWithoutAnswerKeys() throws Exception {
        QuizGameForm f = factForm(1); f.setTitleRu("<script>alert(1)</script>"); f.setDescriptionKk("<img src=x onerror=alert(2)>");
        QuizGame game = games.create(f, admin());
        for (String path : new String[]{"/admin/quiz-games", "/admin/quiz-games/" + game.getId() + "/edit", "/admin/quiz-games/" + game.getId() + "/publish"}) {
            mvc.perform(get(path).with(user("admin@damulab.kz").roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                    .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                    .andExpect(content().string(not(containsString("answerKey")))).andExpect(content().string(not(containsString("truthValue"))));
        }
    }

    @Test
    void invalidFormKeepsEnteredSafeValuesAndDoesNotSave() throws Exception {
        long before = gameRepository.count(); String code = code();
        mvc.perform(post("/admin/quiz-games").with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                .param("code", code).param("titleRu", "Сохрани введённое").param("maxPlayers", "8"))
                .andExpect(status().isOk()).andExpect(view().name("admin/quiz-game-form"))
                .andExpect(content().string(containsString("Сохрани введённое"))).andExpect(model().attributeExists("formErrors"));
        assertThat(gameRepository.count()).isEqualTo(before);
    }

    private void action(QuizGame game, String action) throws Exception {
        mvc.perform(post("/admin/quiz-games/{id}/{action}", game.getId(), action).with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                .param("expectedRevision", Long.toString(revision(game.getId()))))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("successMessage", "Действие выполнено"));
    }
}
