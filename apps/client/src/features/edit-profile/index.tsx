import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import {
  draftFromMe,
  type Me,
  ProfileFields,
  type ProfileErrors,
  toProfilePayload,
  userApi,
  userKeys,
  validateProfile,
} from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { AvatarPicker, Button, Checkbox, FieldError } from '@/shared/ui'

export function ProfileImageSection({ me }: { me: Me }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const onSuccess = (updated: Me) => queryClient.setQueryData(userKeys.me, updated)

  const upload = useMutation({
    mutationFn: userApi.uploadProfileImage,
    onSuccess: (m) => {
      onSuccess(m)
      toast.success(t('settings.photoChanged'))
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const remove = useMutation({ mutationFn: userApi.removeProfileImage, onSuccess })

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row sm:gap-6">
      <AvatarPicker src={me.profileImageUrl} name={me.nickname} onPick={(f) => upload.mutate(f)} busy={upload.isPending} />
      <div className="flex flex-col items-center gap-2 sm:items-start">
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.photoHint')}</p>
        {me.profileImageUrl && (
          <Button variant="ghost" size="sm" icon={<Trash2 className="size-4" />} loading={remove.isPending} onClick={() => remove.mutate()}>
            {t('settings.photoRemove')}
          </Button>
        )}
      </div>
    </div>
  )
}

export function EditProfileForm({ me }: { me: Me }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [profile, setProfile] = useState(() => draftFromMe(me))
  const [marketing, setMarketing] = useState(me.marketingAgreed)
  const [errors, setErrors] = useState<ProfileErrors>({})

  const save = useMutation({
    mutationFn: () => userApi.updateProfile({ ...toProfilePayload(profile), marketingAgreed: marketing }),
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
