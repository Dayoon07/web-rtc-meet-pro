import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { useSessionStore } from '@/entities/session'
import { userApi, userKeys } from '@/entities/user'
import { authApi } from '@/features/auth'
import { ApiError } from '@/shared/api'
import { Spinner } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { returnToPath } from '@/shared/lib/return-to'

function safeNext(next: string | null): string | null {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return null
  return next
}

export function OAuthCallbackPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const setTokens = useSessionStore((s) => s.setTokens)
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true

    const code = params.get('code')
    if (!code) return void navigate(`${ROUTES.login}?error=OAUTH_FAILED`, { replace: true })

    ;(async () => {
      try {
        queryClient.clear()
        setTokens(await authApi.exchangeOAuthCode(code))
        const me = await userApi.me()
        queryClient.setQueryData(userKeys.me, me)
        navigate(me.profileCompleted ? (safeNext(params.get('next')) ?? returnToPath()) : ROUTES.onboarding, { replace: true })
      } catch (e) {
        const error = e instanceof ApiError ? e.code : 'OAUTH_FAILED'
        navigate(`${ROUTES.login}?error=${error}`, { replace: true })
      }
    })()
  }, [params, navigate, queryClient, setTokens])

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-white dark:bg-gray-950">
      <Spinner className="size-8 text-brand-600" />
      <p className="text-sm text-gray-500">{t('auth.callback.loading')}</p>
    </div>
  )
}
