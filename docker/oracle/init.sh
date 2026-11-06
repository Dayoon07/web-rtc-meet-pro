#!/bin/bash
# gvenzl/oracle-free 최초 기동 시 실행. APP_USER 스키마에 테이블과 시드 데이터를 넣는다.
set -e
export NLS_LANG=AMERICAN_AMERICA.AL32UTF8
sqlplus -s "${APP_USER}/${APP_USER_PASSWORD}@//localhost/FREEPDB1" <<EOF
WHENEVER SQLERROR EXIT SQL.SQLCODE
@/db/schema.sql
@/db/seed.sql
EXIT
EOF
