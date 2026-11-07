-- 플랜 초기값. 금액/한도는 이 값만 바꾸면 된다. 연 결제 = 월 가격 × 12 × 0.9 (서버가 계산)
-- ai_notes_minutes / live_caption_minutes: 한 달 AI 회의록 / 실시간 자막·번역 시간(분), 0 = 없음 (자막은 Business 전용)
-- 요금제는 워크스페이스(팀)마다. max_group_rooms_per_team(채널 수) NULL = 제한 없음, max_owned_teams 는 쓰지 않음(워크스페이스는 몇 개든 만들 수 있다)
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, max_meeting_participants, recording_enabled, recording_storage_gb, recording_retention_days, ai_notes_minutes, live_caption_minutes, sort_order)
VALUES ('FREE',     '무료',     '가볍게 쓰는 모임·작은 팀',  0,  1,  10, NULL,  6, 'N',   0,  30, 0, 0, 0);
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, max_meeting_participants, recording_enabled, recording_storage_gb, recording_retention_days, ai_notes_minutes, live_caption_minutes, sort_order)
VALUES ('PRO',      'Pro',      '작은 워크스페이스',       4900,  3,  15, NULL, 15, 'N',   0,  30, 120, 0, 1);
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, max_meeting_participants, recording_enabled, recording_storage_gb, recording_retention_days, ai_notes_minutes, live_caption_minutes, sort_order)
VALUES ('TEAM',     'Team',     '작은 회사 (녹화 포함)',          19900,  3,  50, NULL, 30, 'Y',  50,  90, 600, 0, 2);
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, max_meeting_participants, recording_enabled, recording_storage_gb, recording_retention_days, ai_notes_minutes, live_caption_minutes, sort_order)
VALUES ('BUSINESS', 'Business', '관리가 필요한 회사',     49900, 10, 200, NULL, 50, 'Y', 200, 365, 3000, 600, 3);
COMMIT;
