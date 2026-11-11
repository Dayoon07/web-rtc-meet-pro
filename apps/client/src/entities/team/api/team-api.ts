import { useQuery } from '@tanstack/react-query'
import { API, request } from '@/shared/api'
import type { ChangeRoleRequest, CreateTeamRequest, Invitation, InviteRequest, TeamDetail, TeamMember, TeamRole, TeamSummary } from '../model/types'

export const teamKeys = {
  all: ['teams'] as const,
  detail: (id: number) => ['teams', id] as const,
  invitations: (id: number) => ['teams', id, 'invitations'] as const,
  myInvitations: ['invitations'] as const,
}

export const teamApi = {
  list: () => request<TeamSummary[]>(API.teams.root),
  detail: (id: number) => request<TeamDetail>(API.teams.item(id)),
  create: (body: CreateTeamRequest) => request<TeamSummary>(API.teams.root, { method: 'POST', json: body }),
  invite: (teamId: number, email: string) =>
    request<Invitation>(API.teams.invitations(teamId), { method: 'POST', json: { email } satisfies InviteRequest }),
  teamInvitations: (teamId: number) => request<Invitation[]>(API.teams.invitations(teamId)),
  cancelInvitation: (teamId: number, invitationId: number) => request<void>(API.teams.invitation(teamId, invitationId), { method: 'DELETE' }),
  myInvitations: () => request<Invitation[]>(API.invitations.mine),
  /** 팀원 역할 바꾸기 (ADMIN 지정·해제). MANAGE_ROLES 권한(팀 OWNER)만 */
  changeRole: (teamId: number, userId: number, role: Exclude<TeamRole, 'OWNER'>) =>
    request<TeamMember>(API.teams.memberRole(teamId, userId), { method: 'PATCH', json: { role } satisfies ChangeRoleRequest }),
  /** 팀 나가기 (OWNER 는 못 나간다. 팀을 삭제해야 한다) */
  leave: (teamId: number) => request<void>(API.teams.leave(teamId), { method: 'POST' }),
  /** 팀원 내보내기. REMOVE_MEMBER 권한 */
  removeMember: (teamId: number, userId: number) => request<void>(API.teams.member(teamId, userId), { method: 'DELETE' }),
  /** 팀 삭제. DELETE_TEAM 권한(OWNER)만 */
  remove: (teamId: number) => request<void>(API.teams.item(teamId), { method: 'DELETE' }),
  accept: (id: number) => request<TeamSummary>(API.invitations.accept(id), { method: 'POST' }),
  decline: (id: number) => request<void>(API.invitations.decline(id), { method: 'POST' }),
}

export const useTeamsQuery = (enabled = true) => useQuery({ queryKey: teamKeys.all, queryFn: teamApi.list, enabled })
export const useTeamQuery = (id: number) => useQuery({ queryKey: teamKeys.detail(id), queryFn: () => teamApi.detail(id), enabled: id > 0 })
export const useTeamInvitationsQuery = (id: number, enabled: boolean) =>
  useQuery({ queryKey: teamKeys.invitations(id), queryFn: () => teamApi.teamInvitations(id), enabled })
export const useMyInvitationsQuery = () => useQuery({ queryKey: teamKeys.myInvitations, queryFn: teamApi.myInvitations })
