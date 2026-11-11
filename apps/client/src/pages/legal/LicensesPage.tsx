import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { SERVER_PATHS } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { Spinner } from '@/shared/ui'

type License = { name: string; version: string; license: string; url: string; copyleft: boolean }

/** 오픈소스 라이선스 고지: 웹 화면과 앱에 들어간 라이브러리 (scripts/oss-licenses.py 가 만든 /oss-licenses.json) */
export function LicensesPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'web' | 'app'>('web')
  const { data, isPending } = useQuery({
    queryKey: ['oss-licenses'],
    queryFn: async () => (await fetch(SERVER_PATHS.ossLicenses)).json() as Promise<{ web: License[]; app: License[] }>,
    staleTime: Infinity,
  })
  const items = data?.[tab] ?? []
  return (
    <article className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t('legal.licenses')}</h1>
      <p className="mt-3 text-sm leading-relaxed text-gray-600 dark:text-gray-400">{t('legal.licensesIntro')}</p>
      <div className="mt-6 flex gap-2">
        {(['web', 'app'] as const).map((k) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={cn(
              'rounded-lg px-3 py-1.5 text-sm font-medium',
              tab === k ? 'bg-brand-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300',
            )}
          >
            {t(k === 'web' ? 'legal.licensesWeb' : 'legal.licensesApp')} {data ? `(${data[k].length})` : ''}
          </button>
        ))}
      </div>
      {isPending ? (
        <Spinner className="mt-8 size-5 text-brand-600" />
      ) : (
        <ul className="mt-6 divide-y divide-gray-100 rounded-xl border border-gray-200 text-sm dark:divide-gray-800 dark:border-gray-800">
          {items.map((l) => (
            <li key={`${l.name}@${l.version}`} className="flex flex-wrap items-baseline gap-x-3 gap-y-0.5 px-4 py-2">
              {l.url ? (
                <a href={l.url} target="_blank" rel="noreferrer" className="font-medium text-gray-900 hover:underline dark:text-gray-100">
                  {l.name}
                </a>
              ) : (
                <span className="font-medium text-gray-900 dark:text-gray-100">{l.name}</span>
              )}
              <span className="text-xs text-gray-400">{l.version}</span>
              <span className="ml-auto text-xs text-gray-600 dark:text-gray-400">{l.license}</span>
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}
