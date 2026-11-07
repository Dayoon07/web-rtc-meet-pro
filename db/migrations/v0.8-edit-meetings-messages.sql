-- =====================================================================
-- v0.7 → v0.8: 예약 회의 고치기 알림 + 채팅 메시지 고치기 (기존 데이터는 그대로)
--   - notifications.type 에 MEETING_UPDATED 추가
--   - chat_messages.content_edited_at: 보낸 사람이 글을 고친 시각 ("수정됨")
-- 실행 (저장소 루트에서):
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.8-edit-meetings-messages.sql'
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED',
    'CHAT_ROOM_INVITED',
    'MEETING_SCHEDULED', 'MEETING_REMINDER', 'MEETING_STARTED', 'MEETING_CANCELED', 'MEETING_UPDATED'
));
ALTER TABLE chat_messages ADD (content_edited_at TIMESTAMP(6));

PROMPT  알림 종류(MEETING_UPDATED)와 chat_messages.content_edited_at 을 추가했어요.
EXIT
