# Deferred route image loading release

Code commit: 9c15bbb. Deployed 2026-10-24 17:16:55 Asia/Shanghai.

Route preparation no longer attaches image src/srcSet while the target page is hidden. Images start after the new page is visible and has had a paint opportunity, defaulting to lazy loading and asynchronous decoding. Covers, avatars, galleries, previews and avatar-history images use RouteImage. Existing route readiness still waits for page data/code; this does not reduce HTTP API TTFB.

Production build passed. Tests were not run, as requested by the user. Frontend archive and served index hashes verified during publishing; no browser acceptance or business smoke suite was run.

- Target: /var/www/html/pms on 111.229.254.252
- Index SHA-256: 5a7cd010d05f55d6e05f5f35ae6b6e56d49f190589bce1282279d32540da8174
- Backup: /opt/pms/frontend.bak-deferredimages-20261024-171655.tar.gz
- Staging: /opt/pms/frontend-deferredimages-stage-20261024-171655

Hashed assets were copied first and old assets retained; index.html was replaced atomically. Backend and Nginx configuration were not changed; no service restart.
