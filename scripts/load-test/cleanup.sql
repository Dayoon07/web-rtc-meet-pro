-- 부하 테스트 데이터 지우기: 테스트 계정(load####@mq-load.test)과 그 계정들이 만든 팀·채팅방·회의·메시지·알림·결제 구독.
-- 범위는 이메일로 찾는다 (다른 회원 데이터는 건드리지 않는다). 하나라도 실패하면 전부 되돌린다
--   sqlplus -S limeet/limeet@//localhost:1521/XEPDB1 @scripts/load-test/cleanup.sql
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET FEEDBACK ON

CREATE GLOBAL TEMPORARY TABLE load_ids (kind VARCHAR2(10), id NUMBER(19)) ON COMMIT PRESERVE ROWS;
INSERT INTO load_ids SELECT 'user', user_id FROM users WHERE email LIKE 'load%@mq-load.test';
INSERT INTO load_ids SELECT 'team', team_id FROM teams WHERE owner_id IN (SELECT id FROM load_ids WHERE kind = 'user');
INSERT INTO load_ids SELECT 'room', room_id FROM chat_rooms WHERE team_id IN (SELECT id FROM load_ids WHERE kind = 'team');
INSERT INTO load_ids SELECT 'meeting', meeting_id FROM meetings WHERE team_id IN (SELECT id FROM load_ids WHERE kind = 'team');

-- 테스트 팀에 테스트 계정이 아닌 사람이 있으면 멈춘다
DECLARE n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM team_members
   WHERE team_id IN (SELECT id FROM load_ids WHERE kind = 'team') AND user_id NOT IN (SELECT id FROM load_ids WHERE kind = 'user');
  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20001, '테스트 팀에 다른 회원이 있다'); END IF;
END;
/

DELETE FROM notifications WHERE user_id IN (SELECT id FROM load_ids WHERE kind = 'user');
DELETE FROM meeting_events WHERE meeting_id IN (SELECT id FROM load_ids WHERE kind = 'meeting');
DELETE FROM meeting_invitees WHERE meeting_id IN (SELECT id FROM load_ids WHERE kind = 'meeting');
DELETE FROM meeting_participants WHERE meeting_id IN (SELECT id FROM load_ids WHERE kind = 'meeting');
DELETE FROM meetings WHERE meeting_id IN (SELECT id FROM load_ids WHERE kind = 'meeting');
DELETE FROM chat_room_members WHERE room_id IN (SELECT id FROM load_ids WHERE kind = 'room');
DELETE FROM chat_messages WHERE room_id IN (SELECT id FROM load_ids WHERE kind = 'room');
DELETE FROM chat_rooms WHERE room_id IN (SELECT id FROM load_ids WHERE kind = 'room');
DELETE FROM team_invite_links WHERE team_id IN (SELECT id FROM load_ids WHERE kind = 'team');
DELETE FROM team_members WHERE team_id IN (SELECT id FROM load_ids WHERE kind = 'team');
DELETE FROM subscriptions WHERE user_id IN (SELECT id FROM load_ids WHERE kind = 'user');
DELETE FROM teams WHERE team_id IN (SELECT id FROM load_ids WHERE kind = 'team');
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM load_ids WHERE kind = 'user');
DELETE FROM login_histories WHERE user_id IN (SELECT id FROM load_ids WHERE kind = 'user') OR (user_id IS NULL AND email_attempted LIKE 'load%@mq-load.test');
DELETE FROM admin_error_logs WHERE user_id IN (SELECT id FROM load_ids WHERE kind = 'user');
DELETE FROM users WHERE user_id IN (SELECT id FROM load_ids WHERE kind = 'user');
COMMIT;
TRUNCATE TABLE load_ids;
DROP TABLE load_ids;
SELECT COUNT(*) AS load_users_left FROM users WHERE email LIKE 'load%@mq-load.test';
EXIT
