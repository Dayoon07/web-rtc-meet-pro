import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type Me, userApi, userKeys } from '@/entities/user'
import { toastError } from '@/shared/api'

export function useQuietHours(me: Me) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [enabled, setEnabled] = useState(!!me.dndStart)
  const [start, setStart] = useState(me.dndStart ?? '22:00')
  const [end, setEnd] = useState(me.dndEnd ?? '08:00')

  const save = useMutation({
    mutationFn: () =>
      userApi.updateQuietHours(
        enabled ? { start, end, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone } : { start: null, end: null, timeZone: null },
      ),
    onSuccess: (next) => {
      queryClient.setQueryData(userKeys.me, next)
      toast.success(t('notifySettings.quiet.saved'))
    },
    onError: (e) => toastError(e),
  })

  const changed = enabled !== !!me.dndStart || (enabled && (start !== me.dndStart || end !== me.dndEnd))
  return { enabled, setEnabled, start, setStart, end, setEnd, changed, saving: save.isPending, save: () => save.mutate() }
}
