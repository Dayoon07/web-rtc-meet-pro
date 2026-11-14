import type { TeamPermission, TeamSummary } from '../model/types'

export const hasTeamPermission = (team: TeamSummary | undefined, permission: TeamPermission) =>
  team?.myPermissions.includes(permission) ?? false
