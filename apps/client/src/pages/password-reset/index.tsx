import { useMutation } from '@tanstack/react-query'
import { CircleCheck, MailCheck } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { authApi } from '@/features/auth'
import { errorMessage } from '@/shared/api'
import { Button, Field, FieldError, Input, LinkButton } from '@/shared/ui'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function BackToLogin() {
  const { t } = useTranslation()
  return (
    <p className="text-center text-sm text-gray-500 dark:text-gray-400">
      <Link to="/login" className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
        {t('auth.reset.backToLogin')}
      </Link>
    </p>
  )
}

/** 비밀번호를 잊었을 때: 이메일을 받아 재설정 링크를 보낸다. 가입 여부와 상관없이 같은 안내를 보여 준다 */
export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const request = useMutation({ mutationFn: () => authApi.requestPasswordReset(email.trim()) })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (EMAIL_RE.test(email.trim())) request.mutate()
  }

  if (request.isSuccess) {
    return (
      <div className="flex flex-col gap-6">
        <div className="flex size-12 items-center justify-center rounded-full bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
          <MailCheck className="size-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.reset.sentTitle')}</h1>
          <p className="mt-2 text-sm leading-relaxed text-gray-500 dark:text-gray-400">{t('auth.reset.sentBody', { email: email.trim() })}</p>
        </div>
        <BackToLogin />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.reset.forgotTitle')}</h1>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{t('auth.reset.forgotSubtitle')}</p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Field label={t('auth.fields.email')} htmlFor="reset-email">
          <Input
            id="reset-email"
            type="email"
            autoComplete="email"
            placeholder={t('auth.fields.emailPlaceholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoFocus
          />
        </Field>
        <FieldError>{errorMessage(request.error)}</FieldError>
        <Button type="submit" size="lg" loading={request.isPending} disabled={!EMAIL_RE.test(email.trim())}>
          {t('auth.reset.send')}
        </Button>
      </form>
      <BackToLogin />
    </div>
  )
}

/** 메일의 링크(/reset-password?token=...)로 들어와 새 비밀번호를 정한다 */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const reset = useMutation({ mutationFn: () => authApi.confirmPasswordReset(token, password) })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (password.length < 8) return setError(t('auth.validation.passwordLength'))
    // 가입 화면과 같은 규칙 (영문·숫자 함께)
    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return setError(t('auth.validation.passwordMix'))
    if (password !== confirm) return setError(t('auth.validation.passwordMismatch'))
    setError(null)
    reset.mutate()
  }

  if (reset.isSuccess) {
    return (
      <div className="flex flex-col gap-6">
        <div className="flex size-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300">
          <CircleCheck className="size-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.reset.doneTitle')}</h1>
          <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{t('auth.reset.doneBody')}</p>
        </div>
        <LinkButton to="/login" size="lg">
          {t('auth.login.submit')}
        </LinkButton>
      </div>
    )
  }

  if (!token) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.reset.newTitle')}</h1>
        <p className="text-sm text-red-600 dark:text-red-400">{t('errors.RESET_TOKEN_INVALID')}</p>
        <LinkButton to="/forgot-password" variant="outline">
          {t('auth.reset.again')}
        </LinkButton>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('auth.reset.newTitle')}</h1>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{t('auth.reset.newSubtitle')}</p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Field label={t('auth.reset.newPassword')} htmlFor="reset-password" hint={t('auth.fields.passwordHint')}>
          <Input
            id="reset-password"
            type="password"
            autoComplete="new-password"
            placeholder={t('auth.fields.passwordPlaceholder')}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoFocus
          />
        </Field>
        <Field label={t('auth.fields.passwordConfirm')} htmlFor="reset-confirm">
          <Input id="reset-confirm" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        <FieldError>{error ?? errorMessage(reset.error)}</FieldError>
        {reset.error && (
          <Link to="/forgot-password" className="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
            {t('auth.reset.again')}
          </Link>
        )}
        <Button type="submit" size="lg" loading={reset.isPending} disabled={!password || !confirm}>
          {t('auth.reset.save')}
        </Button>
      </form>
    </div>
  )
}
