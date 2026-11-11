import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type Me, userApi, userKeys } from '@/entities/user'
import { toastError } from '@/shared/api'
import { AvatarPicker, Button } from '@/shared/ui'

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
    onError: (e) => toastError(e),
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
