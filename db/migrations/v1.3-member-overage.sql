-- =====================================================================
-- v1.2 → v1.3: 멤버 초과 요금 (플랜 멤버 한도를 넘으면 1명당 월 overage_price_per_member 원)
--   - plans.overage_price_per_member : 한도를 넘은 멤버 1명당 한 달 요금 (기본 9,900원)
--   - teams.overage_*                : 소유자가 "한도 초과 허용"에 동의했는지·언제·누가, 경고 메일, 따로 결제할 날
--       월 자동결제 플랜이면 갱신 때 (플랜 요금 + 초과 인원 × 요금) 을 한 번에, 아니면 (무료·연 결제·자동결제 꺼짐) 한 달마다 따로
--   - payments.payment_kind / overage_members / overage_amount : 결제 건에 들어간 초과 인원 요금 (영수증에 따로 보인다)
--   - notifications.type 에 MEMBER_OVERAGE(한도 초과 경고), OVERAGE_CHARGE_SOON(3일 뒤 결제 안내)
-- 실행 (저장소 루트에서):
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.3-member-overage.sql'
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE plans ADD (
    overage_price_per_member  NUMBER(12)  DEFAULT 9900 NOT NULL
);

ALTER TABLE teams ADD (
    overage_allowed          CHAR(1)       DEFAULT 'N' NOT NULL,   -- 소유자가 한도 초과 초대를 허용 (= 초과 요금 동의)
    overage_agreed_at        TIMESTAMP(6),                         -- 동의한 시각 (전자상거래 분쟁 대비 기록)
    overage_agreed_by        NUMBER(19),                           -- 동의한 사람 (소유자)
    overage_warned_at        TIMESTAMP(6),                         -- 마지막 "한도 초과" 경고 메일 (하루 한 번까지)
    overage_next_charge_at   TIMESTAMP(6),                         -- 따로 결제할 날 (월 자동결제 플랜이 아닐 때)
    overage_notice_for       TIMESTAMP(6),                         -- "3일 뒤 결제" 메일을 보낸 결제일 (같은 결제일에 두 번 보내지 않게)
    overage_failed_attempts  NUMBER(5)     DEFAULT 0 NOT NULL,
    CONSTRAINT ck_teams_overage_allowed CHECK (overage_allowed IN ('Y', 'N'))
);
CREATE INDEX idx_teams_overage ON teams (overage_allowed, status);

ALTER TABLE payments ADD (
    payment_kind     VARCHAR2(20)  DEFAULT 'PLAN' NOT NULL,       -- PLAN(플랜, 초과 요금이 같이 들어갈 수 있음) / OVERAGE(초과 요금만)
    overage_members  NUMBER(10)    DEFAULT 0 NOT NULL,
    overage_amount   NUMBER(12)    DEFAULT 0 NOT NULL,
    CONSTRAINT ck_payments_kind CHECK (payment_kind IN ('PLAN', 'OVERAGE'))
);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED',
    'CHAT_ROOM_INVITED',
    'MEETING_SCHEDULED', 'MEETING_REMINDER', 'MEETING_STARTED', 'MEETING_CANCELED', 'MEETING_UPDATED',
    'MEETING_NOTES_READY',
    'PLAN_EXPIRING', 'PLAN_EXPIRED', 'PAYMENT_FAILED',
    'MEMBER_OVERAGE', 'OVERAGE_CHARGE_SOON'
));

COMMIT;
PROMPT  멤버 초과 요금(plans.overage_price_per_member, teams.overage_*, payments.payment_kind)을 추가했어요.
EXIT
