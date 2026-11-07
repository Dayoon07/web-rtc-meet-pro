-- =====================================================================
-- v1.0 → v1.1: 채널 상시 음성방 (디스코드처럼 채널을 누르면 바로 음성·화면 공유)
--   - meetings.chat_room_id: 채널(그룹 채팅방)의 음성방이면 그 채널. NULL = 일반 회의
--     채널마다 진행 중인 음성방은 하나 (모두 나가면 끝나고, 다음에 누가 들어오면 새로 열린다)
-- 실행 (저장소 루트에서):
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.1-voice-rooms.sql'
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE meetings ADD (
    chat_room_id  NUMBER(19),
    CONSTRAINT fk_meetings_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_rooms (room_id)
);
CREATE INDEX idx_meetings_chat_room ON meetings (chat_room_id, status);

COMMIT;
PROMPT  채널 음성방(meetings.chat_room_id)을 추가했어요.
EXIT
