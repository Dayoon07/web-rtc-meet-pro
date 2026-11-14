import { useEffect } from 'react'
import { useTeamsQuery } from '../api/team-api'

const KEY = 'mq.currentTeam'

function readRemembered(): number | undefined {
  try {
    return Number(localStorage.getItem(KEY)) || undefined
  } catch {
    return undefined
  }
}

function remember(teamId: number) {
  try {
    localStorage.setItem(KEY, String(teamId))
  } catch {
  }
}

export function useCurrentTeamId(addressTeamId?: number): number | undefined {
  const { data: teams } = useTeamsQuery()
  useEffect(() => {
    if (addressTeamId) remember(addressTeamId)
  }, [addressTeamId])
  if (!teams?.length) return addressTeamId
  const has = (id?: number) => id != null && teams.some((tm) => tm.id === id)
  if (has(addressTeamId)) return addressTeamId
  const remembered = readRemembered()
  if (has(remembered)) return remembered
  return (teams.find((tm) => tm.myRole === 'OWNER') ?? teams[0]).id
}
