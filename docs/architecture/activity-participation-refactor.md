# Activity participation migration

The existing signup, cancellation and check-in HTTP endpoints now use ActivityParticipationCommands. Original service, DTOs, paths and database tables are retained. Publishing, editing, queries and recycle-bin operations still use the legacy activity service.

All participation writes lock the activity row before reading its signup row and keep the lock through commit. Seat reservations include capacity, approval, deletion, status and start-time predicates. Cancellation deletes the original signup row and decrements the count atomically; keeping this legacy contract avoids changing absence statistics and permits rejoining. Check-in remains organizer-operated and allows early check-in as before. Duplicate or invalid state transitions are rejected. Disabled accounts and active signup bans cannot join.

Immutable events deliver notifications after commit using independent transactions. Each recipient is isolated from another notification failure. ActivityCache eviction remains after commit. Legacy activity schedulers now update only status and update time, preserving concurrently changed participant counters and content.

Validation: 14 real-MySQL cases in ActivityParticipationMySqlTest cover capacity races, duplicate signup/cancellation/check-in, rollback, inconsistent counters, activity/account states, ownership, controller routing, commit-duration locking, notification failure and fallback database commits. Fixtures are isolated in local pet_system_test. The full regression suite enables all existing opt-in database tests and excludes PmsApplicationTests, which requires the complete external environment.

Next: migrate publishing/editing and query assembly; review task state-transition conditions, absence-statistics idempotency and XXL-JOB. This batch does not integrate XXL-JOB or durable notification replay.
