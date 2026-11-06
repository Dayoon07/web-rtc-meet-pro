import { useTranslation } from 'react-i18next'
import { PricingTable } from '@/widgets/pricing-table'

export function BillingPage() {
  const { t } = useTranslation()
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('billing.title')}</h2>
        <p className="mt-1 text-gray-500 dark:text-gray-400">{t('billing.subtitle')}</p>
      </div>
      <PricingTable />
    </div>
  )
}
