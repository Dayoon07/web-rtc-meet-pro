-- 플랜 초기값. 금액/한도는 이 값만 바꾸면 된다 (현재 전부 0원)
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, recording_enabled, sort_order)
VALUES ('FREE',  '무료',  '개인 / 소규모 팀',   0,  1,  10,  1, 'N', 0);
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, recording_enabled, sort_order)
VALUES ('PLAN1', '플랜1', '팀 3개, 팀원 50명',  0,  3,  50,  3, 'Y', 1);
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, recording_enabled, sort_order)
VALUES ('PLAN2', '플랜2', '팀 5개, 팀원 100명', 0,  5, 100,  5, 'Y', 2);
INSERT INTO plans (code, name, description, monthly_price, max_owned_teams, max_members_per_team, max_group_rooms_per_team, recording_enabled, sort_order)
VALUES ('PLAN3', '플랜3', '팀 10개, 팀원 200명', 0, 10, 200, 10, 'Y', 3);
COMMIT;
