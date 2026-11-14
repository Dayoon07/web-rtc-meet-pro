import { useQuery, useQueryClient } from '@tanstack/react-query'
import { LogOut, Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { adminApi, ApiError, useSession } from '@/lib/api'
import { Button, cx, FullPageSpinner, Logo } from '@/ui'
import { isMenuActive, MENU_SECTIONS } from './screens'

export function AdminLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const queryClient = useQueryClient()
  const loggedIn = useSession((s) => s.accessToken !== null)
  const me = useQuery({ queryKey: ['admin', 'me'], queryFn: adminApi.me, enabled: loggedIn, retry: false })
  const [menuOpen, setMenuOpen] = useState(false)

  const forbidden = me.error instanceof ApiError && me.error.status === 403
  useEffect(() => {
    if (!forbidden) return
    void adminApi.logout().then(() => navigate('/login', { replace: true, state: { notAdmin: true } }))
  }, [forbidden, navigate])
  useEffect(() => setMenuOpen(false), [pathname])

  if (!loggedIn) return <Navigate to="/login" replace />
  if (me.isPending || forbidden) return <FullPageSpinner />

  const logout = async () => {
    await adminApi.logout()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  const nav = (
    <nav className="flex flex-col gap-5 p-3">
      {MENU_SECTIONS.map((section) => (
        <div key={section.title} className="flex flex-col gap-0.5">
          <p className="px-3 pb-1 text-xs font-semibold text-gray-400 dark:text-gray-500">{section.title}</p>
          {section.items.map((s) => {
            const Icon = s.menu!.icon
            const active = isMenuActive(s, pathname)
            return (
              <Link
                key={s.path}
                to={s.path}
                aria-current={active ? 'page' : undefined}
                className={cx(
                  'flex h-9 items-center gap-2.5 rounded-lg px-3 text-sm font-medium whitespace-nowrap',
                  active
                    ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
                )}
              >
                <Icon className="size-4" />
                {s.menu!.label}
              </Link>
            )
          })}
        </div>
      ))}
    </nav>
  )

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-20 border-b border-gray-200 bg-white/90 backdrop-blur dark:border-gray-800 dark:bg-gray-950/90">
        <div className="flex h-14 items-center gap-3 px-4">
          <button
            type="button"
            className="-ml-1 flex size-9 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100 lg:hidden dark:text-gray-300 dark:hover:bg-gray-800"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
          <Link to="/">
            <Logo compact />
          </Link>
          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-sm text-gray-500 sm:inline dark:text-gray-400">{me.data?.email}</span>
            <Button variant="ghost" icon={<LogOut className="size-4" />} onClick={() => void logout()}>
              <span className="hidden sm:inline">로그아웃</span>
            </Button>
          </div>
        </div>
      </header>

      <div className="flex flex-1">
        <aside className="sticky top-14 hidden h-[calc(100dvh-3.5rem)] w-56 shrink-0 overflow-y-auto border-r border-gray-200 bg-white lg:block dark:border-gray-800 dark:bg-gray-950">
          {nav}
        </aside>
        {menuOpen && (
          <div className="fixed inset-0 top-14 z-10 lg:hidden">
            <button type="button" aria-label="메뉴 닫기" className="absolute inset-0 bg-gray-950/30" onClick={() => setMenuOpen(false)} />
            <aside className="relative h-full w-64 overflow-y-auto border-r border-gray-200 bg-white shadow-xl dark:border-gray-800 dark:bg-gray-950">{nav}</aside>
          </div>
        )}
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 sm:py-8">
          <div className="mx-auto w-full max-w-7xl">
            {me.isError && !forbidden ? (
              <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{me.error.message}</p>
            ) : (
              <Outlet />
            )}
          </div>
        </main>
      </div>
    </div>
  )
}
