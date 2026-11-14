import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useSessionStore } from '@/entities/session'
import {
  emptyAgreements,
  emptyProfileDraft,
  ProfileFields,
  type ProfileErrors,
  requiredAgreed,
  TermsAgreement,
  toProfileRequest,
  userApi,
  validateProfile,
} from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { AvatarPicker, Button, Divider, Field, FieldError, Input } from '@/shared/ui'
import { authApi } from '../api/auth-api'
import { GoogleButton } from './GoogleButton'
import { returnToPath } from '@/shared/lib/return-to'

function Stepper({ step, labels }: { step: number; labels: string[] }) {
  return (
    <ol className="flex items-center gap-2">
      {labels.map((label, i) => {
        const done = i < step
        const active = i === step
        return (
          <li key={label} className="flex flex-1 items-center gap-2">
            <span
              className={cn(
                'flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-bold',
                done && 'bg-brand-600 text-white',
                active && 'bg-brand-100 text-brand-700 ring-2 ring-brand-600 dark:bg-brand-500/20 dark:text-brand-200',
                !done && !active && 'bg-gray-100 text-gray-400 dark:bg-gray-800',
              )}
            >
              {done ? <Check className="size-4" strokeWidth={3} /> : i + 1}
            </span>
            <span className={cn('text-sm font-medium', active ? 'text-gray-900 dark:text-white' : 'text-gray-400')}>{label}</span>
            {i < labels.length - 1 && <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />}
          </li>
        )
      })}
    </ol>
  )
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function SignupForm() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const setTokens = useSessionStore((s) => s.setTokens)

  const [step, setStep] = useState(0)
  const [account, setAccount] = useState({ email: '', password: '', confirm: '' })
  const [accountErrors, setAccountErrors] = useState<Partial<Record<keyof typeof account, string>>>({})
  const [profile, setProfile] = useState(emptyProfileDraft)
  const [profileErrors, setProfileErrors] = useState<ProfileErrors>({})
  const [agreements, setAgreements] = useState(emptyAgreements)
  const [agreementError, setAgreementError] = useState<string | null>(null)
  const [image, setImage] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)

  useEffect(() => {
    if (!image) return setPreview(null)
    const url = URL.createObjectURL(image)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [image])

  const checkEmail = useMutation({ mutationFn: () => authApi.emailAvailable(account.email.trim()) })

  const signup = useMutation({
    mutationFn: async () => {
      const tokens = await authApi.signup({ email: account.email.trim(), password: account.password, ...toProfileRequest(profile), ...agreements })
      queryClient.clear()
      setTokens(tokens)
      if (image) {
        await userApi.uploadProfileImage(image).catch(() => toast.error(t('auth.signup.photoUploadFailed')))
      }
    },
    onSuccess: () => {
      toast.success(t('auth.signup.welcome'))
      navigate(returnToPath(), { replace: true })
    },
  })

  const submitAccount = async (e: FormEvent) => {
    e.preventDefault()
    const errors: typeof accountErrors = {}
    if (!EMAIL_RE.test(account.email.trim())) errors.email = t('auth.validation.email')
    if (account.password.length < 8) errors.password = t('auth.validation.passwordLength')
    else if (!/[A-Za-z]/.test(account.password) || !/\d/.test(account.password)) errors.password = t('auth.validation.passwordMix')
    if (account.confirm !== account.password) errors.confirm = t('auth.validation.passwordMismatch')
    setAccountErrors(errors)
    if (Object.keys(errors).length) return

    const { available, signInMethod } = await checkEmail.mutateAsync()
    if (!available) return setAccountErrors({ email: t(signInMethod === 'GOOGLE' ? 'auth.validation.emailTakenGoogle' : 'auth.validation.emailTaken') })
    setStep(1)
  }

  const submitProfile = (e: FormEvent) => {
    e.preventDefault()
    const errors = validateProfile(profile, t)
    setProfileErrors(errors)
    setAgreementError(requiredAgreed(agreements) ? null : t('auth.validation.termsRequired'))
    if (Object.keys(errors).length || !requiredAgreed(agreements)) return
    signup.mutate()
  }

  return (
    <div className="flex flex-col gap-7">
      <Stepper step={step} labels={[t('auth.signup.stepAccount'), t('auth.signup.stepProfile')]} />

      {step === 0 ? (
        <div className="flex flex-col gap-6">
          <GoogleButton mode="signup" />
          <Divider label={t('auth.signup.emailDivider')} />
          <form onSubmit={submitAccount} className="flex flex-col gap-4" noValidate>
            <Field label={t('auth.fields.email')} htmlFor="su-email" required error={accountErrors.email}>
              <Input
                id="su-email"
                type="email"
                autoComplete="email"
                placeholder={t('auth.fields.emailPlaceholder')}
                value={account.email}
                invalid={!!accountErrors.email}
                onChange={(e) => setAccount({ ...account, email: e.target.value })}
              />
            </Field>
            <Field label={t('auth.fields.password')} htmlFor="su-pw" required error={accountErrors.password} hint={t('auth.fields.passwordHint')}>
              <Input
                id="su-pw"
                type="password"
                autoComplete="new-password"
                value={account.password}
                invalid={!!accountErrors.password}
                onChange={(e) => setAccount({ ...account, password: e.target.value })}
              />
            </Field>
            <Field label={t('auth.fields.passwordConfirm')} htmlFor="su-pw2" required error={accountErrors.confirm}>
              <Input
                id="su-pw2"
                type="password"
                autoComplete="new-password"
                value={account.confirm}
                invalid={!!accountErrors.confirm}
                onChange={(e) => setAccount({ ...account, confirm: e.target.value })}
              />
            </Field>
            <FieldError>{errorMessage(checkEmail.error)}</FieldError>
            <Button type="submit" size="lg" loading={checkEmail.isPending}>
              {t('common.next')}
            </Button>
          </form>
        </div>
      ) : (
        <form onSubmit={submitProfile} className="flex flex-col gap-6" noValidate>
          <div className="flex items-center gap-5">
            <AvatarPicker src={preview} name={profile.nickname || profile.realName} onPick={setImage} />
            <div className="text-sm">
              <p className="font-semibold text-gray-900 dark:text-white">{t('auth.signup.photoTitle')}</p>
              <p className="text-gray-500 dark:text-gray-400">{t('auth.signup.photoHint')}</p>
              {image && (
                <button type="button" onClick={() => setImage(null)} className="mt-1 text-xs text-gray-500 underline hover:text-gray-800">
                  {t('auth.signup.photoRemove')}
                </button>
              )}
            </div>
          </div>

          <ProfileFields value={profile} onChange={setProfile} errors={profileErrors} disabled={signup.isPending} />
          <TermsAgreement field={{ value: agreements, onChange: setAgreements, error: agreementError }} />
          <FieldError>{errorMessage(signup.error)}</FieldError>

          <div className="flex gap-3">
            <Button type="button" variant="outline" size="lg" icon={<ArrowLeft className="size-4" />} onClick={() => setStep(0)}>
              {t('common.prev')}
            </Button>
            <Button type="submit" size="lg" className="flex-1" loading={signup.isPending}>
              {t('auth.signup.submit')}
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}
