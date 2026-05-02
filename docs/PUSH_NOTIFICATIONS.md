# Push Notifications

Status: Stage 10 closed for the current MVP slice.
Date: 2026-04-28.

This document fixes the implemented MVP push boundary. The current implementation supports manual scheduled push creation from admin, DB-backed outbox processing, a stub provider and browser/mobile QA for the admin workflow. It does not integrate a production FCM/Web Push/APNs provider yet.

## Implemented Scope

- `PushNotification`, `PushDeliveryLog` and `DeviceToken` persistence.
- REST API:
  - `POST /api/admin/push-notifications`
  - `GET /api/admin/push-notifications`
  - `PATCH /api/admin/push-notifications/{id}`
  - `POST /api/admin/push-notifications/{id}/cancel`
- Admin UI at `/admin/push-notifications`.
- Status lifecycle: `scheduled -> sent | cancelled | failed`.
- Server-side validation:
  - text is required and limited to `1..120` characters;
  - `scheduled_at` uses `YYYY-MM-DD HH:mm`;
  - input time is interpreted in `damulab.ui.server-time-zone`;
  - past scheduling is rejected;
  - `subject_test` requires `subject_id`;
  - only `scheduled` notifications can be edited or cancelled.
- `PushProvider` interface with `StubPushProvider`.
- Scheduler worker processes due scheduled notifications and writes delivery logs.
- UI features: live character counter, server time label, mobile preview, status filter and edit/cancel controls.
- Browser/mobile QA covered desktop width, 390px and 360px mobile widths. The admin page has no page-level horizontal overflow; the form, preview, status table and inline edit state are readable on mobile.

## DTO Boundary

Request body:

```json
{
  "text": "Через 15 минут стартует викторина",
  "scheduled_at": "2026-04-23 19:30",
  "target_screen": "quiz_create_room",
  "target_payload": {}
}
```

Supported `target_screen` values:

- `quiz_create_room`
- `subject_test`

For `subject_test`, `target_payload.subject_id` is required.

## Time Handling

The database stores `scheduled_at_utc`.

The admin UI and API input interpret `scheduled_at` as server-local time from:

```yaml
damulab:
  ui:
    server-time-zone: Asia/Almaty
```

The UI displays the current server offset label, for example `Серверное время (UTC+05:00)`.

## Provider Boundary

`StubPushProvider` does not make network calls. It marks due notifications as sent and writes a delivery log. The token model exists for future production integration, but token registration and real delivery are outside this slice.

## Remaining Non-Goals

- No production FCM/Web Push/APNs provider.
- No real student device-token registration flow.
- No audience segmentation beyond the target screen payload.
- No retry/backoff queue for failed production sends.
- No browser/mobile QA for real PWA push permissions.
