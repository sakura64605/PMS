# Cache consistency repair (2026-10-24)

## Reproduced symptom

Using the administrator's own approved pet post, warm the detail endpoint, toggle unlike, then immediately read detail. Before this patch the write returned likeCount=2 but detail returned likeCount=3, even though isLiked=false. The write took 15.2 ms. The original like state/count was restored after the probe.

## Corrections

| Domain | Defect | Result |
| --- | --- | --- |
| Likes | Colon-style eviction did not match underscore-style activity keys; pet detail not evicted | Match real detail/list/fallback keys on both like and unlike |
| Comments | Counter writes had no independent invalidation, self-comments may send no notification | Publish CommentCreated independently; invalidate affected pet/activity/daily snapshots after commit |
| Pet posts | Detail deletion inside transaction; old counters and viewer state returned on hits | Evict after commit; validate cached updateTime and refresh counters, author and viewer flags from current data |
| Activities | Partial list key deletion, missing invalidation on several writes, personalized shared DTOs | Central ActivityCache; invalidate all pages and fallbacks after create/edit/delete/restore/signup/cancel/audit/scheduled changes; read personalized lists/details freshly |
| User profiles | Shared follow state and stale profile/privacies; avatar/follow writes did not invalidate | Read profile/relationship/visibility freshly; invalidate old profile keys after profile/avatar/follow writes commit |
| Daily recommendations | Invalidated a prefix rather than actual page keys; writes left other viewers stale | Invalidate both recommendation key families and topic cache after committed writes, comments and audit; include page size; recheck candidate visibility |
| Notices | All eviction depended on MQ; list/detail held another viewer's read state; cache hits skipped read tracking | Direct after-commit invalidation plus existing MQ path; neutral list cache with copied viewer overlay; expired pages refill; detail validates current DB row and records each viewer |
| Statistics | Realtime card cache hid committed writes; rebuild invalidation before commit | Query realtime counts freshly; historical cache invalidation after commit |
| Cache infrastructure | MQ send fallback was empty; consumer and SCAN swallowed errors | Real direct Redis fallback; propagate consumer/SCAN failures so delivery can retry |

## Boundaries

HTTP routes and DTO contracts are retained, including null interaction flags for anonymous pet viewers. This repair covers application writes; direct SQL edits bypass application events. It does not claim serializable cache reads during every concurrent fill/write race or durable retry for every in-process callback. TTL remains a final fallback; cache outages are logged without turning an already committed write into an apparent rollback.

Shared personalized activity/profile DTO caches are deliberately bypassed; neutral notice lists, pet public detail and daily recommendation caches remain. No global Redis flush, schema migration, server configuration change or frontend rebuild is needed.

## Validation

420 backend tests passed, zero failures/errors/skips. This includes opt-in MySQL suites on local pet_system_test for follow, report, audit, pet review, feed, private messages and notifications, plus DB equivalence tests. PmsApplicationTests (full external-service startup) is excluded; server startup and HTTP probes are checked separately at deployment. Added transaction-commit/rollback, matching key patterns, cache delivery failure, viewer isolation, notice read/expiry and realtime counter regressions.

Build uses the existing target/report-build-settings.xml because the default Maven mirror is unavailable. No credentials are recorded in this document.
