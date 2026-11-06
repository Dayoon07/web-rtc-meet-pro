import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Logo } from '@/shared/ui'

export function MarketingFooter() {
  const { t } = useTranslation()
  const columns = [
    {
      title: t('footer.product'),
      links: [
        { label: t('nav.features'), to: '/#features' },
        { label: t('nav.pricing'), to: '/pricing' },
      ],
    },
    {
      title: t('footer.getStarted'),
      links: [
        { label: t('footer.signup'), to: '/signup' },
        { label: t('footer.login'), to: '/login' },
      ],
    },
  ]

  return (
    <footer className="border-t border-gray-200 bg-gray-50 dark:border-gray-800 dark:bg-gray-900/40">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-[2fr_1fr_1fr]">
        <div className="flex flex-col gap-3">
          <Logo />
          <p className="max-w-xs text-sm text-gray-500 dark:text-gray-400">{t('footer.tagline')}</p>
        </div>
        {columns.map((col) => (
          <div key={col.title} className="flex flex-col gap-3">
            <p className="text-sm font-semibold text-gray-900 dark:text-white">{col.title}</p>
            {col.links.map((l) => (
              <Link key={l.to} to={l.to} className="text-sm text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white">
                {l.label}
              </Link>
            ))}
          </div>
        ))}
      </div>
      <div className="border-t border-gray-200 py-6 text-center text-xs text-gray-400 dark:border-gray-800">© {new Date().getFullYear()} Limeet Pro</div>
    </footer>
  )
}
