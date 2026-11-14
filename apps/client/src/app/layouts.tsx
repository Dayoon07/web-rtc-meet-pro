import { Quote } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation } from 'react-router'
import { useMeQuery } from '@/entities/user'
import { LanguageMenu } from '@/features/change-language'
import { ThemeToggle } from '@/features/toggle-theme'
import { VoiceBar } from '@/features/voice-chat'
import { Avatar, Logo, MqMark } from '@/shared/ui'
import { AppSidebar } from '@/widgets/app-sidebar'
import { AppTopbar } from '@/widgets/app-topbar'
import { TeamRoomPanel, useChatRealtime } from '@/widgets/chat-workspace'
import { MarketingFooter } from '@/widgets/marketing-footer'
import { MarketingHeader } from '@/widgets/marketing-header'
import { ROUTES } from '@/shared/config/routes'

export function MarketingLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <MarketingHeader />
      <main className="flex-1">
        <Outlet />
      </main>
      <MarketingFooter />
    </div>
  )
}

export function AuthLayout() {
  const { t } = useTranslation()
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1fr_1fr]">
      <div className="flex flex-col px-4 py-6 sm:px-8">
        <div className="flex items-center justify-between">
          <Logo />
          <div className="flex items-center gap-1">
            <LanguageMenu />
            <ThemeToggle />
          </div>
        </div>
        <div className="flex flex-1 items-center justify-center py-10">
          <div className="w-full max-w-lg">
            <Outlet />
          </div>
        </div>
      </div>

      <div className="relative hidden overflow-hidden bg-gradient-to-br from-brand-600 via-brand-700 to-brand-900 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -top-24 -right-24 size-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-32 -left-16 size-96 rounded-full bg-fuchsia-400/20 blur-3xl" />
        <p className="relative flex items-center gap-2 text-sm font-bold text-white">
          <MqMark mono className="h-5 w-6" />
          MQ
        </p>
        <div className="relative flex flex-col gap-6">
          <Quote className="size-10 text-brand-300" />
          <p className="text-2xl leading-relaxed font-semibold">{t('authLayout.quote')}</p>
          <div className="flex items-center gap-3">
            <Avatar name={t('authLayout.quoteName')} size="md" className="bg-white text-brand-700" />
            <div>
              <p className="font-semibold">{t('authLayout.quoteName')}</p>
              <p className="text-sm text-brand-200">{t('authLayout.quoteRole')}</p>
            </div>
          </div>
        </div>
        <div className="relative flex items-center gap-3 text-sm text-brand-100">
          <div className="flex -space-x-2">
            {['Alex', 'Seojun', 'Haneul', 'Sam'].map((n) => (
              <Avatar key={n} name={n} size="sm" ring className="bg-white text-brand-700 ring-brand-700" />
            ))}
          </div>
          {t('authLayout.socialProof')}
        </div>
      </div>
    </div>
  )
}


const COLLAPSED_KEY = 'mq.sidebarCollapsed'
const readCollapsed = () => {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const toggleCollapsed = () =>
    setCollapsed((c) => {
      try {
        localStorage.setItem(COLLAPSED_KEY, c ? '0' : '1')
      } catch {
      }
      return !c
    })
  const location = useLocation()
  const { data: me } = useMeQuery()
  useChatRealtime()
  useEffect(() => setSidebarOpen(false), [location.pathname])
  useTranslation()

  return (
    <div className="flex min-h-dvh">
      <AppSidebar
        state={{
          open: sidebarOpen,
          onClose: () => setSidebarOpen(false),
          collapsed,
          onToggleCollapsed: toggleCollapsed,
          slots: { teamPanel: <TeamRoomPanel />, footer: <VoiceBar me={me} /> },
        }}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <AppTopbar onOpenSidebar={() => setSidebarOpen(true)} />
        <main key={location.pathname.startsWith(ROUTES.chat()) ? 'chat' : location.pathname} className="flex-1 px-4 py-8 sm:px-6 lg:px-10">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
