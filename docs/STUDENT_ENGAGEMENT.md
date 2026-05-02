# Student engagement: stage 7

Status: stage 7 implementation and browser/mobile QA completed on 2026-04-27.

## Scope

Stage 7 adds a basic student engagement layer on top of the existing `content -> test -> result -> analytics -> parent` MVP cycle.

Implemented:

- `Streak` updated after the first successful `POST /api/test-sessions/{id}/finish`.
- `Achievement` and `StudentAchievement` with seeded rules:
  - `first-test-finished`
  - `three-tests-finished`
  - `five-day-streak`
- Student dashboard model for `/student`:
  - daily mission;
  - current and longest streak;
  - last finished activity;
  - progress widget;
  - achievement gallery.
- Student profile notification settings:
  - `lessonRemindersEnabled`;
  - `weeklyParentReportEnabled`;
  - `sessionResultPushEnabled`.
- Read-only student dashboard API:
  - `GET /api/student/dashboard`.
- Header language selector persists `StudentProfile.preferredLanguage` for authenticated students.

Not implemented in this slice:

- energy/fire economy;
- streak freezes;
- advanced spaced repetition;
- quiz/Arena achievements;
- push delivery based on notification preferences.

## Data Model

Migration: `V9__student_engagement.sql`.

Tables:

- `streaks`
- `achievements`
- `student_achievements`

New columns on `student_profiles`:

- `lesson_reminders_enabled`
- `weekly_parent_report_enabled`
- `session_result_push_enabled`

## Business Rules

- A useful activity is currently a newly finished test session.
- Repeated `finish` for the same test session is idempotent and does not update streak or achievements again.
- Multiple useful activities on the same UTC day keep the streak at the same day count.
- Consecutive UTC-day activity increments the streak.
- A missed day resets the current streak to `1`.
- Achievements are awarded once per student via the unique `(student_profile_id, achievement_id)` constraint.

## Integration Points

- `TestingHubService.finishSession(...)` saves `TestResult`, updates analytics, then records useful activity in `StudentEngagementService`.
- `PageController.student(...)` renders `/student` with `StudentDashboardView`.
- `ProfileService.updateStudentProfile(...)` persists notification settings together with grade and language.
- `ProfilePageController.updateStudentLanguage(...)` persists header language changes and redirects back to the student area with the matching `lang` parameter.

## API Surface

### `GET /api/student/dashboard`

Returns the current authenticated student's dashboard state:

```json
{
  "studentId": 1,
  "fullName": "Demo Student",
  "preferredLanguage": "ru",
  "mission": {
    "title": "Первый предметный срез",
    "description": "Начните с короткого теста, чтобы Damulab построил стартовую карту знаний.",
    "actionText": "Начать тест",
    "actionUrl": "/student/tests"
  },
  "streak": {
    "currentCount": 0,
    "longestCount": 0,
    "lastActivityDate": null
  },
  "lastActivity": null,
  "progress": {
    "completedTests": 0,
    "averagePercent": 0,
    "overallMastery": 0,
    "earnedAchievements": 0
  },
  "achievements": []
}
```

### Profile API

Existing profile API now includes the notification flags:

```json
{
  "userId": 1,
  "email": "student@damulab.kz",
  "fullName": "Demo Student",
  "phone": null,
  "gradeNo": 4,
  "preferredLanguage": "ru",
  "lessonRemindersEnabled": true,
  "weeklyParentReportEnabled": true,
  "sessionResultPushEnabled": false
}
```

`PATCH /api/student/profile` accepts the same three boolean fields. If omitted, the current stored value is preserved.

## Verification

Focused checks:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-run"; .\gradlew.bat --no-daemon --console plain test --tests kz.damulab.StudentEngagementIntegrationTest --tests kz.damulab.ProfileIntegrationTest
```

Full checks:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-run"; .\gradlew.bat --no-daemon --console plain test
$env:GRADLE_USER_HOME="$PWD\.gradle-run"; .\gradlew.bat --no-daemon --console plain build
```

Browser QA:

- Local app started on `http://localhost:18081` with `test` profile.
- Chrome/Playwright fallback was used because in-app browser runtime requires Node `>=22.22.0`, while the configured local Node is `22.21.0`.
- Checked `/student` desktop and mobile `390x844`: no horizontal overflow.
- Checked `/student/profile` mobile `390x844`: no horizontal overflow, 3 notification toggles visible.
- Header language selector persisted `preferredLanguage=kk`; `GET /api/student/profile` returned `preferredLanguage: "kk"`.
- Screenshots:
  - `build/stage7-student-dashboard-desktop.png`
  - `build/stage7-student-dashboard-mobile.png`
  - `build/stage7-student-profile-mobile.png`
