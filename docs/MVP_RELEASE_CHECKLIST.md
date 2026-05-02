# MVP Release Checklist

Дата: 2026-04-29.

## Build And Tests

- [x] Run targeted smoke/integration tests:
  - `.\gradlew.bat test --tests kz.damulab.QuestionBankIntegrationTest --tests kz.damulab.LectureIntegrationTest --tests kz.damulab.TestingHubIntegrationTest --tests kz.damulab.AnalyticsIntegrationTest --tests kz.damulab.ParentLinkIntegrationTest --tests kz.damulab.PageSecuritySmokeTest`
- [x] Run full suite: `.\gradlew.bat test`
- [x] Run production artifact build: `.\gradlew.bat build`
- [ ] Confirm no committed secrets in `application.yml`, `.env`, docs or logs.

## Database And Migrations

- [ ] Apply Flyway migrations through latest version in `src/main/resources/db/migration`.
- [ ] Confirm PostgreSQL connection values are provided by environment in production.
- [ ] Confirm seed/reference data for subjects, grades and topics is present.
- [ ] Confirm demo users are disabled or intentionally configured for target environment.

## Required Environment

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- [ ] `AI_PROVIDER=stub` unless real provider use is explicitly approved.
- [ ] `AI_REAL_PROVIDERS_ENABLED=false` unless external AI usage, contracts and data policy are approved.
- [ ] If real AI is enabled, provide `OPENAI_API_KEY` and/or `DEEPSEEK_API_KEY` only through environment or secret manager.
- [ ] Configure production timezone policy for scheduled push (`damulab.ui.server-time-zone` / env equivalent).
- [ ] Configure public base URL, HTTPS and reverse proxy headers.

## Seed Users / Access

- [x] Admin account exists and can log in.
- [x] Student account exists with profile, grade and language.
- [x] Parent account exists.
- [x] Parent-child link is created through code/QR or seeded link.
- [x] Admin, student and parent sessions are tested independently.

## Smoke Routes

Public:
- [x] `/`
- [x] `/login`

Admin:
- [x] `/admin`
- [x] `/admin/topics`
- [x] `/admin/questions`
- [x] `/admin/questions/health`
- [x] `/admin/questions/import`
- [x] `/admin/questions/ai-generate`
- [x] `/admin/lectures`
- [x] `/admin/push-notifications`

Student:
- [x] `/student`
- [x] `/student/tests`
- [x] start test session and open `/student/test-sessions/{id}`
- [x] finish test and open `/student/test-sessions/{id}/result`
- [x] `/student/analytics`
- [x] `/student/quiz`
- [x] `/student/lectures`

Parent:
- [x] `/parent`
- [x] `/parent/children/{studentId}`

## MVP Flow Gates

- [x] Content can be created, approved and published.
- [x] Published question edit does not remove the existing published version from student tests.
- [x] Published lecture edit does not remove the existing published lecture from student pages.
- [x] Manual content-entry smoke from `docs/CONTENT_ENTRY_QA_PLAN.md`: create topic -> create/publish SCQ or MCQ question -> create/publish lecture for the same topic.
- [x] Lecture rich-editor smoke: admin create/edit/publish with plain text + inline/block formulas, reopen without content loss, student visibility.
- [x] `/admin/questions/new` opens without subject/grade query params; subject, grade and topic are selected inside the form.
- [x] SCQ/MCQ option deletion is row-level with confirmation and backend soft-delete handling.
- [x] Active test session responses do not expose correct answers or `answer_key_json`.
- [x] Correct answers are visible only after test finish/result.
- [x] Analytics updates after completed test.
- [x] Parent sees only linked children and linked child progress.
- [x] Admin/content actions preserve published/history behavior.

## Browser / Mobile QA

- [x] Desktop route smoke for public/admin/student/parent MVP routes.
- [x] Mobile width `390px` smoke for key MVP routes.
- [x] Mobile width `360px` smoke for key MVP routes.
- [x] Confirm no page-level horizontal overflow on admin question health/import, student test session/result, analytics, parent dashboard/child card.
- [x] Confirm modals/alerts/buttons/tables/cards remain usable on mobile.
- [x] Confirm lecture list/editor layout is stable on desktop/tablet/mobile after rich-editor + attachments UX updates.

## Known Non-Blocking Gaps

- Production push provider, real device-token registration and real device delivery QA.
- Production object storage for lecture attachments and import raw files.
- Materialized analytics/content-health aggregates for large data volumes.
- Full LMS checkpoint attempt flow.
- Advanced AI/Arena/gamification scope beyond current MVP.
- Final product decision for multiple parents per child.

## 2026-04-30 Verification Notes

- Targeted tests passed:
  - `.\gradlew.bat test --tests kz.damulab.QuestionBankIntegrationTest --tests kz.damulab.LectureIntegrationTest --tests kz.damulab.TestingHubIntegrationTest --tests kz.damulab.AnalyticsIntegrationTest --tests kz.damulab.ParentLinkIntegrationTest --tests kz.damulab.PageSecuritySmokeTest`
- Full suite passed:
  - `.\gradlew.bat test`
- Build passed:
  - `.\gradlew.bat build`
- Seeded browser smoke passed:
  - `57/57`, `0` failures on desktop + 390 + 360.
  - Artifacts: `.run-logs/stage13-visual-pass/browser-smoke/report.json`
- Manual content-entry smoke passed on `http://localhost:18090`:
  - topic created: `topicId=13` (subject `2`, grade `1`);
  - SCQ created with row-level delete+confirm+soft-delete and published (`questionId=17`);
  - MCQ validation (`at least one correct`) confirmed;
  - lecture created and published (`lectureId=5`) on same topic;
  - student lecture visibility confirmed; answer keys unavailable pre-finish and available on result page.
  - Artifacts: `.run-logs/manual-content-smoke/content-entry-smoke-report.json` + screenshots in `.run-logs/manual-content-smoke/`.
- Lecture rich-editor smoke passed on `http://localhost:18091`:
  - `npm run smoke:lectures` with plain text + inline/block formulas, reopen/edit persistence check, publish and student visibility.
  - Artifacts: `.run-logs/lecture-rich-smoke/lecture-rich-editor-smoke-report.json` + screenshots in `.run-logs/lecture-rich-smoke/`.
