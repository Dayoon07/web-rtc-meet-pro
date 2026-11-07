import { useQuery } from '@tanstack/react-query'
import { http } from '@/shared/api'

export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER'

/** 팀 안에서 역할에 따라 할 수 있는 일. 어느 역할이 무엇을 할 수 있는지는 서버(TeamRole)만 안다 */
export type TeamPermission =
  | 'INVITE_MEMBER'
  | 'CREATE_GROUP_ROOM'
  | 'CANCEL_ANY_MEETING'
  | 'MANAGE_ROLES'
  | 'REMOVE_MEMBER'
  | 'DELETE_TEAM'

export type TeamSummary = {
  id: number
  name: string
  description: string | null
  ownerId: number
  myRole: TeamRole
  /** 내 역할로 이 팀에서 할 수 있는 일 (서버가 계산) */
  myPermissions: TeamPermission[]
  memberCount: number
  createdAt: string
}

export type TeamMember = {
  userId: number
  nickname: string
  email: string
  profileImageUrl: string | null
  role: TeamRole
  joinedAt: string
  /** 지금 보는 사람이 이 팀원을 내보낼 수 있는지 (서버가 계산) */
  removable: boolean
}

export type TeamDetail = {
  team: TeamSummary
  members: TeamMember[]
  limits: { maxMembers: number; maxGroupRooms: number; pendingInvitations: number }
}

export type Invitation = {
  id: number
  teamId: number
  teamName: string
  inviterName: string
  inviteeEmail: string
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'CANCELED'
  expiresAt: string
  createdAt: string
}

export const teamKeys = {
  all: ['teams'] as const,
  detail: (id: number) => ['teams', id] as const,
  invitations: (id: number) => ['teams', id, 'invitations'] as const,
  myInvitations: ['invitations'] as const,
}

export const teamApi = {
  list: () => http<TeamSummary[]>('/teams'),
  detail: (id: number) => http<TeamDetail>(`/teams/${id}`),
  create: (body: { name: string; description?: string }) => http<TeamSummary>('/teams', { method: 'POST', json: body }),
  invite: (teamId: number, email: string) => http<Invitation>(`/teams/${teamId}/invitations`, { method: 'POST', json: { email } }),
  teamInvitations: (teamId: number) => http<Invitation[]>(`/teams/${teamId}/invitations`),
  cancelInvitation: (teamId: number, invitationId: number) =>
    http<void>(`/teams/${teamId}/invitations/${invitationId}`, { method: 'DELETE' }),
  myInvitations: () => http<Invitation[]>('/invitations'),
  /** 팀원 역할 바꾸기 (ADMIN 지정·해제). MANAGE_ROLES 권한(팀 OWNER)만 */
  changeRole: (teamId: number, userId: number, role: Exclude<TeamRole, 'OWNER'>) =>
    http<TeamMember>(`/teams/${teamId}/members/${userId}/role`, { method: 'PATCH', json: { role } }),
  /** 팀 나가기 (OWNER 는 못 나간다. 팀을 삭제해야 한다) */
  leave: (teamId: number) => http<void>(`/teams/${teamId}/leave`, { method: 'POST' }),
  /** 팀원 내보내기. REMOVE_MEMBER 권한 */
  removeMember: (teamId: number, userId: number) => http<void>(`/teams/${teamId}/members/${userId}`, { method: 'DELETE' }),
  /** 팀 삭제. DELETE_TEAM 권한(OWNER)만 */
  remove: (teamId: number) => http<void>(`/teams/${teamId}`, { method: 'DELETE' }),
  accept: (id: number) => http<TeamSummary>(`/invitations/${id}/accept`, { method: 'POST' }),
  decline: (id: number) => http<void>(`/invitations/${id}/decline`, { method: 'POST' }),
}

export const useTeamsQuery = (enabled = true) => useQuery({ queryKey: teamKeys.all, queryFn: teamApi.list, enabled })
export const useTeamQuery = (id: number) => useQuery({ queryKey: teamKeys.detail(id), queryFn: () => teamApi.detail(id), enabled: id > 0 })
export const useTeamInvitationsQuery = (id: number, enabled: boolean) =>
  useQuery({ queryKey: teamKeys.invitations(id), queryFn: () => teamApi.teamInvitations(id), enabled })
export const useMyInvitationsQuery = () => useQuery({ queryKey: teamKeys.myInvitations, queryFn: teamApi.myInvitations })

/** 버튼을 보여 줄지 판단. 역할 이름으로 직접 비교하지 않는다 (권한 표가 바뀌어도 화면은 그대로) */
export const hasTeamPermission = (team: TeamSummary | undefined, permission: TeamPermission) =>
  team?.myPermissions.includes(permission) ?? false
