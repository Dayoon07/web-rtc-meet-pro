import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CircleCheck, CircleX, MailCheck } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { useIsAuthenticated } from '@/entities/session'
import { userApi, userKeys, useMeQuery } from '@/entities/user'
import { authApi, useLogout } from '@/features/auth'
import { ApiError, errorMessage } from '@/shared/api'
import { Button, FullPageSpinner, LinkButton, Spinner } from '@/shared/ui'

/**
 * 이메일로 가입한 뒤 인증 전에 보이는 화면. 메일의 링크를 누르면(다른 기기여도) 인증되고,
 * 이 화면은 "인증했어요" 를 누르거나 창으로 돌아오면 다시 확인해서 앱으로 보낸다.
 */
export function VerifyEmailPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const logout = useLogout()
  const { data: me, refetch, isFetching } = useMeQuery()
  const [cooldown, setCooldown] = useState(0)

  // 다른 탭(메일 링크)에서 인증하고 돌아오면 바로 반영
  useEffect(() => {
    const onFocus = () => void refetch()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [refetch])
  useEffect(() => {
    if (cooldown <= 0) return
    const id = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(id)
  }, [cooldown])

  const resend = useMutation({
    mutationFn: userApi.resendVerification,
    onSuccess: () => {
      toast.success(t('auth.verify.resent'))
      setCooldown(60)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'RESEND_TOO_SOON') setCooldown(Number(e.params.seconds) || 60)
    },
  })

  const check = async () => {
    const { data } = await refetch()
    if (data && !data.emailVerified) toast(t('auth.verify.notYet'))
    else void queryClient.invalidateQueries({ queryKey: userKeys.me })
  }

  if (!me) return <FullPageSpinner />
  return (
    <div className="flex flex-col gap-6">
      <div className="flex size-12 items-center justify-center rounded-full bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
        <MailCheck className="size-6" />
      </div>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.verify.title')}</h1>
        <p className="mt-2 text-sm leading-relaxed text-gray-500 dark:text-gray-400">
          {t('auth.verify.body', { email: me.email })}
        </p>
      </div>
      <div className="flex flex-col gap-2">
        <Button size="lg" onClick={() => void check()} loading={isFetching}>
          {t('auth.verify.done')}
        </Button>
        <Button size="lg" variant="outline" onClick={() => resend.mutate()} loading={resend.isPending} disabled={cooldown > 0}>
          {cooldown > 0 ? t('auth.verify.resendIn', { seconds: cooldown }) : t('auth.verify.resend')}
        </Button>
        {resend.error && !(resend.error instanceof ApiError && resend.error.code === 'RESEND_TOO_SOON') && (
          <p className="text-sm text-red-600 dark:text-red-400">{errorMessage(resend.error)}</p>
        )}
      </div>
      <p className="text-center text-sm text-gray-500 dark:text-gray-400">
        {t('auth.verify.wrongEmail')}{' '}
        <button type="button" onClick={() => void logout()} className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
          {t('auth.verify.logout')}
        </button>
      </p>
    </div>
  )
}

/** 메일의 링크(/verify-email/confirm?token=...). 로그인하지 않은 기기에서 열어도 인증된다 */
export function ConfirmEmailPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const loggedIn = useIsAuthenticated()
  const queryClient = useQueryClient()
  const confirm = useMutation({
    mutationFn: () => authApi.confirmEmail(token),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: userKeys.me }),
  })
  // 페이지에 들어오면 한 번만 확인한다 (StrictMode 의 두 번 실행 방지)
  const started = useRef(false)
  useEffect(() => {
    if (started.current || !token) return
    started.current = true
    confirm.mutate()
  }, [token, confirm])

  const failed = !token || confirm.isError
  if (!failed && !confirm.isSuccess) {
    return (
      <div className="flex justify-center py-16">
        <Spinner className="size-7 text-brand-600" />
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-6">
      <div
        className={
          failed
            ? 'flex size-12 items-center justify-center rounded-full bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-300'
            : 'flex size-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300'
        }
      >
        {failed ? <CircleX className="size-6" /> : <CircleCheck className="size-6" />}
      </div>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
          {failed ? t('auth.verify.failTitle') : t('auth.verify.successTitle')}
        </h1>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
          {failed ? (confirm.error ? errorMessage(confirm.error) : t('errors.EMAIL_VERIFY_TOKEN_INVALID')) : t('auth.verify.successBody')}
        </p>
      </div>
      <LinkButton to={loggedIn ? (failed ? '/verify-email' : '/app/chat') : '/login'} size="lg">
        {loggedIn ? (failed ? t('auth.verify.again') : t('auth.verify.goApp')) : t('auth.login.submit')}
      </LinkButton>
    </div>
  )
}
