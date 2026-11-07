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
-- 반드시 XEPDB1 에 접속해서 실행한다. CDB 루트(XE)에서 CREATE USER 를 하면 ORA-65096 이 나고,
-- C## 으로 시작하는 공통 사용자를 만들어도 XEPDB1 에서는 로그인할 수 없다 (앱은 XEPDB1 의 limeet 을 쓴다).
-- 테이블 구조가 바뀌었을 때는 SYSTEM 없이 limeet 계정으로 db/local-reset.sql 을 실행하면 된다.
-- 예전 wrmp 계정은 더 이상 쓰지 않는다. 지우려면 SYSTEM 으로:  DROP USER wrmp CASCADE;
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- CDB 루트(서비스 XE, sqlplus / as sysdba 등)에 접속하면 일반 계정을 만들 수 없다 (ORA-65096).
-- 그 경우 바로 멈추고 접속 방법을 알려 준다
BEGIN
    IF SYS_CONTEXT('USERENV', 'CON_NAME') = 'CDB$ROOT' THEN
        RAISE_APPLICATION_ERROR(-20001, 'XEPDB1 에 접속해서 실행하세요: sqlplus system@//localhost:1521/XEPDB1  (지금 접속: CDB 루트. 같은 창에서는 ALTER SESSION SET CONTAINER = XEPDB1; 후 다시 실행)');
    END IF;
END;
/

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
