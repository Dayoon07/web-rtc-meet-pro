import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { useSessionStore } from '@/entities/session'
import { userApi, userKeys } from '@/entities/user'
import { authApi } from '@/features/auth'
import { ApiError } from '@/shared/api'
import { Spinner } from '@/shared/ui'

/**
 * 앱(Android·Windows)이 넘긴 이동 경로. 같은 사이트 안의 경로만 허용한다 (open redirect 방지).
 * "//evil.com", "/\evil.com" 처럼 다른 사이트로 해석될 수 있는 값은 버린다.
 */
function safeNext(next: string | null): string | null {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return null
  return next
}

/**
 * ?code= 로 받은 1회용 코드를 토큰으로 교환하고 프로필 완성 여부에 따라 이동.
 * - 구글 로그인: 서버가 리다이렉트로 보내준다
 * - 앱 → 브라우저 이동(화상회의 등): 앱이 ?code=…&next=/meet/12 로 연다
 */
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
        navigate(me.profileCompleted ? (safeNext(params.get('next')) ?? '/app/chat') : '/onboarding', { replace: true })
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
