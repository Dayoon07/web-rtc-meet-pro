import { useTranslation } from 'react-i18next'
import { PricingTable } from '@/widgets/pricing-table'

export function PricingPage() {
  const { t } = useTranslation()
  return (
    <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-24">
      <div className="mx-auto mb-14 max-w-2xl text-center">
        <p className="text-sm font-semibold text-brand-600 dark:text-brand-400">{t('pricing.eyebrow')}</p>
        <h1 className="mt-2 text-4xl font-extrabold tracking-tight text-gray-900 dark:text-white">{t('pricing.title')}</h1>
        <p className="mt-4 text-gray-600 dark:text-gray-400">{t('pricing.subtitle')}</p>
      </div>
      <PricingTable />
    </section>
  )
}
