-- 부하 테스트 계정(load####@mq-load.test)만 이메일 인증 처리 (팀 만들기·초대 링크 참여에 필요)
UPDATE users SET email_verified_at = SYSTIMESTAMP WHERE email LIKE 'load%@mq-load.test' AND email_verified_at IS NULL;
COMMIT;
SELECT COUNT(*) AS verified, MIN(user_id) AS min_id, MAX(user_id) AS max_id FROM users WHERE email LIKE 'load%@mq-load.test';
EXIT
