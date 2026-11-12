-- v1.9: 환불 (결제 취소). 환불 금액·사유·한 사람을 남기고, 환불 알림(PAYMENT_REFUNDED)을 더한다. 환불 시각은 기존 canceled_at
--   NLS_LANG=KOREAN_KOREA.AL32UTF8 sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.9-refunds.sql'
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

ALTER TABLE payments ADD (
    refunded_amount     NUMBER(12)      DEFAULT 0 NOT NULL,
    refund_reason       VARCHAR2(200),
    refunded_by         NUMBER(19)
);
ALTER TABLE payments ADD CONSTRAINT ck_payments_refunded_amount CHECK (refunded_amount >= 0 AND refunded_amount <= amount);
COMMENT ON COLUMN payments.refunded_amount IS '환불한 금액 (전액이면 amount, 환불 시각은 canceled_at)';
COMMENT ON COLUMN payments.refund_reason IS '환불 사유';
COMMENT ON COLUMN payments.refunded_by IS '환불한 사람 (결제한 사람 또는 관리자)';

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'TEAM_INVITATION', 'TEAM_INVITATION_ACCEPTED',
    'CHAT_ROOM_INVITED', 'CHAT_MENTION',
    'MEETING_SCHEDULED', 'MEETING_REMINDER', 'MEETING_STARTED', 'MEETING_CANCELED', 'MEETING_UPDATED',
    'MEETING_NOTES_READY',
    'PLAN_EXPIRING', 'PLAN_EXPIRED', 'PAYMENT_FAILED', 'PAYMENT_REFUNDED',
    'MEMBER_OVERAGE', 'OVERAGE_CHARGE_SOON'
));

COMMIT;
PROMPT  환불 칸과 환불 알림 종류를 추가했어요.
EXIT
