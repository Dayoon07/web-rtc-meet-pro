import { RotateCw } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { isRouteErrorResponse, useRouteError } from 'react-router'
import { reportClientError } from '@/shared/api'
import { newErrorCode } from '@/shared/lib/unexpected-error'
import { Button, ErrorDetails, FullPageSpinner, LinkButton } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

const CHUNK_ERROR = /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Outdated Optimize Dep/i
const RELOAD_KEY = 'limeet.chunkReloadAt'

function errorText(error: unknown): string {
  if (isRouteErrorResponse(error)) return `${error.status} ${error.statusText}`
  if (error instanceof Error) return error.message
  return String(error ?? '')
}

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

export function RouteErrorPage() {
  const { t } = useTranslation()
  const error = useRouteError()
  const [reloading, setReloading] = useState(false)
  const [errorCode] = useState(newErrorCode)
  const reported = useRef(false)

  useEffect(() => {
    if (reloadOnceForChunkError(error)) {
      setReloading(true)
      return
    }
    if (reported.current) return
    reported.current = true
    console.error(error)
    void reportClientError({
      errorCode,
      category: 'CLIENT',
      name: error instanceof Error ? error.name : isRouteErrorResponse(error) ? `Route ${error.status}` : 'RouteError',
      message: errorText(error),
      stack: error instanceof Error ? error.stack : undefined,
    })
  }, [error, errorCode])

  if (reloading) return <FullPageSpinner />

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-white px-4 py-24 text-center dark:bg-gray-950">
      <p className="text-sm font-semibold text-brand-600 dark:text-brand-400">MQ</p>
      <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t('routeError.title')}</h1>
      <p className="max-w-md text-gray-500 dark:text-gray-400">{t('routeError.body')}</p>
      <div className="mt-2 flex gap-3">
        <Button icon={<RotateCw className="size-4" />} onClick={() => window.location.reload()}>
          {t('routeError.reload')}
        </Button>
        <LinkButton to={ROUTES.home} variant="outline">
          {t('routeError.home')}
        </LinkButton>
      </div>
      <div className="mt-6 w-full max-w-md rounded-2xl border border-gray-200 p-5 dark:border-gray-800">
        <ErrorDetails errorCode={errorCode} reason="CLIENT" />
      </div>
      {import.meta.env.DEV && (
        <pre className="mt-2 max-w-2xl overflow-x-auto rounded-lg bg-gray-100 p-4 text-left text-xs whitespace-pre-wrap text-gray-700 dark:bg-gray-900 dark:text-gray-300">
          {errorText(error)}
        </pre>
      )}
    </main>
  )
}
