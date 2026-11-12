import { useEffect } from 'react'
import { useTeamsQuery } from '../api/team-api'

/** 마지막에 본 팀 (이 브라우저에만 기억. 못 읽어도 내 팀 → 첫 팀으로 열린다) */
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
    // 저장이 안 돼도 이번 화면은 그 팀으로 보인다
  }
}

/**
 * 지금 팀: 주소에 팀이 있으면 그 팀(기억해 둔다), 없으면 마지막에 본 팀, 그것도 없으면 내가 만든 팀 → 첫 팀.
 * 사이드바 "채팅 목록" 과 채팅 화면이 같은 팀을 보게 한다
 */
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
