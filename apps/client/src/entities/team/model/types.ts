export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export type TeamPermission =
  | 'INVITE_MEMBER'
  | 'CREATE_GROUP_ROOM'
  | 'CANCEL_ANY_MEETING'
  | 'MANAGE_ROLES'
  | 'REMOVE_MEMBER'
  | 'DELETE_TEAM'
  | 'DELETE_ANY_MESSAGE'

export type TeamSummary = {
  id: number
  name: string
  description: string | null
  ownerId: number
  myRole: TeamRole
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
  removable: boolean
}

export type TeamDetail = {
  team: TeamSummary
  members: TeamMember[]
  limits: {
    maxMembers: number
    maxGroupRooms: number | null
    pendingInvitations: number
    usedMembers: number
    ownedTeamCount: number
    maxMeetingParticipants: number
    recordingEnabled: boolean
    planCode: string
  }
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

export type InviteLink = {
  id: number
  token: string
  hasPassword: boolean
  joinCount: number
  createdByName: string | null
  createdAt: string
}

export type InviteLinkPreview = {
  teamId: number
  teamName: string
  teamDescription: string | null
  memberCount: number
  hasPassword: boolean
  alreadyMember: boolean
}

export type InviteLinkJoined = { teamId: number; teamName: string }

export type CreateTeamRequest = { name: string; description?: string }
export type InviteLinkPasswordRequest = { password?: string }
export type InviteRequest = { email: string }
export type ChangeRoleRequest = { role: Exclude<TeamRole, 'OWNER'> }
