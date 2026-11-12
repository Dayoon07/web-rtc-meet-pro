import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { Navigate, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { chatKeys } from '@/entities/chat'
import { useAwayTracker, usePresenceRealtime } from '@/entities/presence'
import { onPushNavigate, syncPush } from '@/entities/push'
import { useIsAuthenticated } from '@/entities/session'
import { useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { realtime } from '@/shared/api'
import { FullPageSpinner } from '@/shared/ui'
import { useNotificationSideEffects } from '@/widgets/notification-bell'
import { useChatDesktopNotifications } from '@/features/desktop-notifications'
import { ROUTES, safeRedirect } from '@/shared/config/routes'
import { clearReturnToIfArrived, rememberReturnTo, returnToPath } from '@/shared/lib/return-to'

/** 로그인 상태에서만 STOMP 연결을 유지하고, 실시간 이벤트로 캐시를 갱신한다 */
function RealtimeBridge() {
  const queryClient = useQueryClient()
  useEffect(() => {
    realtime.start()
    // 새 메시지·방 변화 때만 안 읽은 수를 다시 (입력 중·읽음 이벤트는 자주 와서 제외)
    const off = realtime.on('chat', (payload) => {
      const type = (payload as { type?: string }).type
      if (type === 'MESSAGE' || type === 'ROOM') void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
    })
    return () => {
      off()
      realtime.stop()
    }
  }, [queryClient])
  useNotificationSideEffects(useCallback((cb) => realtime.on('notifications', cb), []))
  useChatDesktopNotifications()
  usePresenceRealtime()
  useAwayTracker(true)
  const navigate = useNavigate()
  useEffect(() => {
    void syncPush()
    return onPushNavigate((link) => navigate(link))
  }, [navigate])
  return null
}

export function RequireAuth() {
  const isAuthenticated = useIsAuthenticated()
  const location = useLocation()
  if (!isAuthenticated) {
    // 로그인 대신 회원가입·구글 가입으로 가도 끝나면 이 주소로 돌아오게 기억한다
    rememberReturnTo(location.pathname + location.search)
    return <Navigate to={`${ROUTES.login}?redirect=${encodeURIComponent(location.pathname)}`} replace />
  }
  return (
    <>
      <RealtimeBridge />
      <Outlet />
    </>
  )
}

/** 구글로 가입하고 추가 정보를 안 넣은 사용자는 온보딩으로, 이메일 인증 전인 사용자는 인증 안내로 */
export function RequireCompletedProfile() {
  const { data: me } = useMeQuery()
  if (!me) return <FullPageSpinner />
  if (!me.profileCompleted) return <Navigate to={ROUTES.onboarding} replace />
  if (!me.emailVerified) return <Navigate to={ROUTES.verifyEmail} replace />
  return (
    <>
      <ReturnToArrival />
      <Outlet />
    </>
  )
}

/** 가입·인증을 다 마치고 기억해 둔 주소(초대 링크 등)에 실제로 도착하면 지운다 (중간에 인증 화면으로 튕기면 지우지 않는다) */
function ReturnToArrival() {
  const { pathname } = useLocation()
  useEffect(() => clearReturnToIfArrived(pathname), [pathname])
  return null
}

/** 팀이 없으면 먼저 팀을 만들거나 받은 초대를 수락한다 (회사 하나 = 팀 하나, 그 안에 채팅방은 몇 개든) */
export function RequireTeam() {
  const { data: teams } = useTeamsQuery()
  if (!teams) return <FullPageSpinner />
  if (!teams.length) return <Navigate to={ROUTES.start} replace />
  return <Outlet />
}

/** 이메일 인증 안내는 인증 전에만 */
export function VerifyEmailOnly() {
  const { data: me } = useMeQuery()
  if (!me) return <FullPageSpinner />
  if (me.emailVerified) return <Navigate to={returnToPath()} replace />
  return <Outlet />
}

export function OnboardingOnly() {
  const { data: me } = useMeQuery()
  if (!me) return <FullPageSpinner />
  if (me.profileCompleted) return <Navigate to={returnToPath()} replace />
  return <Outlet />
}

/** 로그인·가입 화면은 로그인 전에만. 로그인되면 ?redirect= 주소(예: 팀 초대 링크)로, 없으면 채팅으로 */
export function GuestOnly() {
  const [params] = useSearchParams()
  return useIsAuthenticated() ? <Navigate to={params.get('redirect') ? safeRedirect(params.get('redirect')) : returnToPath()} replace /> : <Outlet />
}
