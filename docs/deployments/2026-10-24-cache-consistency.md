# Backend cache consistency release — 2026-10-24

Code commits: eafb167, c87679a.

## Diagnosis and scope

See ../architecture/cache-consistency.md for the domain/key matrix. A production probe on admin's own pet post reproduced likeCount=3 in the cached detail immediately after unlike returned likeCount=2. No notifications were generated for another user, and the original state was restored.

Fixed matching key patterns, missing dependent invalidations, commit timing, shared viewer data, announcement read tracking and cache delivery error handling. Realtime statistics read fresh counters. Existing routes and DTO contracts remain unchanged. No frontend rebuild, schema migration or Redis flush.

## Verification

- 421 backend tests passed: zero failures, errors or skips. Opt-in MySQL suites ran only on local pet_system_test, including existing concurrency tests. Full external-service PmsApplicationTests excluded; actual service startup verified below.
- Production package succeeded with the existing target/report-build-settings.xml.
- Production probes after deployment: pet unlike write/read both returned 2; original count/state restored to 3/liked. Activity 63 write/read both returned 1; original state restored. Announcement list warmed by admin was then read anonymously; all 20 anonymous isRead flags remained null.
- Existing read-only release smoke: 55/55 passed (authenticated/anonymous APIs, frontend routes and resource hashes). Frontend unchanged.
- Service after validation: MainPID=617621, NRestarts=0, active/running.

## Deployment and recovery

Server: 111.229.254.252. Target: /opt/pms/pms.jar. Service: pms.

The first startup exposed the existing double cache registration (stringRedisTemplateProxy and distributedCache). New consumers using a different parameter name made that ambiguity visible. The previous JAR was restored immediately. The facade is now explicitly primary; a regression test loads the real cache configuration with both registrations and the new consumers. The entire suite was rerun successfully before retrying deployment.

Successful release timestamp: 20261024-165752 (Asia/Shanghai).

- Deployed SHA-256: f4efc118549b536fc5a4ea57c98222bd975d9a88e71feee55c90cfdeb41fe3ca
- Backup: /opt/pms/pms.jar.bak-cache-20261024-165752
- Deployment metadata: /opt/pms/cache-deploy-meta.json
- Previous working SHA-256: 0ec7b8d1162d044a075cd4bd9f73bbd995b5f424481d8c1e3a4131b5edf6b18d

The uploaded package was patched with the final cache-binding class to avoid resending the unchanged 347 MB archive. All 557 business class hashes and all 271 dependency archive CRCs/sizes were verified against the final local build. Runtime application YAML files were copied unchanged from the prior deployment; their hashes are recorded in deployment metadata. The deployment script checks the old/new checksums, backs up the working JAR, replaces it atomically and checks database-backed HTTP readiness. No Nginx or frontend configuration was changed.

The initial failed-attempt backup remains /opt/pms/pms.jar.bak-cache-20261024-164826. No business data was deleted during deployment.
