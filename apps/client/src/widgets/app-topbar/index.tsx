import { ChevronDown, CreditCard, LogOut, Menu as MenuIcon, UserRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { planName } from '@/entities/plan'
import { useMeQuery } from '@/entities/user'
import { useLogout } from '@/features/auth'
import { LanguageMenu } from '@/features/change-language'
import { ThemeToggle } from '@/features/toggle-theme'
import { Avatar, Badge, IconButton, Menu, MenuItem, MenuSeparator } from '@/shared/ui'
import { NotificationBell } from '@/widgets/notification-bell'
import { ROUTES } from '@/shared/config/routes'

function UserMenu() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const logout = useLogout()
  const { data: me } = useMeQuery()
  if (!me) return <span className="size-8 animate-pulse rounded-full bg-gray-200 dark:bg-gray-800" />

  return (
    <Menu
      label={t('userMenu.label')}
      trigger={({ open, toggle, id }) => (
        <button
          type="button"
          onClick={toggle}
          aria-expanded={open}
          aria-controls={id}
          aria-haspopup="menu"
          className="flex items-center gap-2 rounded-full p-1 pr-2 transition-colors hover:bg-gray-100 dark:hover:bg-gray-800"
        >
          <Avatar src={me.profileImageUrl} name={me.nickname} size="sm" />
          <span className="hidden text-sm font-medium text-gray-800 sm:inline dark:text-gray-200">{me.nickname}</span>
          <ChevronDown className="hidden size-4 text-gray-400 sm:inline" />
        </button>
      )}
    >
      {(close) => {
        const go = (to: string) => () => {
          close()
          navigate(to)
        }
        return (
          <>
            <div className="flex items-center gap-3 px-3 py-3">
              <Avatar src={me.profileImageUrl} name={me.nickname} size="md" />
              <div className="min-w-0">
                <p className="truncate font-semibold text-gray-900 dark:text-white">{me.nickname}</p>
                <p className="truncate text-xs text-gray-500 dark:text-gray-400">{me.email}</p>
                <Badge className="mt-1">{planName(me.plan)}</Badge>
              </div>
            </div>
            <MenuSeparator />
            <MenuItem icon={<UserRound />} onClick={go(ROUTES.settings)}>
              {t('userMenu.profile')}
            </MenuItem>
            <MenuItem icon={<CreditCard />} onClick={go(ROUTES.billing)}>
              {t('userMenu.billing')}
            </MenuItem>
            <MenuSeparator />
            <MenuItem
              danger
              icon={<LogOut />}
              onClick={() => {
                close()
                void logout()
              }}
            >
              {t('userMenu.logout')}
            </MenuItem>
          </>
        )
      }}
    </Menu>
  )
}

export function AppTopbar({ onOpenSidebar }: { onOpenSidebar: () => void }) {
  const { t } = useTranslation()
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 bg-white/80 px-4 backdrop-blur-md sm:px-6 dark:border-gray-800 dark:bg-gray-950/80">
      <div className="flex min-w-0 items-center gap-2">
        <IconButton label={t('nav.openMenu')} className="md:hidden" onClick={onOpenSidebar}>
          <MenuIcon className="size-5" />
        </IconButton>
      </div>
      <div className="flex items-center gap-1">
        <LanguageMenu />
        <ThemeToggle />
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  )
}
