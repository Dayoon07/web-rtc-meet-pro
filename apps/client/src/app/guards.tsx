import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { chatKeys } from '@/entities/chat'
import { useAwayTracker, usePresenceRealtime } from '@/entities/presence'
import { onPushNavigate, syncPush } from '@/entities/push'
import { useIsAuthenticated } from '@/entities/session'
import { useMeQuery } from '@/entities/user'
import { realtime } from '@/shared/api'
import { FullPageSpinner } from '@/shared/ui'
import { useNotificationSideEffects } from '@/widgets/notification-bell'
import { ROUTES } from '@/shared/config/routes'

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
  if (!isAuthenticated) return <Navigate to={`${ROUTES.login}?redirect=${encodeURIComponent(location.pathname)}`} replace />
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
  return <Outlet />
}

/** 이메일 인증 안내는 인증 전에만 */
export function VerifyEmailOnly() {
  const { data: me } = useMeQuery()
  if (!me) return <FullPageSpinner />
  if (me.emailVerified) return <Navigate to={ROUTES.chat()} replace />
  return <Outlet />
}

export function OnboardingOnly() {
  const { data: me } = useMeQuery()
  if (!me) return <FullPageSpinner />
  if (me.profileCompleted) return <Navigate to={ROUTES.chat()} replace />
  return <Outlet />
}

export function GuestOnly() {
  return useIsAuthenticated() ? <Navigate to={ROUTES.chat()} replace /> : <Outlet />
}
