-- =====================================================================
-- v0.6 → v0.7: 가입 이메일 인증 (users.email_verified_at). 기존 데이터는 그대로
--   이미 있는 회원은 모두 "인증됨"으로 둔다 (이 기능 전에 가입한 사람이 갑자기 막히지 않게)
-- 실행 (저장소 루트에서):
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.7-email-verification.sql'
-- 다른 창에서 users 행을 고치고 COMMIT 하지 않았다면 UPDATE 가 기다린다 → 그 창에서 먼저 COMMIT
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE users ADD (email_verified_at TIMESTAMP(6));
UPDATE users SET email_verified_at = created_dt WHERE email_verified_at IS NULL;
COMMIT;

PROMPT  users.email_verified_at 을 추가하고 기존 회원을 인증됨으로 표시했어요.
EXIT
