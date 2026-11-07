-- =====================================================================
-- 로컬 개발 DB 초기화: limeet 스키마의 테이블을 전부 지우고 schema.sql + seed.sql 로 다시 만든다.
-- 테이블 구조(schema.sql)가 바뀌었을 때 쓴다. 로컬에 쌓인 데이터(회원·팀·채팅)는 모두 사라진다.
-- 운영 DB 에서는 절대 실행하지 않는다 (limeet 계정이 아니면 바로 멈춘다).
--
-- 실행 (저장소 루트에서, SYSTEM 필요 없음):
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/local-reset.sql'
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

BEGIN
    IF USER <> 'LIMEET' THEN
        RAISE_APPLICATION_ERROR(-20001, 'limeet 계정으로 접속해서 실행하세요 (지금 접속: ' || USER || ')');
    END IF;
    FOR t IN (SELECT table_name FROM user_tables) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE "' || t.table_name || '" CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/

@@schema.sql
@@seed.sql

PROMPT
PROMPT  limeet 테이블을 새로 만들었어요.
EXIT
