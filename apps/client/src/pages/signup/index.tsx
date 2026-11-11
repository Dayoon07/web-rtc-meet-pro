import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { SignupForm } from '@/features/auth'
import { ROUTES } from '@/shared/config/routes'

export function SignupPage() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.signup.title')}</h1>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{t('auth.signup.subtitle')}</p>
      </div>
      <SignupForm />
      <p className="text-center text-sm text-gray-500 dark:text-gray-400">
        {t('auth.signup.haveAccount')}{' '}
        <Link to={ROUTES.login} className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
          {t('auth.signup.loginLink')}
        </Link>
      </p>
    </div>
  )
}
