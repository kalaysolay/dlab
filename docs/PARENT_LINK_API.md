# Parent Link API

Status: stage 2 MVP slice.

## Scope

- A parent can create a child profile directly.
- A parent can attach an existing student by a one-time link code.
- A parent can see only linked children.
- Link codes are single-use and expire after 15 minutes.
- QR rendering is implemented server-side as SVG and returned with link-code responses.

## Web Pages

- `GET /parent` - parent dashboard with linked children, create-child form, and attach-by-code form.
- `POST /parent/children` - server-rendered form submit for creating a child.
- `POST /parent/link-codes/attach` - server-rendered form submit for attaching by code.
- `GET /parent/children/{studentId}` - child card visible only to linked parent.
- `POST /parent/children/{studentId}/link-code` - generates a code for a child already linked to the parent.
  The child card displays both the text code and QR SVG.

## REST Endpoints

All parent endpoints require `PARENT`. Student link-code generation requires `STUDENT`.

### List Children

`GET /api/parent/children`

Returns children linked to the current parent.

### Create Child

`POST /api/parent/children`

```json
{
  "fullName": "Child Name",
  "email": "child@example.com",
  "gradeNo": 4,
  "preferredLanguage": "ru"
}
```

`email` is optional for parent-created demo children. If omitted, the backend creates a technical login under `@child.damulab.local`.

### Child Details

`GET /api/parent/children/{studentId}`

Returns `404` when the student is not linked to the current parent.

### Generate Link Code

`POST /api/student/link-codes`

Generates a one-time code for the current student.

`POST /api/parent/children/{studentId}/link-code`

Generates a one-time code for a child already linked to the current parent.

Response:

```json
{
  "code": "ABCD2345",
  "expiresAt": "2026-04-26T15:00:00Z",
  "qrSvg": "<svg ...>...</svg>"
}
```

### Attach By Code

`POST /api/parent/link-codes/{code}/attach`

Creates the parent-child link and consumes the code. A second use returns `409`.

### Unlink

`DELETE /api/parent/children/{studentId}/link`

Removes only the current parent's link. The student account remains.

## Validation and Access Rules

- `fullName` is required.
- `gradeNo` must be `1..5` when provided.
- `preferredLanguage` must be `ru` or `kk`.
- Link code format is uppercase alphanumeric and generated server-side.
- Ownership checks are performed in `ParentLinkService`, not in the UI.

## Test Coverage

- Parent creates a child through REST and server-rendered form.
- Parent sees only linked children.
- Child card can generate a link code with QR.
- Link code response includes QR SVG.
- Used link code cannot be reused.
- Expired link code is rejected.
- Parent dashboard renders create/attach forms.
