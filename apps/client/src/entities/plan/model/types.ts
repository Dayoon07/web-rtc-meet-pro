export type Plan = {
  code: string
  name: string
  description: string | null
  monthlyPrice: number
  maxOwnedTeams: number
  maxMembersPerTeam: number
  maxGroupRoomsPerTeam: number
  recordingEnabled: boolean
}

export type SubscriptionStatus = 'ACTIVE' | 'CANCELED' | 'EXPIRED' | 'PAST_DUE'

export type Subscription = {
  plan: Plan
  status: SubscriptionStatus
  startedAt: string
  currentPeriodEnd: string | null
  ownedTeams: number
}
