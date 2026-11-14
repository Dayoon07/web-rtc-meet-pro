export type NotificationType =
  | 'TEAM_INVITATION'
  | 'TEAM_INVITATION_ACCEPTED'
  | 'CHAT_ROOM_INVITED'
  | 'CHAT_MENTION'
  | 'THREAD_REPLY'
  | 'MEETING_SCHEDULED'
  | 'MEETING_REMINDER'
  | 'MEETING_STARTED'
  | 'MEETING_CANCELED'
  | 'MEETING_UPDATED'
  | 'MEETING_NOTES_READY'
  | 'PLAN_EXPIRING'
  | 'PLAN_EXPIRED'
  | 'PAYMENT_FAILED'
  | 'MEMBER_OVERAGE'
  | 'OVERAGE_CHARGE_SOON'

export type AppNotification = {
  id: number
  type: NotificationType
  params: Record<string, string | number | null>
  link: string | null
  read: boolean
  createdAt: string
}

export type UnreadCountResponse = { count: number }
