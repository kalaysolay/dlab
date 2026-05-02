# Stage 13 Template Inventory

Дата: 2026-04-29.

Scope: Stage 13 переоткрыт как UI visual parity task по root HTML reference screens и `admin/mockups/*`.
Mockup/reference HTML остаются source-of-truth для визуального слоя и не удаляются.

Backend routes, form actions, field names, CSRF и server-side answer checking сохраняются. Отдельный SPA не создается.

## Decisions In This Reopen Pass

- Student shell отделен от generic public/parent header: `templates/fragments/student-header.html`.
- Student pages теперь используют icon nav grid под brand row, как `student-home.html`: `/student`, `/student/tests`, `/student/analytics`, `/student/quiz`, achievements anchor, `/student/profile`.
- Generic header больше не рендерит student navigation; student pages не получают parent/admin links.
- Admin pages теперь используют explicit `app-shell admin-shell` без public header; sidebar остается единственным admin shell.
- Icons: добавлен локальный SVG sprite `static/icons/lucide-sprite.svg` вместо CDN.
- Language switch оставлен backend-backed: student POST `/student/profile/language`, public/parent/admin safe `?lang=` mechanism. Visual wrapper получил compact icon/select treatment.
- Fonts: shared head loads Google `Nunito`/`Manrope`, matching root references. Open risk: external Google Fonts dependency remains; local font assets are not present in repo.

## Actionable Screen Inventory

| Area | Reference file | Thymeleaf template | Missing visual pieces before this pass | Backend constraints | Planned / applied changes | Status after changes |
|---|---|---|---|---|---|---|
| Public home | `index.html` | `templates/index.html` | Footer/reviews/FAQ and lucide detail lighter than reference. | Keep real links `/login`, `/register`, role routes; no SPA. | Kept prior visual layer; no new backend risk. | Open visual gap: footer/reviews/FAQ still lighter. |
| Login | `auth.html` | `templates/auth/login.html` | Reference is one tabbed auth card; working app has split login/register routes. | Spring Security login must keep `username/password`; no email confirmation. | Kept split route, existing typography/layout. | Backend-safe compromise. |
| Register | `auth.html` | `templates/auth/register.html` | Register form alignment and line rhythm differed from login/reference card. | Preserve `registerForm`; no email confirmation. | Rebuilt register page with same `auth-grid`/tab shell as login and aligned field rows/spacing. | Improved parity; split-route auth remains backend-safe compromise. |
| Student dashboard | `student-home.html` | `templates/student/dashboard.html` | Generic header, CSS pseudo-icons, no reference nav grid, weaker active state. | GET `/student`; data from `dashboard`; no fake stats. | Swapped to student shell, local SVG icons, reference nav grid, compact language select, restored achievements modal trigger + empty state fallback, then softened modal backdrop with smooth transitions. | Improved parity; remaining gap is not pixel-perfect Tailwind spacing/SVG badges. |
| Student tests list | `student-tests.html` | `templates/student/tests.html` | Generic header/nav, less rich card composition. | POST `/student/test-sessions`; keep `testType`, `subjectId`, `gradeId`, `language`, `difficulty`, `questionCount`. | Student shell applied; existing backend form preserved. | Shell parity closed; test card parity still open. |
| Student active session | `student-test-session.html` | `templates/student/test-session.html`, `fragments/question-renderer.html` | Reference has single-question carousel/modals; working renders all questions in one server form. | Finish POST parses `answer_*`, `match_*`, `fill_*`; correct answers must stay server-side until result. | Student shell applied; server form preserved. | Open backend-flow compromise: no carousel. Browser flow blocked in clean test profile by no published questions. |
| Student result | `student-test-result.html` | `templates/student/test-result.html` | Reference result hero/review nav is richer; working lists all rows. | Result only after finish; correct answers allowed only here. | Student shell applied; result route/actions preserved. | Open visual gap; route smoke blocked without seeded published question/session. |
| Student analytics | `analytics.html` | `templates/student/analytics.html` | Generic header/nav; knowledge cloud less exact. | Use `analytics.knowledgeMap()`, `lastErrors()`, `timeline()`, `trajectory()`. | Student shell applied. | Shell parity closed; analytics content visuals still open. |
| Student quiz hub | `student-quiz.html`, `quiz-hub.html`, `quiz-create-room.html` | `templates/student/quiz-hub.html` | Reference has create-room/quick cards and presets; working keeps full backend form on hub. | POST `/student/quiz/rooms`, POST `/student/quiz/rooms/join`; preserve CSRF and request fields. | Student shell applied; backend flow preserved. | Open visual gap: embedded form instead of standalone create page. |
| Student quiz room | `quiz-room-host.html`, `quiz-room-guest.html`, `quiz-question.html` | `templates/student/quiz-room.html`, `quiz-room.js` | Host/guest/question views are merged in one backend state template. | Keep data attributes, CSRF meta and API `/api/quiz/rooms/{code}/answers`; server timeout/checking authoritative. | Student shell applied; no JS contract changes. | Backend-safe compromise; visual split still open. |
| Student quiz results | `quiz-results.html` | `templates/student/quiz-results.html` | Leaderboard table less exact. | GET `/student/quiz/rooms/{code}/results`. | Student shell applied. | Open visual gap. |
| Student lectures | `lecture-view-mobile.html` | `templates/student/lectures.html`, `templates/student/lecture.html` | No phone-shell/checkpoint view. | Published lecture routes only; full LMS checkpoint attempt post-MVP. | Student shell applied; no route change. | Accepted MVP compromise. |
| Student profile | `student-profile.html`, `profile.html` | `templates/student/profile.html` | Reference profile cards/avatar/parent links richer. | POST `/student/profile`; language also POSTs through header; preserve validation. | Student shell plus profile-specific typography (Manrope/Nunito weights and heading scale) applied. | Improved parity; composition still open. |
| Parent dashboard | `parent-home.html` | `templates/parent/dashboard.html` | Modal add-child tabs/detail feed not exact. | Preserve `/parent/children`, `/parent/link-codes/attach` POSTs and CSRF. | Kept prior backend-safe inline form visuals. | Open visual gap. |
| Parent child progress/connect | `connect.html`, `parent-home.html` | `templates/parent/child-details.html` | Standalone connect screen not implemented; QR/PIN flow lives in authenticated child detail. | Link code generation requires auth and POST `/parent/children/{id}/link-code`. | No route compromise added. | Backend-safe compromise. |
| Admin shell | `admin/mockups/*` | `templates/admin/*.html`, `fragments/admin-sidebar.html` | Public header was rendered then hidden via CSS `:has`. | Admin routes `/admin/**`; `/dashboard` role fallback only. | Admin templates now use `app-shell admin-shell` and do not render public header. | Closed shell gap. |
| Admin dashboard | `admin/mockups/index.html` | `templates/admin/dashboard.html` | Mockup KPI/ranking data not backed. | Do not fake analytics counts. | Admin shell explicit; existing dashboard cards preserved. | Open visual gap for data-rich KPIs. |
| Admin topics/tree | `topics-list.html`, `topic-tree-editor.html` | `admin/topics.html`, `admin/topic-tree.html` | Compact controls/tree polish lighter. | Preserve inline CRUD/delete/skills forms. | Admin shell explicit. | Partial parity. |
| Admin questions/form | `questions-list.html`, `question-create-manual.html` | `admin/questions.html`, `admin/question-form.html` | Dynamic add/remove option UI less exact. | Preserve `questionForm` field names, answer-key semantics, approve/publish/archive POSTs. | Admin shell explicit; AI generation kept as action inside `Вопросы` section, separate sidebar item removed to avoid duplicate navigation semantics; added subject/grade selectors with live topic reload. | Backend-safe compromise. |
| Admin health/import/AI | `question-ai-generate.html`, admin card patterns | `question-health.html`, `question-import.html`, `question-ai-generate.html` | Health/import no direct full mockup; AI cards less interactive. | Import creates `needs_review`; AI human review preserved. | Admin shell explicit; AI topic select now updates when subject/grade changes without page reload. | Partial parity. |
| Admin lectures | `lectures-list.html`, `lecture-editor.html`, `lecture-view-mobile.html` | `lectures.html`, `lecture-form.html` | No local Quill/KaTeX/phone-shell preview. | Safe textarea fallback; publish/archive POSTs; no CDN editor added. | Admin shell explicit. | Accepted MVP compromise. |
| Admin push | `push-notifications.html` | `push-notifications.html`, `admin-push-notifications.js` | Preview/server clock less exact. | Preserve create/update/cancel POSTs and payload validation. | Admin shell explicit. | Partial parity. |

## Verification Notes

- Targeted tests passed: `.\gradlew.bat test --tests kz.damulab.PageSecuritySmokeTest --tests kz.damulab.StudentEngagementIntegrationTest`.
- Browser smoke on `http://localhost:18088` passed 54 checks across desktop, 390px and 360px for public, student, parent and admin routes: no page-level horizontal overflow, no console errors, student nav has no admin/parent links, admin sidebar present.
- Screenshots and JSON report saved in `.run-logs/stage13-ui-parity/`.
- Student active session/result browser flow could not be reached in clean `test` profile because test start returns `published_questions_not_found`. Screenshot/report saved as `student-test-start-error-390.png` and `student-test-flow-smoke.json`.

## Open Visual Gaps

- Student active session still uses all-questions server form instead of reference carousel to preserve backend answer parsing and server-side checking.
- Student achievements are now visible from all student pages via shared modal, but detailed badge art still differs from mockup SVG set.
- Several non-dashboard student screens now have correct shell/header but still need deeper card-level parity.
- Public home/auth and parent dashboard remain visually lighter than their references.
- Admin content screens have correct shell but not all mockup interaction polish.
- Local font assets are absent; Google Fonts dependency remains an external asset risk.
- Local Quill/KaTeX rich editor assets remain a separate non-blocking task.

---

## 2026-04-30 Addendum (This Pass)

### Updated screens and status

| Area | Reference | Template | Applied in this pass | Status |
|---|---|---|---|---|
| Student active session | `student-test-session.html` | `templates/student/test-session.html` | Reworked to mockup-like flow: hero + timer, question nav grid, one-question view switching, finish/exit modals, clearer Fill-In hint; backend POST + field names unchanged. | Improved parity; backend-safe compromise (server form, not separate SPA carousel engine). |
| Student result | `student-test-result.html` | `templates/student/test-result.html` | Reworked result summary/nav/actions to closer mockup hierarchy, added “next wrong” helper, kept server-rendered question review. | Improved parity. |
| Question renderer (student test/result) | `student-test-session.html`, `student-test-result.html` | `templates/fragments/question-renderer.html` | Explicit Fill-In guidance for `[[...]]`, clearer field placeholders; answer checking flow unchanged. | Improved parity + UX clarity. |
| Student achievements modal behavior | `student-tests.html` modal pattern | `templates/fragments/student-header.html` + CSS | Modal script isolated to avoid global collisions, outside-click/escape close preserved, full-screen backdrop and centered card behavior preserved. | Closed behavior bug. |
| Public/auth shell | `auth.html` | `templates/auth/login.html`, `templates/auth/register.html`, `templates/fragments/header.html` | Fixed login/register tab alignment, tightened copy hierarchy, unified star brand mark in shared header. | Improved parity; split login/register route remains backend-safe compromise. |
| Parent screens | `parent-home.html`, `connect.html` | `templates/parent/dashboard.html`, `templates/parent/child-details.html` | Improved card hierarchy and section structure while keeping all existing forms/actions/CSRF. | Improved parity; modal-rich mockup behavior still lighter. |
| Admin IA + topic flow | `admin/mockups/*` | `templates/fragments/admin-sidebar.html`, `templates/admin/questions.html`, `templates/admin/question-ai-generate.html`, `templates/admin/question-form.html` | Removed duplicate AI action from questions header; single AI nav entry in sidebar; subject/grade/topic refresh hardened (loading/empty/error states) for manual + AI forms. | Improved parity + UX robustness. |
| Global typography scale | all | `static/css/app.css` | Added strict end-of-file overrides: less “airy” style, smaller headings, reduced radius/shadow, tighter auth/student/profile scale, modal overlay hardening. | Improved parity; local font files still absent. |

### Verification (this pass)

- `.\gradlew.bat test --tests kz.damulab.TestingHubIntegrationTest --tests kz.damulab.PageSecuritySmokeTest --tests kz.damulab.AuthFlowIntegrationTest --tests kz.damulab.ParentLinkIntegrationTest` passed.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- Browser smoke (desktop + 390 + 360) passed with `57/57` checks, `0` failures.  
  Artifacts: `.run-logs/stage13-ui-parity/browser-smoke-latest/` (copied from fresh run output).

### Remaining open visual gaps

- Student/quiz host-vs-guest screens are still unified in one backend template (`quiz-room.html`) instead of separate reference pages.
- Student/analytics knowledge cloud and some card-level details are still simpler than reference SVG-rich variants.
- Parent dashboard still does not fully replicate modal-heavy mockup interactions (kept backend-safe form-first flow).
- Public home footer/reviews/FAQ depth remains lighter than reference.
- Fonts are still loaded from Google Fonts (external dependency risk) until local/self-host assets are added.

---

## 2026-04-30 Finalization Addendum

### Closed in this final pass

- Local self-host fonts enabled:
  - `src/main/resources/static/fonts/manrope/Manrope-wght.ttf`
  - `src/main/resources/static/fonts/nunito/Nunito-wght.ttf`
  - `src/main/resources/static/css/fonts.css`
  - `fragments/pwa-head.html` switched from Google Fonts to local preload + stylesheet.
- Student parity deepened:
  - `templates/student/analytics.html` rebuilt to reference-like timeline/cloud/errors structure.
  - `templates/student/quiz-hub.html` updated to reference-like hub composition with quick cards + create/join forms.
  - `templates/student/quiz-room.html` split into host-style waiting, guest-style waiting and active-round block while preserving existing backend routes/JS contract.
  - `templates/student/quiz-results.html` moved to table-style final ranking layout closer to `quiz-results.html`.
- Admin deep parity pass applied:
  - `templates/fragments/admin-sidebar.html`
  - `templates/admin/questions.html`
  - `templates/admin/question-form.html`
  - `templates/admin/question-ai-generate.html`
  - admin-scoped parity layer in `app.css`.
- Public/auth/parent parity pass applied:
  - `templates/index.html`, `templates/auth/login.html`, `templates/auth/register.html`,
    `templates/parent/dashboard.html`, `templates/parent/child-details.html`,
    `templates/fragments/header.html`.

### Verification (final)

- `.\gradlew.bat test --tests kz.damulab.QuestionBankIntegrationTest --tests kz.damulab.LectureIntegrationTest --tests kz.damulab.TestingHubIntegrationTest --tests kz.damulab.AnalyticsIntegrationTest --tests kz.damulab.ParentLinkIntegrationTest --tests kz.damulab.PageSecuritySmokeTest` passed.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- Seeded browser smoke passed: `57/57`, `0` failures (desktop + 390 + 360).
  - Report: `.run-logs/stage13-visual-pass/browser-smoke/report.json`

### Remaining open visual gaps (explicit)

- Student quiz room is still one backend-driven template (`quiz-room.html`) rather than three independent pages (`host/guest/question`), by design to keep server-authoritative room state/answer flow.
- Some visual micro-details (exact SVG badge art, exact mockup gradients/shadows in selected cards) are still slightly simplified.
- Parent/public sections are much closer but not strict pixel copy of all mockup decorative blocks.
