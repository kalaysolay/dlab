# Quiz Arena API and MVP slice

Status: Stage 11 live slice, 28 April 2026.

## Scope

This slice adds short quiz rooms for students without a separate SPA and without exposing answer keys before the room is finished.

Implemented:

- `QuizRoom`, `QuizParticipant`, `QuizRound`, `QuizAnswer`, `QuizResult`.
- Room code generation with short `QZ....` codes.
- Up to 4 players per room.
- Host auto-joins and is ready by default.
- Join, ready, start, answer, and results lifecycle.
- Published `QuestionVersion` selection through the existing question bank query.
- Server-side answer checking through `AnswerChecker`.
- Thymeleaf pages:
  - `/student/quiz`
  - `/student/quiz/rooms/{code}`
  - `/student/quiz/rooms/{code}/results`
- WebSocket/STOMP live event channel for lobby, ready, progress, timeout, and results transitions.
- Server-side round windows derived from `startedAt`, `roundSeconds`, and round order.
- Server-side timeout enforcement that creates zero-score missed answers after the round deadline.

Not implemented in this slice:

- Matchmaking, leagues, ranking economy, or Arena production hardening.
- Parent visibility for quiz results.

## REST contracts

All endpoints require `ROLE_STUDENT`.

### Create room

`POST /api/quiz/rooms`

Request:

```json
{
  "subjectId": 1,
  "gradeId": 4,
  "language": "ru",
  "difficulty": 2,
  "questionCount": 4,
  "roundSeconds": 20,
  "maxPlayers": 4
}
```

Response: `201 Created`, `QuizRoomResponse`.

### Room state

`GET /api/quiz/rooms/{code}`

Returns room metadata, participants, and public round data for a room participant. It does not return `answer_key_json` or correct answers.

### Join

`POST /api/quiz/rooms/{code}/join`

Adds the current student while the room is `waiting`.

### Ready

`POST /api/quiz/rooms/{code}/ready`

Marks the current participant as ready.

### Start

`POST /api/quiz/rooms/{code}/start`

Host-only. Starts the room after every participant is ready.

### Submit answer

`POST /api/quiz/rooms/{code}/answers`

Request:

```json
{
  "roundId": 10,
  "answer": {
    "selected": ["B"]
  }
}
```

The server checks the answer immediately and stores only the submitted payload plus evaluation metadata.

### Results

`GET /api/quiz/rooms/{code}/results`

Returns participant scores only after all participants answered all rounds and the room became `finished`.

## DTO boundary

Public round DTO includes:

- round id
- order number
- server-derived `startsAt` and `endsAt`
- whether the round has timed out
- question type
- localized body
- topic/skill titles
- difficulty and points
- public choices, matching sides, or fill placeholders
- whether the current participant already answered

Public round DTO excludes:

- `answer_key_json`
- correct labels/values before finish
- raw internal question payload fields

Room DTO also includes:

- `serverTime`
- `activeRoundId`

`activeRoundId` is null before start, after finish, or when no round is currently open.

## WebSocket/STOMP live contract

Endpoint:

- `/ws/quiz`

Subscription destination per room:

- `/topic/quiz.rooms.{code}`

Events:

- `lobby.updated`
- `ready.updated`
- `room.started`
- `answer.progress`
- `round.timeout`
- `room.finished`

Payload:

```json
{
  "type": "answer.progress",
  "code": "QZ7K2M",
  "status": "active",
  "occurredAt": "2026-04-28T10:00:05Z"
}
```

The event payload intentionally does not include question answers, answer keys, or participant-private flags. The browser rereads `GET /api/quiz/rooms/{code}` after an event and receives only the current student's authorized room DTO.

## Timeout and late-answer rules

- Round `startsAt` is `room.startedAt + (round.orderNo - 1) * room.roundSeconds`.
- Round `endsAt` is `startsAt + room.roundSeconds`.
- An answer is accepted only when `serverTime >= startsAt` and `serverTime < endsAt`.
- A future round returns `round_not_open`.
- A late answer returns `late_answer`.
- When a round deadline passes, the server creates a zero-score `{}` answer for each participant who did not answer.
- A room finishes when every participant has either submitted or timed out on every round.
- Results are available only after the room status becomes `finished`.

## Validation and security notes

- `questionCount`: 1-12.
- `roundSeconds`: 5-120.
- `maxPlayers`: 2-4.
- Create/join/ready/start/answer require authenticated student.
- Room state and results require the current student to be a participant.
- Parent and admin role access to `/api/quiz/**` is blocked by security config.
- Answer checking stays server-side through `AnswerChecker`.
