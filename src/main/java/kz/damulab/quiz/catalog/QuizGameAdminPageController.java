package kz.damulab.quiz.catalog;

import kz.damulab.content.*;
import kz.damulab.questions.QuestionType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Реальные SSR-страницы редакционных игр; ADMIN/CSRF проверяются общей security-конфигурацией. */
@Controller
@RequestMapping("/admin/quiz-games")
public class QuizGameAdminPageController {
    private final QuizGameAdminService games;
    private final CurrentAdminService admin;
    private final GradeRepository grades;
    private final SubjectRepository subjects;
    private final VocabularySetRepository words;
    private final FactSetRepository facts;

    public QuizGameAdminPageController(QuizGameAdminService games, CurrentAdminService admin, GradeRepository grades,
            SubjectRepository subjects, VocabularySetRepository words, FactSetRepository facts) {
        this.games = games; this.admin = admin; this.grades = grades; this.subjects = subjects; this.words = words; this.facts = facts;
    }

    /** Показывает пакетно рассчитанный список, редакционный статус и независимую видимость. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String query, @RequestParam(required = false) QuizGameType type,
            @RequestParam(required = false) EditorialStatus status, @RequestParam(required = false) String language,
            @RequestParam(required = false) Boolean hasErrors, Model model) {
        base(model); model.addAttribute("games", games.list(query, type, status, language, hasErrors));
        model.addAttribute("query", query); model.addAttribute("selectedType", type); model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedLanguage", language); model.addAttribute("selectedHasErrors", hasErrors);
        return "admin/quiz-game-list";
    }

    /** Открывает пустую типизированную форму; GET не создаёт игру или черновую версию. */
    @GetMapping("/new")
    public String newGame(Model model) { return form(model, new QuizGameForm(), null); }

    /** Показывает отдельную редакцию или текущую публикацию как основу следующего черновика. */
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) { return form(model, games.get(id).form(), id); }

    /** Создаёт игру; ошибки сохраняют введённую форму и не оставляют частично сохранённых конфигураций. */
    @PostMapping
    public String create(@ModelAttribute("gameForm") QuizGameForm value, BindingResult binding,
            Authentication auth, Model model) {
        if (binding.hasErrors()) { model.addAttribute("formErrors", java.util.List.of("Проверьте числовые значения и выбранные настройки")); return form(model, value, null); }
        try { return "redirect:/admin/quiz-games/" + games.create(value, admin.requireId(auth)).getId() + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); return form(model, value, null); }
        catch (DataIntegrityViolationException ex) { model.addAttribute("formErrors", java.util.List.of("Код уже занят или источник изменился. Проверьте настройки")); return form(model, value, null); }
    }

    /** Сохраняет новую редакцию с проверкой revision; непроверенные поля не меняют публикацию. */
    @PostMapping("/{id}")
    public String save(@PathVariable Long id, @ModelAttribute("gameForm") QuizGameForm value, BindingResult binding,
            Authentication auth, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) { model.addAttribute("formErrors", java.util.List.of("Проверьте числовые значения и выбранные настройки")); return form(model, value, id); }
        try { games.saveDraft(id, value, admin.requireId(auth)); redirect.addFlashAttribute("successMessage", "Новая черновая редакция сохранена"); return "redirect:/admin/quiz-games/" + id + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); return form(model, value, id); }
        catch (DataIntegrityViolationException ex) { model.addAttribute("formErrors", java.util.List.of("Код уже занят или источник изменился. Обновите страницу")); return form(model, value, id); }
    }

    /** Показывает реальные счётчики и причины исключения со ссылками на соответствующий банк. */
    @GetMapping("/{id}/content")
    public String content(@PathVariable Long id, Model model) { base(model); model.addAttribute("item", games.get(id)); return "admin/quiz-game-content"; }

    /** Показывает checklist и текстовый RU/KK-предпросмотр, не создавая ученическую комнату. */
    @GetMapping("/{id}/publish")
    public String publication(@PathVariable Long id, Model model) { base(model); model.addAttribute("item", games.get(id)); return "admin/quiz-game-publish"; }

    /** Публикует только заново проверенную актуальную редакцию; подтверждение предупреждений — явный параметр. */
    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id, @RequestParam long expectedRevision,
            @RequestParam(defaultValue = "false") boolean confirmWarnings, Authentication auth, RedirectAttributes redirect) {
        return command(id, redirect, () -> games.publish(id, expectedRevision, confirmWarnings, admin.requireId(auth)));
    }

    /** Выполняет review/approve/archive отдельно от чтения страниц. */
    @PostMapping("/{id}/{action:submit|approve|archive}")
    public String action(@PathVariable Long id, @PathVariable String action, @RequestParam long expectedRevision,
            Authentication auth, RedirectAttributes redirect) {
        return command(id, redirect, () -> { if ("archive".equals(action)) games.archive(id, expectedRevision, admin.requireId(auth));
            else games.transition(id, expectedRevision, action, admin.requireId(auth)); });
    }

    /** Скрывает или возвращает опубликованную игру без смены её учебной версии. */
    @PostMapping("/{id}/visibility")
    public String visibility(@PathVariable Long id, @RequestParam long expectedRevision, @RequestParam boolean visible,
            Authentication auth, RedirectAttributes redirect) {
        return command(id, redirect, () -> games.setVisibility(id, expectedRevision, visible, admin.requireId(auth)));
    }

    /** Обрабатывает бизнес-ошибки чтения, не раскрывая SQL или внутренние исключения. */
    @ExceptionHandler(ContentValidationException.class)
    public String businessError(ContentValidationException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("formErrors", ex.getErrors()); return "redirect:/admin/quiz-games";
    }

    private String command(Long id, RedirectAttributes redirect, Runnable action) {
        try { action.run(); redirect.addFlashAttribute("successMessage", "Действие выполнено"); }
        catch (ContentValidationException ex) { redirect.addFlashAttribute("formErrors", ex.getErrors()); }
        catch (org.springframework.dao.ConcurrencyFailureException ex) {
            // PostgreSQL REPEATABLE_READ может отклонить публикацию после конкурентного изменения строки под lock.
            redirect.addFlashAttribute("formErrors", java.util.List.of("Игра изменена конкурентным действием. Обновите страницу и повторите проверку"));
        }
        return "redirect:/admin/quiz-games/" + id + "/publish";
    }
    private String form(Model model, QuizGameForm value, Long id) {
        base(model); model.addAttribute("gameForm", value); model.addAttribute("gameId", id);
        model.addAttribute("item", id == null ? null : games.get(id));
        model.addAttribute("grades", grades.findAllByOrderByGradeNoAsc()); model.addAttribute("subjects", subjects.findAll());
        model.addAttribute("wordSets", words.findForGameDiagnostics()); model.addAttribute("factSets", facts.findAll());
        model.addAttribute("gradeModes", GradeSelectionMode.values()); model.addAttribute("audiencePolicies", AudiencePolicy.values());
        model.addAttribute("questionTypes", QuestionType.values()); model.addAttribute("cardStyles", QuizGameValidation.CARD_STYLES.stream().sorted().toList());
        return "admin/quiz-game-form";
    }
    private void base(Model model) {
        model.addAttribute("activeAdminNav", "quizGames"); model.addAttribute("gameTypes", QuizGameType.values()); model.addAttribute("statuses", EditorialStatus.values());
    }
}
