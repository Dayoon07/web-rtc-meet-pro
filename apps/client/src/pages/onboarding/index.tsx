import { useTranslation } from 'react-i18next'
import { useMeQuery } from '@/entities/user'
import { OnboardingForm } from '@/features/complete-profile'
import { Avatar, Spinner } from '@/shared/ui'

export function OnboardingPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  if (!me) return <Spinner className="mx-auto size-6 text-brand-600" />

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-center gap-4">
        <Avatar src={me.profileImageUrl} name={me.nickname} size="lg" />
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.onboarding.title')}</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{t('auth.onboarding.subtitle', { email: me.email })}</p>
        </div>
      </div>
      <OnboardingForm me={me} />
    </div>
  )
}
