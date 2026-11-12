// 워크스페이스(팀) 자료 모양. 서버가 돌려주는 값과 서버에 보내는 값(XxxRequest)

export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER'

/** 팀 안에서 역할에 따라 할 수 있는 일. 어느 역할이 무엇을 할 수 있는지는 서버(TeamRole)만 안다 */
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
  limits: {
    maxMembers: number
    /** null = 제한 없음 */
    maxGroupRooms: number | null
    /** 소유자의 모든 팀에 대기 중인 초대 */
    pendingInvitations: number
    /** 소유자의 모든 팀 사람 수 (여러 팀에 있어도 한 명) */
    usedMembers: number
    /** 소유자가 만든 팀 수 */
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

/** 팀 초대 링크 (팀 소유자·관리자가 만들고 지운다. 기간 제한 없음) */
export type InviteLink = {
  id: number
  token: string
  hasPassword: boolean
  /** 이 링크로 들어온 사람 수 */
  joinCount: number
  createdByName: string | null
  createdAt: string
}

/** 초대 링크를 연 사람이 보는 팀 소개 */
export type InviteLinkPreview = {
  teamId: number
  teamName: string
  teamDescription: string | null
  memberCount: number
  hasPassword: boolean
  /** 이미 멤버면 수락 대신 "채팅으로 가기" */
  alreadyMember: boolean
}

export type InviteLinkJoined = { teamId: number; teamName: string }

// ---- 서버에 보내는 값 ----
export type CreateTeamRequest = { name: string; description?: string }
/** 초대 링크 만들기·들어가기 (비밀번호가 없으면 비워 둔다) */
export type InviteLinkPasswordRequest = { password?: string }
export type InviteRequest = { email: string }
export type ChangeRoleRequest = { role: Exclude<TeamRole, 'OWNER'> }
