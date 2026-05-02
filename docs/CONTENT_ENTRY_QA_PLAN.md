# Content Entry QA Plan

Date: 2026-04-30.

Purpose: manual smoke plan for the next admin pass: create topics, create questions, publish usable content, then create lectures that reference the created topic/questions.

## Scope

This plan covers the current MVP content-entry path:

- Admin topic creation and topic tree visibility.
- Manual question creation through `/admin/questions/new`.
- Question moderation: draft / needs_review -> approve -> publish.
- Lecture creation and publication for the same topic.
- Student visibility of published questions through tests and published lectures through the lecture reader.

It intentionally does not block on post-MVP gaps such as binary file upload, full LMS checkpoint attempts, rich editor assets, or production push provider delivery.

## Question Form Decisions

- `/admin/questions/new` should open without requiring `subjectId` or `gradeId` query params.
- Subject, grade and topic are selected inside the create form.
- The topic selector refreshes after subject/grade changes through `/api/content/references`.
- SCQ/MCQ variants are dynamic.
- Removing a variant uses a row-level trash button with confirmation.
- Removed variants are posted as `softDeleted` and ignored by backend validation and answer-key generation.
- The form must keep at least two active SCQ/MCQ variants.

## Manual Smoke Steps

1. Log in as admin.
2. Open `/admin/topics`.
3. Create or confirm a subject/grade/topic path, for example: Math -> Grade 4 -> Percent.
4. Open `/admin/questions/new` directly, without query params.
5. Select subject, grade and the topic created in step 3.
6. Create an SCQ question:
   - fill RU/KK text and source;
   - add at least four variants;
   - delete a middle variant using the trash button;
   - confirm that labels reindex and at least two active variants remain;
   - save as `needs_review`.
7. Approve and publish the question from `/admin/questions`.
8. Create an MCQ question and verify that at least one active correct option is required.
9. Create a lecture on `/admin/lectures/new` for the same topic.
10. Publish the lecture with RU/KK title/content.
11. If checkpoint mode is used, verify that only published questions from the same topic are selectable or auto-picked.
12. Open student pages and confirm that published content is visible while answer keys are not exposed before test/result flows allow it.

## Expected Pass Criteria

- No `500` on `/admin/questions/new`, including old-style URLs like `/admin/questions/new?subjectId=2&gradeId=4`.
- Adding a question does not depend on carrying subject/grade through the list-page link.
- Soft-deleted choice options do not appear in stored `options_json` or `answer_key_json`.
- Published question/lecture edits preserve the already published version used by student flows.
- Topic deletion remains blocked when questions or lectures reference the topic.

## Open Gaps For This Flow

- Existing question edit UI is still not a full parity editor.
- Matching and fill-in forms remain MVP-simple; richer authoring can be improved after the basic content-entry pass.
- Lecture editor still uses safe textarea fallback; local rich editor/math assets are a separate task.
- Binary lecture attachments are URL metadata only until object storage/upload is implemented.
- Full LMS checkpoint attempt flow is still post-MVP.
- Browser QA should be rerun after the manual content-entry pass on desktop and mobile widths.

## 2026-04-30 Execution Result

- Status: PASS (`http://localhost:18090`, profile `test`).
- Run marker: `57197986`.
- Completed checks:
  - created topic in admin (`topicId=13`, subject `2`, grade `1`);
  - opened `/admin/questions/new` without query params;
  - created SCQ, deleted specific row via trash+confirm, verified min two active options, published question (`questionId=17`);
  - validated MCQ rule "at least one correct option";
  - created and published lecture for same topic (`lectureId=5`);
  - student sees published lecture; answer keys are not exposed before finish/result and are visible on result page after finish.
- Artifacts:
  - `.run-logs/manual-content-smoke/content-entry-smoke-report.json`
  - `.run-logs/manual-content-smoke/*.png`
- Remaining gap for deeper audit:
  - stored `options_json`/`answer_key_json` were validated indirectly via behavior and API/UI flow; there is no read-only admin surface yet to inspect raw version payloads directly from UI.

## 2026-04-30 Lecture Rich-Editor Smoke

- Command:
  - `npm run smoke:lectures` with `QA_BASE_URL=http://localhost:18091`
- Scope:
  - admin lecture create in rich editor (plain text + inline formula + block formula),
  - reopen lecture in edit page and verify formula/content persistence,
  - admin publish,
  - student lecture visibility.
- Status:
  - PASS.
- Artifacts:
  - `.run-logs/lecture-rich-smoke/lecture-rich-editor-smoke-report.json`
  - `.run-logs/lecture-rich-smoke/01-admin-editor-filled.png`
  - `.run-logs/lecture-rich-smoke/02-admin-editor-reopened.png`
  - `.run-logs/lecture-rich-smoke/03-admin-lecture-published.png`
  - `.run-logs/lecture-rich-smoke/04-student-lecture-visible.png`
