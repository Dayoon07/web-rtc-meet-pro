import type { TeamPermission, TeamSummary } from '../model/types'

/** 버튼을 보여 줄지 판단. 역할 이름으로 직접 비교하지 않는다 (권한 표가 바뀌어도 화면은 그대로) */
export const hasTeamPermission = (team: TeamSummary | undefined, permission: TeamPermission) =>
  team?.myPermissions.includes(permission) ?? false
