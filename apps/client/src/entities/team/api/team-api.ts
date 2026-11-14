import { useQuery } from '@tanstack/react-query'
import { API, request } from '@/shared/api'
import type {
  ChangeRoleRequest,
  CreateTeamRequest,
  Invitation,
  InviteLink,
  InviteLinkJoined,
  InviteLinkPasswordRequest,
  InviteLinkPreview,
  InviteRequest,
  TeamDetail,
  TeamMember,
  TeamRole,
  TeamSummary,
} from '../model/types'

export const teamKeys = {
  all: ['teams'] as const,
  detail: (id: number) => ['teams', id] as const,
  invitations: (id: number) => ['teams', id, 'invitations'] as const,
  myInvitations: ['invitations'] as const,
  inviteLinks: (id: number) => ['teams', id, 'invite-links'] as const,
  inviteLinkPreview: (token: string) => ['invite-links', token] as const,
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
  changeRole: (teamId: number, userId: number, role: Exclude<TeamRole, 'OWNER'>) =>
    request<TeamMember>(API.teams.memberRole(teamId, userId), { method: 'PATCH', json: { role } satisfies ChangeRoleRequest }),
  leave: (teamId: number) => request<void>(API.teams.leave(teamId), { method: 'POST' }),
  removeMember: (teamId: number, userId: number) => request<void>(API.teams.member(teamId, userId), { method: 'DELETE' }),
  remove: (teamId: number) => request<void>(API.teams.item(teamId), { method: 'DELETE' }),
  accept: (id: number) => request<TeamSummary>(API.invitations.accept(id), { method: 'POST' }),
  decline: (id: number) => request<void>(API.invitations.decline(id), { method: 'POST' }),
  inviteLinks: (teamId: number) => request<InviteLink[]>(API.teams.inviteLinks(teamId)),
  createInviteLink: (teamId: number, password?: string) =>
    request<InviteLink>(API.teams.inviteLinks(teamId), { method: 'POST', json: { password } satisfies InviteLinkPasswordRequest }),
  deleteInviteLink: (teamId: number, linkId: number) => request<void>(API.teams.inviteLink(teamId, linkId), { method: 'DELETE' }),
  inviteLinkPreview: (token: string) => request<InviteLinkPreview>(API.inviteLinks.item(token)),
  joinByInviteLink: (token: string, password?: string) =>
    request<InviteLinkJoined>(API.inviteLinks.join(token), { method: 'POST', json: { password } satisfies InviteLinkPasswordRequest }),
}

export const useTeamsQuery = (enabled = true) => useQuery({ queryKey: teamKeys.all, queryFn: teamApi.list, enabled })
export const useTeamQuery = (id: number) => useQuery({ queryKey: teamKeys.detail(id), queryFn: () => teamApi.detail(id), enabled: id > 0 })
export const useTeamInvitationsQuery = (id: number, enabled: boolean) =>
  useQuery({ queryKey: teamKeys.invitations(id), queryFn: () => teamApi.teamInvitations(id), enabled })
export const useMyInvitationsQuery = () => useQuery({ queryKey: teamKeys.myInvitations, queryFn: teamApi.myInvitations })
export const useInviteLinksQuery = (teamId: number) => useQuery({ queryKey: teamKeys.inviteLinks(teamId), queryFn: () => teamApi.inviteLinks(teamId) })
export const useInviteLinkPreviewQuery = (token: string) =>
  useQuery({ queryKey: teamKeys.inviteLinkPreview(token), queryFn: () => teamApi.inviteLinkPreview(token), enabled: !!token, retry: false })
