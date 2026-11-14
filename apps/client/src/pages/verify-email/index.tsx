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
import { ROUTES } from '@/shared/config/routes'
import { returnToPath } from '@/shared/lib/return-to'

export function VerifyEmailPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const logout = useLogout()
  const { data: me, refetch, isFetching } = useMeQuery()
  const [cooldown, setCooldown] = useState(0)

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
      <LinkButton to={loggedIn ? (failed ? ROUTES.verifyEmail : returnToPath()) : ROUTES.login} size="lg">
        {loggedIn ? (failed ? t('auth.verify.again') : t('auth.verify.goApp')) : t('auth.login.submit')}
      </LinkButton>
    </div>
  )
}
