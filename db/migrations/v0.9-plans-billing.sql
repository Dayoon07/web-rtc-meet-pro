-- =====================================================================
-- v0.8 → v0.9: 요금제 다시 정의 + 여러 구독(가장 높은 플랜 적용) + 자동결제 (기존 데이터는 그대로)
--   - plans: 코드 PLAN1/2/3 → PRO/TEAM/BUSINESS, 가격·한도 새로 정함
--            회의당 최대 인원, 녹화 저장 용량(GB), 녹화 보관 일수 추가. 그룹 채팅방 NULL = 제한 없음
--   - subscriptions: 사용자당 여러 구독 (플랜마다 하나), 월/연 결제, 자동결제 실패 재시도, 만료 알림·만료 창 확인
--   - payments: 결제 주기와 결제로 늘어난 기간 끝
--   - notifications.type: PLAN_EXPIRING, PLAN_EXPIRED, PAYMENT_FAILED
--   기존 유료 플랜 구독(지금까지 0원으로 바꾼 것)은 오늘부터 30일짜리 월 구독으로 바꾼다 (자동결제 꺼짐)
-- 실행 (저장소 루트에서):
--   PowerShell:  $env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
--   sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.9-plans-billing.sql'
-- =====================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- 1. plans
ALTER TABLE plans MODIFY (max_group_rooms_per_team NULL);
ALTER TABLE plans ADD (
    max_meeting_participants  NUMBER(10) DEFAULT 6  NOT NULL,
    recording_storage_gb      NUMBER(10) DEFAULT 0  NOT NULL,
    recording_retention_days  NUMBER(10) DEFAULT 30 NOT NULL
);

UPDATE plans SET name = '무료', description = '가볍게 쓰는 개인·모임',
       monthly_price = 0, max_owned_teams = 1, max_members_per_team = 10, max_group_rooms_per_team = 3,
       max_meeting_participants = 6, recording_enabled = 'N', recording_storage_gb = 0, recording_retention_days = 30, sort_order = 0
 WHERE code = 'FREE';
UPDATE plans SET code = 'PRO', name = 'Pro', description = '자주 회의하는 개인',
       monthly_price = 4900, max_owned_teams = 3, max_members_per_team = 15, max_group_rooms_per_team = 10,
       max_meeting_participants = 15, recording_enabled = 'N', recording_storage_gb = 0, recording_retention_days = 30, sort_order = 1
 WHERE code = 'PLAN1';
UPDATE plans SET code = 'TEAM', name = 'Team', description = '작은 회사·팀',
       monthly_price = 19900, max_owned_teams = 3, max_members_per_team = 50, max_group_rooms_per_team = NULL,
       max_meeting_participants = 30, recording_enabled = 'Y', recording_storage_gb = 50, recording_retention_days = 90, sort_order = 2
 WHERE code = 'PLAN2';
UPDATE plans SET code = 'BUSINESS', name = 'Business', description = '관리가 필요한 회사',
       monthly_price = 49900, max_owned_teams = 10, max_members_per_team = 200, max_group_rooms_per_team = NULL,
       max_meeting_participants = 50, recording_enabled = 'Y', recording_storage_gb = 200, recording_retention_days = 365, sort_order = 3
 WHERE code = 'PLAN3';

-- 2. subscriptions
DROP INDEX uidx_subscriptions_active;
ALTER TABLE subscriptions ADD (
    billing_cycle           VARCHAR2(10),
    failed_attempts         NUMBER(10) DEFAULT 0 NOT NULL,
    next_retry_at           TIMESTAMP(6),
    expiring_notified_at    TIMESTAMP(6),
    expired_at              TIMESTAMP(6),
    expiry_acknowledged_at  TIMESTAMP(6),
    CONSTRAINT ck_subscriptions_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY'))
);
-- 같은 플랜의 살아 있는 구독(ACTIVE·PAST_DUE)은 사용자당 하나. 다른 플랜은 여러 개 동시에 가질 수 있다
CREATE UNIQUE INDEX uidx_subscriptions_active ON subscriptions (
    CASE WHEN status IN ('ACTIVE', 'PAST_DUE') THEN user_id END,
    CASE WHEN status IN ('ACTIVE', 'PAST_DUE') THEN plan_id END
);
CREATE INDEX idx_subscriptions_period ON subscriptions (status, current_period_end);

UPDATE subscriptions s
   SET current_period_end = SYSTIMESTAMP + INTERVAL '30' DAY, billing_cycle = 'MONTHLY', auto_renew = 'N'
 WHERE status = 'ACTIVE'
   AND plan_id IN (SELECT plan_id FROM plans WHERE code <> 'FREE');

-- 3. payments
ALTER TABLE payments ADD (
    billing_cycle  VARCHAR2(10),
    period_end     TIMESTAMP(6),
    CONSTRAINT ck_payments_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY'))
);

-- 4. notifications
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED',
    'CHAT_ROOM_INVITED',
    'MEETING_SCHEDULED', 'MEETING_REMINDER', 'MEETING_STARTED', 'MEETING_CANCELED', 'MEETING_UPDATED',
    'PLAN_EXPIRING', 'PLAN_EXPIRED', 'PAYMENT_FAILED'
));

COMMIT;
PROMPT  요금제(FREE/PRO/TEAM/BUSINESS), 여러 구독, 결제 주기, 플랜 알림을 추가했어요.
EXIT
