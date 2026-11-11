import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'
import { useIsAuthenticated } from '@/entities/session'
import { useMeQuery } from '@/entities/user'
import { LanguageMenu } from '@/features/change-language'
import { ThemeToggle } from '@/features/toggle-theme'
import { cn } from '@/shared/lib/cn'
import { Avatar, LinkButton, Logo } from '@/shared/ui'

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
    isActive ? 'text-gray-900 dark:text-white' : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white',
  )

export function MarketingHeader() {
  const { t } = useTranslation()
  const isAuthenticated = useIsAuthenticated()
  const { data: me } = useMeQuery(isAuthenticated)

  return (
    <header className="sticky top-0 z-40 border-b border-gray-200/70 bg-white/80 backdrop-blur-md dark:border-gray-800/70 dark:bg-gray-950/80">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Logo />
          <nav className="hidden items-center gap-1 md:flex">
            <a href="/#features" className={navClass({ isActive: false })}>
              {t('nav.features')}
            </a>
            <NavLink to="/pricing" className={navClass}>
              {t('nav.pricing')}
            </NavLink>
          </nav>
        </div>
        <div className="flex items-center gap-1 sm:gap-2">
          <LanguageMenu />
          <ThemeToggle />
          {isAuthenticated ? (
            <>
              <LinkButton to="/app/chat" size="sm">
                {t('nav.goToApp')}
              </LinkButton>
              {me && <Avatar src={me.profileImageUrl} name={me.nickname} size="sm" className="hidden sm:inline-flex" />}
            </>
          ) : (
            <>
              <LinkButton to="/login" variant="ghost" size="sm" className="hidden sm:inline-flex">
                {t('nav.login')}
              </LinkButton>
              <LinkButton to="/signup" size="sm">
                {t('nav.startFree')}
              </LinkButton>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
