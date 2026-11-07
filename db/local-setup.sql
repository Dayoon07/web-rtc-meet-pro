-- =====================================================================
-- 로컬 개발 DB 준비 (PC 에 설치된 Oracle XE 21c, 한 번만 실행)
--   limeet 계정(= 스키마)을 만들고 그 계정에 테이블(schema.sql)과 플랜 초기값(seed.sql)을 넣는다.
--   서비스와 관리자 사이트(별도 서브도메인)가 이 limeet 스키마 하나를 같이 쓴다.
--
-- 실행 (저장소 루트에서, SYSTEM 비밀번호는 XE 설치 때 정한 것).
-- NLS_LANG 을 먼저 설정하지 않으면 seed.sql 의 한글이 깨져 플랜이 1개만 들어가는 등 실패한다.
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   cmd:         set NLS_LANG=KOREAN_KOREA.AL32UTF8
--   sqlplus system@//localhost:1521/XEPDB1 '@db/local-setup.sql'
--
-- 테이블 구조가 바뀌었을 때는 SYSTEM 없이 limeet 계정으로 db/local-reset.sql 을 실행하면 된다.
-- 예전 wrmp 계정은 더 이상 쓰지 않는다. 지우려면 SYSTEM 으로:  DROP USER wrmp CASCADE;
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE USER limeet IDENTIFIED BY limeet
  DEFAULT TABLESPACE users
  QUOTA UNLIMITED ON users;

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO limeet;

CONNECT limeet/limeet@//localhost:1521/XEPDB1

@@schema.sql
@@seed.sql

PROMPT
PROMPT  limeet 계정과 테이블을 만들었어요. 이제 서버를 켜면 됩니다 (DB_URL 기본값: localhost:1521/XEPDB1, 계정 limeet).
EXIT
