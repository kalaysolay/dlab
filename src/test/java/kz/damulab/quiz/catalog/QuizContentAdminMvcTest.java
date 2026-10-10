package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Comparator;
import java.util.UUID;
import kz.damulab.users.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** MVC-приёмка ADMIN, CSRF, отсутствия изменяющих GET и HTML-экранирования банков. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuizContentAdminMvcTest {
    @Autowired MockMvc mvc;
    @Autowired VocabularyAdminService vocabulary;
    @Autowired FactAdminService facts;
    @Autowired AppUserRepository users;

    @Test
    void adminPagesRenderButStudentCannotOpenThem() throws Exception {
        mvc.perform(get("/admin/vocabulary").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/vocabulary-bank"));
        mvc.perform(get("/admin/facts").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/fact-bank"));
        mvc.perform(get("/admin/vocabulary").with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
    }

    @Test
    void mutationRequiresCsrfAndHasNoGetVariant() throws Exception {
        mvc.perform(post("/admin/facts/sets").with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("code", "facts-" + UUID.randomUUID()).param("titleRu", "Факты")
                        .param("titleKk", "Деректер").param("sourcePolicy", "Источники"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(get("/admin/facts/sets/1/publish").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/admin/facts/sets").with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                        .param("code", "facts-" + UUID.randomUUID()).param("titleRu", "Факты")
                        .param("titleKk", "Деректер").param("sourcePolicy", "Источники"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/facts"));
    }

    @Test
    void enteredTextIsEscapedInAdministrativeList() throws Exception {
        Long admin = users.findByEmailIgnoreCase("admin@damulab.kz").orElseThrow().getId();
        VocabularySetForm sf = new VocabularySetForm(); sf.setCode("escape-" + UUID.randomUUID()); sf.setSourceLanguage("en"); sf.setTargetLanguage("ru"); sf.setTitleRu("Набор"); sf.setTitleKk("Жинақ"); sf.setSourceReference("Источник");
        VocabularySet set = vocabulary.createSet(sf, admin);
        VocabularyItemForm f = new VocabularyItemForm(); f.setSetId(set.getId()); f.setSourceText("<script>alert(1)</script>");
        f.setPrimaryAnswer("безопасно"); f.setMeaningNoteRu("Значение"); f.setDifficulty(1); f.setSourceReference("Источник"); f.setUniversalAudience(true);
        vocabulary.createItem(f, admin);

        mvc.perform(get("/admin/vocabulary").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void allContentFormsRenderOnExistingAdminLayout() throws Exception {
        Long admin = users.findByEmailIgnoreCase("admin@damulab.kz").orElseThrow().getId();
        VocabularySetForm sf = new VocabularySetForm(); sf.setCode("forms-" + UUID.randomUUID()); sf.setSourceLanguage("en"); sf.setTargetLanguage("ru"); sf.setTitleRu("Формы"); sf.setTitleKk("Пішіндер"); sf.setSourceReference("Источник");
        VocabularySet set = vocabulary.createSet(sf, admin);
        VocabularyItemForm itemForm = new VocabularyItemForm(); itemForm.setSetId(set.getId()); itemForm.setSourceText("render"); itemForm.setPrimaryAnswer("показать"); itemForm.setMeaningNoteRu("Значение"); itemForm.setDifficulty(1); itemForm.setSourceReference("Источник"); itemForm.setUniversalAudience(true);
        VocabularyItem item = vocabulary.createItem(itemForm, admin);

        mvc.perform(get("/admin/vocabulary/sets/{id}/edit", set.getId()).with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/vocabulary-set-form"));
        mvc.perform(get("/admin/vocabulary/items/{id}/edit", item.getId()).with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/vocabulary-form"));
        mvc.perform(get("/admin/facts/statements/new").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/fact-form"));
    }

    @Test
    void adminCompletesWordAndFalseFactWorkflowThroughPostPages() throws Exception {
        Long admin = users.findByEmailIgnoreCase("admin@damulab.kz").orElseThrow().getId();
        VocabularySetForm sf = new VocabularySetForm(); sf.setCode("mvc-words-" + UUID.randomUUID()); sf.setSourceLanguage("en"); sf.setTargetLanguage("ru"); sf.setTitleRu("MVC слова"); sf.setTitleKk("MVC сөздер"); sf.setSourceReference("Источник");
        VocabularySet wordSet = vocabulary.createSet(sf, admin);

        mvc.perform(post("/admin/vocabulary/items").with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                        .param("setId", wordSet.getId().toString()).param("sourceText", "workflow")
                        .param("primaryAnswer", "процесс").param("meaningNoteRu", "Одно значение")
                        .param("difficulty", "2").param("sourceReference", "Dictionary").param("universalAudience", "true"))
                .andExpect(status().is3xxRedirection());
        VocabularyItem word = vocabulary.listItems().stream().max(Comparator.comparing(VocabularyItem::getId)).orElseThrow();
        postAction("/admin/vocabulary/items/" + word.getId() + "/submit");
        postAction("/admin/vocabulary/items/" + word.getId() + "/approve");
        postAction("/admin/vocabulary/items/" + word.getId() + "/publish");
        assertThat(vocabulary.requireItem(word.getId()).getPublishedVersion()).isNotNull();

        FactSetForm fsf = new FactSetForm(); fsf.setCode("mvc-facts-" + UUID.randomUUID()); fsf.setTitleRu("MVC факты"); fsf.setTitleKk("MVC деректер"); fsf.setSourcePolicy("Проверяемые источники");
        FactSet factSet = facts.createSet(fsf, admin);
        mvc.perform(post("/admin/facts/statements").with(user("admin@damulab.kz").roles("ADMIN")).with(csrf())
                        .param("setId", factSet.getId().toString()).param("textRu", "Земля плоская")
                        .param("textKk", "Жер жалпақ").param("truthValue", "false")
                        .param("explanationRu", "Земля имеет форму геоида").param("explanationKk", "Жер геоид пішінді")
                        .param("sourceReference", "Научный источник").param("universalAudience", "true"))
                .andExpect(status().is3xxRedirection());
        FactStatement fact = facts.listStatements().stream().max(Comparator.comparing(FactStatement::getId)).orElseThrow();
        postAction("/admin/facts/statements/" + fact.getId() + "/submit");
        postAction("/admin/facts/statements/" + fact.getId() + "/approve");
        postAction("/admin/facts/statements/" + fact.getId() + "/publish");
        assertThat(facts.requireStatement(fact.getId()).getPublishedVersion().isTruthValue()).isFalse();
    }

    private void postAction(String path) throws Exception {
        mvc.perform(post(path).with(user("admin@damulab.kz").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
