import { CircleCheck, KeyRound, Mail } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useMeQuery } from '@/entities/user'
import { LanguageSelector } from '@/features/change-language'
import { ChangePasswordButton } from '@/features/change-password'
import { EditProfileForm, ProfileImageSection } from '@/features/edit-profile'
import { ThemeSelector } from '@/features/toggle-theme'
import { WithdrawAccount } from '@/features/withdraw-account'
import { Badge, Card, GoogleMark, Spinner } from '@/shared/ui'
import { NotificationSettings } from '@/features/notification-settings'

function Section({ heading, children }: { heading: { title: string; description: string }; children: ReactNode }) {
  const { title, description } = heading
  return (
    <Card className="grid gap-6 md:grid-cols-[220px_1fr]">
      <div>
        <h3 className="font-semibold text-gray-900 dark:text-white">{title}</h3>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{description}</p>
      </div>
      <div>{children}</div>
    </Card>
  )
}

function Row({ row }: { row: { icon: ReactNode; label: string; value: ReactNode } }) {
  const { icon, label, value } = row
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <span className="flex items-center gap-3 text-sm text-gray-700 dark:text-gray-300 [&_svg]:size-4.5">
        {icon}
        {label}
      </span>
      <span className="text-sm text-gray-500 dark:text-gray-400">{value}</span>
    </div>
  )
}

export function SettingsPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  if (!me) return <Spinner className="mx-auto size-6 text-brand-600" />

  const googleLinked = me.linkedProviders.includes('GOOGLE')
  const ok = (label: string) => (
    <Badge tone="green">
      <CircleCheck className="size-3" /> {label}
    </Badge>
  )

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('settings.title')}</h2>
        <p className="mt-1 text-gray-500 dark:text-gray-400">{t('settings.subtitle')}</p>
      </div>

      <Section heading={{ title: t('settings.photoTitle'), description: t('settings.photoDesc') }}>
        <ProfileImageSection me={me} />
      </Section>

      <Section heading={{ title: t('settings.infoTitle'), description: t('settings.infoDesc') }}>
        <EditProfileForm key={me.id} me={me} />
      </Section>

      <Section heading={{ title: t('settings.loginTitle'), description: t('settings.loginDesc') }}>
        <div className="divide-y divide-gray-100 dark:divide-gray-800">
          <Row row={{ icon: <Mail />, label: t('auth.fields.email'), value: me.email }} />
          <Row row={{ icon: <KeyRound />, label: t('settings.password'), value: <span className="flex items-center gap-2">
                {me.hasPassword ? ok(t('settings.passwordSet')) : <Badge tone="gray">{t('settings.passwordNone')}</Badge>}
                <ChangePasswordButton me={me} />
              </span> }} />
          <Row row={{ icon: <GoogleMark className="size-4.5" />, label: 'Google', value: googleLinked ? ok(t('settings.linked')) : <Badge tone="gray">{t('settings.notLinked')}</Badge> }} />
        </div>
      </Section>

      <Section heading={{ title: t('notifySettings.title'), description: t('notifySettings.desc') }}>
        <NotificationSettings key={me.id} me={me} />
      </Section>

      <Section heading={{ title: t('settings.languageTitle'), description: t('settings.languageDesc') }}>
        <LanguageSelector />
      </Section>

      <Section heading={{ title: t('settings.displayTitle'), description: t('settings.displayDesc') }}>
        <div className="max-w-sm">
          <ThemeSelector />
        </div>
      </Section>

      <Section heading={{ title: t('settings.withdraw.title'), description: t('settings.withdraw.desc') }}>
        <WithdrawAccount me={me} />
      </Section>
    </div>
  )
}
