import { matchPath, useLocation } from 'react-router'
import { APP_CHILD_ROUTES, ROUTES } from '@/shared/config/routes'

const num = (value?: string) => (value ? Number(value) : undefined)

/**
 * 주소에서 지금 팀·방: /app/chat/:teamId/:roomId 또는 /app/teams/:teamId.
 * 채팅 화면 밖(앱 사이드바)에서도 읽어야 해서 useParams 대신 주소를 직접 맞춰 본다
 */
export function useChatAddress(): { teamId?: number; roomId?: number } {
  const { pathname } = useLocation()
  const chat = matchPath(`${ROUTES.app}/${APP_CHILD_ROUTES.chat}`, pathname)
  if (chat) return { teamId: num(chat.params.teamId), roomId: num(chat.params.roomId) }
  const team = matchPath(`${ROUTES.app}/${APP_CHILD_ROUTES.team}`, pathname)
  return { teamId: num(team?.params.teamId) }
}
