import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { useSessionStore } from '@/entities/session'
import { errorMessage } from '@/shared/api'
import { Button, Divider, Field, FieldError, Input } from '@/shared/ui'
import { authApi } from '../api/auth-api'
import { GoogleButton } from './GoogleButton'

export function LoginForm({ redirectTo = '/app/chat' }: { redirectTo?: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const setTokens = useSessionStore((s) => s.setTokens)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  const login = useMutation({
    mutationFn: () => authApi.login({ email, password }),
    onSuccess: (tokens) => {
      queryClient.clear()
      setTokens(tokens)
      navigate(redirectTo, { replace: true })
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    login.mutate()
  }

  return (
    <div className="flex flex-col gap-6">
      <GoogleButton />
      <Divider label={t('auth.login.emailDivider')} />
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Field label={t('auth.fields.email')} htmlFor="login-email">
          <Input
            id="login-email"
            type="email"
            autoComplete="email"
            placeholder={t('auth.fields.emailPlaceholder')}
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label={t('auth.fields.password')} htmlFor="login-password">
          <div className="relative">
            <Input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder={t('auth.fields.passwordPlaceholder')}
              required
              className="pr-11"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={t(showPassword ? 'auth.fields.hidePassword' : 'auth.fields.showPassword')}
              className="absolute top-1/2 right-3 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
            >
              {showPassword ? <EyeOff className="size-4.5" /> : <Eye className="size-4.5" />}
            </button>
          </div>
        </Field>
        <Link to="/forgot-password" className="-mt-2 self-end text-sm font-medium text-brand-700 hover:underline dark:text-brand-300">
          {t('auth.login.forgot')}
        </Link>
        <FieldError>{errorMessage(login.error)}</FieldError>
        <Button type="submit" size="lg" loading={login.isPending} disabled={!email || !password}>
          {t('auth.login.submit')}
        </Button>
      </form>
    </div>
  )
}
