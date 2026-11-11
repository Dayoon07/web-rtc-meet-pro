import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { draftFromMe, type Me, ProfileFields, type ProfileErrors, toProfileRequest, userApi, userKeys, validateProfile } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { Button, Checkbox, FieldError } from '@/shared/ui'

export function EditProfileForm({ me }: { me: Me }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [profile, setProfile] = useState(() => draftFromMe(me))
  const [marketing, setMarketing] = useState(me.marketingAgreed)
  const [errors, setErrors] = useState<ProfileErrors>({})

  const save = useMutation({
    mutationFn: () => userApi.updateProfile({ ...toProfileRequest(profile), marketingAgreed: marketing }),
    onSuccess: (updated) => {
      queryClient.setQueryData(userKeys.me, updated)
      toast.success(t('settings.saved'))
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const next = validateProfile(profile, t)
    setErrors(next)
    if (!Object.keys(next).length) save.mutate()
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-6" noValidate>
      <ProfileFields value={profile} onChange={setProfile} errors={errors} disabled={save.isPending} />
      <Checkbox label={t('settings.marketing')} checked={marketing} onChange={(e) => setMarketing(e.target.checked)} />
      <FieldError>{errorMessage(save.error)}</FieldError>
      <div className="flex justify-end">
        <Button type="submit" loading={save.isPending}>
          {t('settings.saveChanges')}
        </Button>
      </div>
    </form>
  )
}
