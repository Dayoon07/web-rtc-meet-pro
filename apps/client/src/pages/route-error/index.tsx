import { RotateCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { isRouteErrorResponse, useRouteError } from 'react-router'
import { Button, FullPageSpinner, LinkButton } from '@/shared/ui'

/**
 * 화면 파일(lazy route)을 못 불러온 에러. 새로 배포한 뒤 예전 탭에서 옛날 파일 이름을 찾을 때,
 * 개발 중 Vite 가 의존성을 다시 묶었을 때 난다. 새로고침하면 새 파일을 받으므로 한 번은 자동으로 새로고침한다.
 */
const CHUNK_ERROR = /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Outdated Optimize Dep/i
const RELOAD_KEY = 'limeet.chunkReloadAt'

function errorText(error: unknown): string {
  if (isRouteErrorResponse(error)) return `${error.status} ${error.statusText}`
  if (error instanceof Error) return error.message
  return String(error ?? '')
}

/** 방금(10초 안) 새로고침했는데 또 실패하면 무한 새로고침이 되므로 그때는 안내 화면을 보여 준다 */
function reloadOnceForChunkError(error: unknown): boolean {
  if (!CHUNK_ERROR.test(errorText(error))) return false
  try {
    const last = Number(sessionStorage.getItem(RELOAD_KEY) ?? 0)
    if (Date.now() - last < 10_000) return false
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()))
  } catch {
    return false
  }
  window.location.reload()
  return true
}

/** 라우트에서 처리하지 못한 에러: React Router 기본 개발자 화면 대신 보여 준다 */
export function RouteErrorPage() {
  const { t } = useTranslation()
  const error = useRouteError()
  const [reloading, setReloading] = useState(false)

  useEffect(() => {
    if (reloadOnceForChunkError(error)) setReloading(true)
    else console.error(error)
  }, [error])

  if (reloading) return <FullPageSpinner />

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-white px-4 py-24 text-center dark:bg-gray-950">
      <p className="text-sm font-semibold text-brand-600 dark:text-brand-400">Limeet Pro</p>
      <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t('routeError.title')}</h1>
      <p className="max-w-md text-gray-500 dark:text-gray-400">{t('routeError.body')}</p>
      <div className="mt-2 flex gap-3">
        <Button icon={<RotateCw className="size-4" />} onClick={() => window.location.reload()}>
          {t('routeError.reload')}
        </Button>
        <LinkButton to="/" variant="outline">
          {t('routeError.home')}
        </LinkButton>
      </div>
      {/* 개발 중에는 원인을 바로 볼 수 있게 에러 문구도 보여 준다 */}
      {import.meta.env.DEV && (
        <pre className="mt-6 max-w-2xl overflow-x-auto rounded-lg bg-gray-100 p-4 text-left text-xs whitespace-pre-wrap text-gray-700 dark:bg-gray-900 dark:text-gray-300">
          {errorText(error)}
        </pre>
      )}
    </main>
  )
}
