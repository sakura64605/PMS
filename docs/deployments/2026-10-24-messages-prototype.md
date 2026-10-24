# Private chat and message center prototype release

Code commit: 4b2f0eb. Deployed 2026-10-24 20:48:23 Asia/Shanghai.

Reference: supplied MessageView.tsx and DirectMessageModal in Modals.tsx.

- Notifications: reference header, horizontal category pills, 36px icon containers/16px colored icons, compact divided rows, unread dots, related-content links, and matching empty state. Retains all real backend categories. Rows mark messages read and use actual legacy link/businessId contracts; all-read targets the global inbox.
- Private chat: 896px/620px rounded two-column window, warm conversation sidebar, compact 36px contact avatars and timestamps, 28px message avatars, sage outgoing bubbles, warm incoming bubbles with embedded timestamps, single-line Enter composer, mobile list/back control. Existing search/new-conversation, pagination, unread, clear/delete confirmations are retained; extra actions are in the header menu.
- Sending has a duplicate-submit guard, preserves text typed during a pending send, and avoids clearing another selected conversation. Refreshing the conversation list no longer reselects a stale URL conversation after a manual selection.
- Route image deferral, atomic navigation and existing API endpoints remain in use. No backend or Nginx changes.

Validation: 48 frontend tests passed; production build passed. Deployment verified archive/index checksums and 8 published assets. No real private messages were sent and no chats were deleted for testing. Browser visual inspection could not acquire the local preview: automatic approval failed due to the approval service usage limit. No alternative browser mechanism was used to bypass that denial; desktop/mobile visual acceptance remains unverified.

Index SHA-256: 4da5ca7088d501a4c75971038018f6898dea773da1765e8d4edd4635d8164506
Backup: /opt/pms/frontend.bak-messagesui-20261024-204823.tar.gz
Stage: /opt/pms/frontend-messagesui-stage-20261024-204823
