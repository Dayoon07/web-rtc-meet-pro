-- =====================================================================
-- 로컬 개발 DB 준비 (PC 에 설치된 Oracle XE 21c, 한 번만 실행)
--   wrmp 계정을 만들고 그 계정에 테이블(schema.sql)과 플랜 초기값(seed.sql)을 넣는다.
--
-- 실행 (저장소 루트에서, SYSTEM 비밀번호는 XE 설치 때 정한 것).
-- NLS_LANG 을 먼저 설정하지 않으면 seed.sql 의 한글이 깨져 플랜이 1개만 들어가는 등 실패한다.
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   cmd:         set NLS_LANG=KOREAN_KOREA.AL32UTF8
--   sqlplus system@//localhost:1521/XEPDB1 '@db/local-setup.sql'
--
-- 다시 처음부터 만들려면 SYSTEM 으로 접속해서:  DROP USER wrmp CASCADE;  후 이 파일을 다시 실행
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE USER wrmp IDENTIFIED BY wrmp
  DEFAULT TABLESPACE users
  QUOTA UNLIMITED ON users;

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO wrmp;

CONNECT wrmp/wrmp@//localhost:1521/XEPDB1

@@schema.sql
@@seed.sql

PROMPT
PROMPT  wrmp 계정과 테이블을 만들었어요. 이제 서버를 켜면 됩니다 (DB_URL 기본값: localhost:1521/XEPDB1).
EXIT
