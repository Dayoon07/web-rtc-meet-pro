import { useQuery } from '@tanstack/react-query'
import { http } from '@/shared/api'

export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export type TeamSummary = {
  id: number
  name: string
  description: string | null
  ownerId: number
  myRole: TeamRole
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
  accept: (id: number) => http<TeamSummary>(`/invitations/${id}/accept`, { method: 'POST' }),
  decline: (id: number) => http<void>(`/invitations/${id}/decline`, { method: 'POST' }),
}

export const useTeamsQuery = (enabled = true) => useQuery({ queryKey: teamKeys.all, queryFn: teamApi.list, enabled })
export const useTeamQuery = (id: number) => useQuery({ queryKey: teamKeys.detail(id), queryFn: () => teamApi.detail(id), enabled: id > 0 })
export const useTeamInvitationsQuery = (id: number, enabled: boolean) =>
  useQuery({ queryKey: teamKeys.invitations(id), queryFn: () => teamApi.teamInvitations(id), enabled })
export const useMyInvitationsQuery = () => useQuery({ queryKey: teamKeys.myInvitations, queryFn: teamApi.myInvitations })

export const canInvite = (role: TeamRole) => role === 'OWNER' || role === 'ADMIN'
