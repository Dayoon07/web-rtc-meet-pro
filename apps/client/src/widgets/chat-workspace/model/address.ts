import { matchPath, useLocation } from 'react-router'
import { APP_CHILD_ROUTES, ROUTES } from '@/shared/config/routes'

const num = (value?: string) => (value ? Number(value) : undefined)

export function useChatAddress(): { teamId?: number; roomId?: number } {
  const { pathname } = useLocation()
  const chat = matchPath(`${ROUTES.app}/${APP_CHILD_ROUTES.chat}`, pathname)
  if (chat) return { teamId: num(chat.params.teamId), roomId: num(chat.params.roomId) }
  const team = matchPath(`${ROUTES.app}/${APP_CHILD_ROUTES.team}`, pathname)
  return { teamId: num(team?.params.teamId) }
}
