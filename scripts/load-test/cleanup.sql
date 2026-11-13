-- 부하 테스트 데이터 지우기 (2026-11-14 테스트: 회원 1107~3106 = load####@mq-load.test, 팀 1053~1252, 채팅방 1107~1306, 회의 1141~1150).
-- 다시 테스트하면 ID 범위가 달라지니 먼저 범위를 확인하고 바꿔서 쓴다. 하나라도 실패하면 전부 되돌린다
--   sqlplus -S limeet/limeet@//localhost:1521/XEPDB1 @scripts/load-test/cleanup.sql
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET FEEDBACK ON

-- 범위가 테스트 계정만인지 다시 확인 (아니면 멈춘다)
DECLARE n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM users WHERE user_id BETWEEN 1107 AND 3106 AND email NOT LIKE 'load%@mq-load.test';
  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20001, '테스트 계정이 아닌 회원이 범위에 있다'); END IF;
  SELECT COUNT(*) INTO n FROM teams WHERE team_id BETWEEN 1053 AND 1252 AND owner_id NOT BETWEEN 1107 AND 3106;
  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20002, '테스트 팀이 아닌 팀이 범위에 있다'); END IF;
END;
/

DELETE FROM notifications WHERE user_id BETWEEN 1107 AND 3106;
DELETE FROM meeting_events WHERE meeting_id BETWEEN 1141 AND 1150;
DELETE FROM meeting_invitees WHERE meeting_id BETWEEN 1141 AND 1150;
DELETE FROM meeting_participants WHERE meeting_id BETWEEN 1141 AND 1150;
DELETE FROM meetings WHERE meeting_id BETWEEN 1141 AND 1150 AND team_id BETWEEN 1053 AND 1252;
DELETE FROM chat_room_members WHERE room_id BETWEEN 1107 AND 1306;
DELETE FROM chat_messages WHERE room_id BETWEEN 1107 AND 1306;
DELETE FROM chat_rooms WHERE room_id BETWEEN 1107 AND 1306 AND team_id BETWEEN 1053 AND 1252;
DELETE FROM team_invite_links WHERE team_id BETWEEN 1053 AND 1252;
DELETE FROM team_members WHERE team_id BETWEEN 1053 AND 1252;
DELETE FROM subscriptions WHERE user_id BETWEEN 1107 AND 3106;
DELETE FROM teams WHERE team_id BETWEEN 1053 AND 1252;
DELETE FROM refresh_tokens WHERE user_id BETWEEN 1107 AND 3106;
DELETE FROM login_histories WHERE user_id BETWEEN 1107 AND 3106 OR (user_id IS NULL AND email_attempted LIKE 'load%@mq-load.test');
DELETE FROM users WHERE user_id BETWEEN 1107 AND 3106 AND email LIKE 'load%@mq-load.test';
-- 테스트 중 일부러 낸 과부하 오류 기록 (테스트 시간대, 테스트 계정이거나 로그인 전 요청)
DELETE FROM admin_error_logs
 WHERE created_dt BETWEEN TIMESTAMP '2026-11-14 01:00:00' AND TIMESTAMP '2026-11-14 03:30:00'
   AND (user_id BETWEEN 1107 AND 3106 OR user_id IS NULL);
COMMIT;
SELECT COUNT(*) AS load_users_left FROM users WHERE email LIKE 'load%@mq-load.test';
EXIT
