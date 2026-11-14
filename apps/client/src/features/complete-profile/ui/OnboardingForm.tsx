import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { draftFromMe, emptyAgreements, type Me, ProfileFields, type ProfileErrors, requiredAgreed, TermsAgreement, toProfileRequest, userApi, userKeys, validateProfile } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { Button, FieldError } from '@/shared/ui'
import { returnToPath } from '@/shared/lib/return-to'

export function OnboardingForm({ me }: { me: Me }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [profile, setProfile] = useState(() => draftFromMe(me))
  const [errors, setErrors] = useState<ProfileErrors>({})
  const [agreements, setAgreements] = useState(emptyAgreements)
  const [agreementError, setAgreementError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => userApi.updateProfile({ ...toProfileRequest(profile), ...agreements }),
    onSuccess: (updated) => {
      queryClient.setQueryData(userKeys.me, updated)
      toast.success(t('auth.onboarding.done'))
      navigate(returnToPath(), { replace: true })
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const next = validateProfile(profile, t)
    setErrors(next)
    setAgreementError(requiredAgreed(agreements) ? null : t('auth.validation.termsRequired'))
    if (Object.keys(next).length || !requiredAgreed(agreements)) return
    save.mutate()
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-6" noValidate>
      <ProfileFields value={profile} onChange={setProfile} errors={errors} disabled={save.isPending} />
      <TermsAgreement field={{ value: agreements, onChange: setAgreements, error: agreementError }} />
      <FieldError>{errorMessage(save.error)}</FieldError>
      <Button type="submit" size="lg" loading={save.isPending}>
        {t('auth.onboarding.submit')}
      </Button>
    </form>
  )
}
