import { useTranslation } from 'react-i18next'
import { COMPANY } from '@/shared/config/company'

export type LegalSection = {
  title: string
  body?: string[]
  table?: { head: string[]; rows: string[][] }
}

export function LegalDoc({ titleKey, sections }: { titleKey: 'legal.privacy' | 'legal.terms'; sections: LegalSection[] }) {
  const { t, i18n } = useTranslation()
  return (
    <article className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t(titleKey)}</h1>
      <p className="mt-2 text-sm text-gray-500">{t('legal.effective', { date: COMPANY.policyDate })}</p>
      {!i18n.language.startsWith('ko') && (
        <p className="mt-4 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:bg-amber-500/10 dark:text-amber-200">{t('legal.koreanOnly')}</p>
      )}
      <div className="mt-8 flex flex-col gap-8" lang="ko">
        {sections.map((s) => (
          <section key={s.title}>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">{s.title}</h2>
            {s.table && (
              <div className="mt-3 overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-800">
                <table className="w-full min-w-[32rem] text-left text-sm">
                  <thead className="bg-gray-50 text-gray-600 dark:bg-gray-900 dark:text-gray-400">
                    <tr>
                      {s.table.head.map((h) => (
                        <th key={h} className="px-3 py-2 font-medium">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 text-gray-700 dark:divide-gray-800 dark:text-gray-300">
                    {s.table.rows.map((r, i) => (
                      <tr key={i}>
                        {r.map((c, j) => (
                          <td key={j} className="px-3 py-2 align-top">
                            {c}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {s.body?.map((p, i) => (
              <p key={i} className="mt-3 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
                {p}
              </p>
            ))}
          </section>
        ))}
      </div>
    </article>
  )
}
