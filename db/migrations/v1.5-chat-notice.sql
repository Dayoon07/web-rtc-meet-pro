-- v1.5: 채팅방 공지 (방 위쪽에 고정되는 글 하나)
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.5-chat-notice.sql'
ALTER TABLE chat_rooms ADD (
    notice_content  VARCHAR2(1000 CHAR),
    notice_by       NUMBER(19),
    notice_at       TIMESTAMP(6)
);
COMMENT ON COLUMN chat_rooms.notice_content IS '방 공지 (NULL = 없음)';
COMMENT ON COLUMN chat_rooms.notice_by IS '공지를 올린 사람 users.user_id';
COMMENT ON COLUMN chat_rooms.notice_at IS '공지를 올린 시각';
EXIT
