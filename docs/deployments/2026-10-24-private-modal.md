# Private-message dialog correction

Code: c122871. Deployed 2026-10-24 21:16:17 Asia/Shanghai.

Corrects the previous inline adaptation of DirectMessageModal. PrivateChat now uses a centered native dialog with the reference backdrop, blur and 896px / 620px window. The background AtomicRoutes path is retained, so its component tree, form drafts and scroll position survive. Closing via X, Escape or backdrop returns through browser history; a direct private-message URL uses /pets as its safe background. Native modal focus handling and body scroll locking prevent background interaction. Existing nested new-contact dialogs and private-message API calls are retained.

51 frontend tests passed; production build passed. New regressions cover native-dialog open/cancel, scroll locking, background DOM/draft preservation, original query restoration and direct-URL close. Browser visual inspection remains unavailable due to the earlier automatic-approval service usage limit; no workaround was used.

Index SHA-256: a6dabc482c3e13ae40b32f7a2fed67da6a59891afa48b1b51b00406cdd0cd09f
Backup: /opt/pms/frontend.bak-privatemodal-20261024-211617.tar.gz
Stage: /opt/pms/frontend-privatemodal-stage-20261024-211617
Frontend only; no backend restart. Archive and served index hashes verified during atomic publishing.
