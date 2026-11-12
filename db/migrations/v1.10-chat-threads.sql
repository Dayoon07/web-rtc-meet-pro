-- v1.10: 채팅 스레드 (슬랙·매터모스트처럼 메시지 아래에 답글 대화를 이어 간다).
--   답글은 thread_root_id 로 원래 메시지(스레드 시작 글)를 가리키고 채널 본문에는 보이지 않는다.
--   시작 글에는 답글 수·마지막 답글 시각·답글 단 사람(최근 순, 쉼표)을 모아 둔다. 스레드 답글 알림(THREAD_REPLY)을 더한다
--   NLS_LANG=KOREAN_KOREA.AL32UTF8 sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.10-chat-threads.sql'
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

ALTER TABLE chat_messages ADD (
    thread_root_id      NUMBER(19),
    reply_count         NUMBER(10)      DEFAULT 0 NOT NULL,
    last_reply_at       TIMESTAMP(6),
    thread_user_ids     VARCHAR2(200)
);
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_thread FOREIGN KEY (thread_root_id) REFERENCES chat_messages (message_id);
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_reply_count CHECK (reply_count >= 0);
CREATE INDEX idx_chat_messages_thread ON chat_messages (thread_root_id, message_id);
COMMENT ON COLUMN chat_messages.thread_root_id IS '스레드 답글이면 스레드 시작 글 (채널 본문에는 안 보인다)';
COMMENT ON COLUMN chat_messages.reply_count IS '스레드 시작 글: 지우지 않은 답글 수';
COMMENT ON COLUMN chat_messages.last_reply_at IS '스레드 시작 글: 마지막 답글 시각';
COMMENT ON COLUMN chat_messages.thread_user_ids IS '스레드 시작 글: 답글 단 사람 user_id (최근 순, 쉼표, 최대 5명)';

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED',
    'CHAT_ROOM_INVITED', 'CHAT_MENTION', 'THREAD_REPLY',
    'MEETING_SCHEDULED', 'MEETING_REMINDER', 'MEETING_STARTED', 'MEETING_CANCELED', 'MEETING_UPDATED',
    'MEETING_NOTES_READY',
    'PLAN_EXPIRING', 'PLAN_EXPIRED', 'PAYMENT_FAILED', 'PAYMENT_REFUNDED',
    'MEMBER_OVERAGE', 'OVERAGE_CHARGE_SOON'
));

COMMIT;
PROMPT  스레드 칸과 스레드 답글 알림 종류를 추가했어요.
EXIT
