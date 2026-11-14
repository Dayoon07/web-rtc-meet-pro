import { useTranslation } from 'react-i18next'
import { SERVER_PATHS } from '@/shared/api'
import { buttonClass, GoogleMark } from '@/shared/ui'
import { useAuthProviders } from '../api/auth-api'

export function GoogleButton({ mode = 'login' }: { mode?: 'login' | 'signup' }) {
  const { t } = useTranslation()
  const { data } = useAuthProviders()
  const label = t(mode === 'login' ? 'auth.google.continue' : 'auth.google.signup')

  if (!data?.google) {
    return (
      <div className="flex flex-col gap-1.5">
        <button type="button" disabled className={buttonClass('outline', 'lg', 'w-full')}>
          <GoogleMark />
          {label}
        </button>
        {data && <p className="text-center text-xs text-gray-400">{t('auth.google.notConfigured')}</p>}
      </div>
    )
  }

  return (
    <a href={SERVER_PATHS.googleLogin} className={buttonClass('outline', 'lg', 'w-full')}>
      <GoogleMark />
      {label}
    </a>
  )
}
