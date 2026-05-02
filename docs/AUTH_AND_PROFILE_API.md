# Auth and Profile API

Status: stage 1 MVP foundation.

## Scope

- Email is used as login.
- Email confirmation is not implemented in MVP.
- Password reset through email is not implemented in MVP.
- Authentication is session-based Spring Security, matching server-rendered Thymeleaf MVP.

## Demo Users

- `admin@damulab.kz` / `password`
- `student@damulab.kz` / `password`
- `parent@damulab.kz` / `password`

Demo users are created by `DemoUserSeeder` and are not a production bootstrap strategy.

## Endpoints

### Register

`POST /api/auth/register`

Allowed roles for self-registration:

- `STUDENT`
- `PARENT`

`ADMIN` self-registration is rejected.

Student payload:

```json
{
  "email": "student@example.com",
  "password": "password123",
  "fullName": "Student Name",
  "phone": "+77000000001",
  "role": "STUDENT",
  "gradeNo": 4,
  "preferredLanguage": "ru"
}
```

Parent payload:

```json
{
  "email": "parent@example.com",
  "password": "password123",
  "fullName": "Parent Name",
  "phone": "+77000000002",
  "role": "PARENT"
}
```

### Login

`POST /api/auth/login`

```json
{
  "email": "student@damulab.kz",
  "password": "password"
}
```

Creates a server session.

### Current User

`GET /api/me`

Requires authentication.

### Student Profile

`GET /api/student/profile`

Requires `STUDENT`.

`PATCH /api/student/profile`

Requires `STUDENT` and CSRF when called in a browser session.

```json
{
  "fullName": "Updated Student",
  "phone": "+77000000001",
  "gradeNo": 5,
  "preferredLanguage": "kk"
}
```

### Parent Profile

`GET /api/parent/profile`

Requires `PARENT`.

`PATCH /api/parent/profile`

Requires `PARENT` and CSRF when called in a browser session.

```json
{
  "fullName": "Updated Parent",
  "phone": "+77000000002"
}
```

## Web Pages

- `GET /login`
- `GET /register`
- `GET /student/profile`
- `POST /student/profile`
- `GET /parent/profile`
- `POST /parent/profile`

Forms use server-side validation and Thymeleaf error rendering.
