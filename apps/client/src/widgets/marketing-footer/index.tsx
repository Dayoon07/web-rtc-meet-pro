import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { COMPANY } from '@/shared/config/company'
import { Logo } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

export function MarketingFooter() {
  const { t } = useTranslation()
  const columns = [
    {
      title: t('footer.product'),
      links: [
        { label: t('nav.features'), to: '/#features' },
        { label: t('nav.pricing'), to: ROUTES.pricing },
      ],
    },
    {
      title: t('footer.getStarted'),
      links: [
        { label: t('footer.signup'), to: ROUTES.signup },
        { label: t('footer.login'), to: ROUTES.login },
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
      <div className="border-t border-gray-200 dark:border-gray-800">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-6 text-xs text-gray-500 sm:px-6 dark:text-gray-400">
          <nav className="flex flex-wrap gap-x-4 gap-y-1">
            <Link to={ROUTES.terms} className="hover:text-gray-900 dark:hover:text-white">
              {t('legal.terms')}
            </Link>
            <Link to={ROUTES.privacy} className="font-bold text-gray-900 hover:underline dark:text-white">
              {t('legal.privacy')}
            </Link>
            <Link to={ROUTES.licenses} className="hover:text-gray-900 dark:hover:text-white">
              {t('legal.licenses')}
            </Link>
          </nav>
          <p className="leading-relaxed">
            {t('legal.company.name')} {COMPANY.name} · {t('legal.company.ceo')} {COMPANY.ceo} · {t('legal.company.bizNo')} {COMPANY.bizNo} ·{' '}
            {t('legal.company.mailOrderNo')} {COMPANY.mailOrderNo}
            <br />
            {t('legal.company.address')} {COMPANY.address} · {t('legal.company.phone')} {COMPANY.phone} · {COMPANY.email} · {t('legal.company.hosting')}{' '}
            {COMPANY.hosting}
          </p>
          <p>© {new Date().getFullYear()} MQ</p>
        </div>
      </div>
    </footer>
  )
}
