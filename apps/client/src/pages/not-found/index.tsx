import { useTranslation } from 'react-i18next'
import { LinkButton } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <section className="mx-auto flex min-h-[60vh] max-w-md flex-col items-center justify-center gap-4 px-4 py-24 text-center">
      <p className="text-sm font-semibold text-brand-600 dark:text-brand-400">404</p>
      <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t('notFound.title')}</h1>
      <p className="text-gray-500 dark:text-gray-400">{t('notFound.body')}</p>
      <LinkButton to={ROUTES.home} className="mt-2">
        {t('notFound.home')}
      </LinkButton>
    </section>
  )
}
