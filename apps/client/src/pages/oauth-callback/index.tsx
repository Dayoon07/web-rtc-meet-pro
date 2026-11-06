import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { useSessionStore } from '@/entities/session'
import { userApi, userKeys } from '@/entities/user'
import { authApi } from '@/features/auth'
import { ApiError } from '@/shared/api'
import { Spinner } from '@/shared/ui'

/** 서버가 구글 로그인 후 ?code= 로 보내준다. 토큰으로 교환하고 프로필 완성 여부에 따라 이동 */
export function OAuthCallbackPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const setTokens = useSessionStore((s) => s.setTokens)
  const started = useRef(false)

  useEffect(() => {
    // StrictMode 에서 effect 가 두 번 돌아도 1회용 코드는 한 번만 교환
    if (started.current) return
    started.current = true

    const code = params.get('code')
    if (!code) return void navigate('/login?error=OAUTH_FAILED', { replace: true })

    ;(async () => {
      try {
        queryClient.clear()
        setTokens(await authApi.exchangeOAuthCode(code))
        const me = await userApi.me()
        queryClient.setQueryData(userKeys.me, me)
        navigate(me.profileCompleted ? '/app' : '/onboarding', { replace: true })
      } catch (e) {
        const error = e instanceof ApiError ? e.code : 'OAUTH_FAILED'
        navigate(`/login?error=${error}`, { replace: true })
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
