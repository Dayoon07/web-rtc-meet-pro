import { useQuery, useQueryClient } from '@tanstack/react-query'
import { LogOut, TriangleAlert } from 'lucide-react'
import { useEffect } from 'react'
import { Navigate, NavLink, Outlet, useNavigate } from 'react-router'
import { adminApi, ApiError, useSession } from './lib/api'
import { Button, cx, FullPageSpinner, Logo } from './ui'

/**
 * 로그인 + 시스템 관리자 확인 후 화면. 관리자가 아니면(403) 로그아웃시키고 로그인 화면으로.
 * (서버가 /api/admin 아래를 매 요청 DB 에서 다시 확인하므로, 화면 확인은 안내용이다)
 */
export function AdminLayout() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const loggedIn = useSession((s) => s.accessToken !== null)
  const me = useQuery({ queryKey: ['admin', 'me'], queryFn: adminApi.me, enabled: loggedIn, retry: false })

  const forbidden = me.error instanceof ApiError && me.error.status === 403
  useEffect(() => {
    if (!forbidden) return
    void adminApi.logout().then(() => navigate('/login', { replace: true, state: { notAdmin: true } }))
  }, [forbidden, navigate])

  if (!loggedIn) return <Navigate to="/login" replace />
  if (me.isPending || forbidden) return <FullPageSpinner />

  const logout = async () => {
    await adminApi.logout()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white/90 backdrop-blur dark:border-gray-800 dark:bg-gray-950/90">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4 sm:gap-6">
          <Logo compact />
          <nav className="flex items-center gap-1">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                cx(
                  'flex h-9 items-center gap-1.5 rounded-lg px-3 text-sm font-medium whitespace-nowrap',
                  isActive ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200' : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
                )
              }
            >
              <TriangleAlert className="size-4" />
              에러 기록
            </NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-sm text-gray-500 sm:inline dark:text-gray-400">{me.data?.email}</span>
            <Button variant="ghost" icon={<LogOut className="size-4" />} onClick={() => void logout()}>
              <span className="hidden sm:inline">로그아웃</span>
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-8">
        {me.isError && !forbidden ? (
          <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{me.error.message}</p>
        ) : (
          <Outlet />
        )}
      </main>
    </div>
  )
}
