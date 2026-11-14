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

function RealtimeBridge() {
  const queryClient = useQueryClient()
  useEffect(() => {
    realtime.start()
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

function ReturnToArrival() {
  const { pathname } = useLocation()
  useEffect(() => clearReturnToIfArrived(pathname), [pathname])
  return null
}

export function RequireTeam() {
  const { data: teams } = useTeamsQuery()
  if (!teams) return <FullPageSpinner />
  if (!teams.length) return <Navigate to={ROUTES.start} replace />
  return <Outlet />
}

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

export function GuestOnly() {
  const [params] = useSearchParams()
  return useIsAuthenticated() ? <Navigate to={params.get('redirect') ? safeRedirect(params.get('redirect')) : returnToPath()} replace /> : <Outlet />
}
