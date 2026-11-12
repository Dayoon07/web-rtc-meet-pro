import { CircleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { LoginForm } from '@/features/auth'
import { translateDynamic } from '@/shared/i18n'
import { ROUTES, safeRedirect } from '@/shared/config/routes'

export function LoginPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const oauthError = params.get('error')

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.login.title')}</h1>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{t('auth.login.subtitle')}</p>
      </div>
      {oauthError && (
        <div role="alert" className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-300">
          <CircleAlert className="mt-0.5 size-4 shrink-0" />
          {translateDynamic(`errors.${oauthError}`, undefined, t('errors.OAUTH_FAILED'))}
        </div>
      )}
      <LoginForm redirectTo={safeRedirect(params.get('redirect'))} />
      <p className="text-center text-sm text-gray-500 dark:text-gray-400">
        {t('auth.login.noAccount')}{' '}
        <Link to={ROUTES.signup} className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
          {t('auth.login.signupLink')}
        </Link>
      </p>
    </div>
  )
}
