import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { useTeamQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { toastError } from '@/shared/api'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Modal } from '@/shared/ui'
import { MemberPicker } from './MemberList'

export function StartDirectChatModal({ teamId, dialog }: { teamId: number; dialog: Dialog<undefined, ChatRoom> }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: team } = useTeamQuery(teamId)
  const { data: me } = useMeQuery()
  const others = (team?.members ?? []).filter((m) => m.userId !== me?.id)

  const openDirect = useMutation({
    mutationFn: (userId: number) => chatApi.openDirect(teamId, userId),
    onSuccess: (room) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
      dialog.done(room)
    },
    onError: (e) => toastError(e),
  })

  return (
    <Modal open={dialog.open} onClose={dialog.close} title={t('chat.newDirect')} size="sm">
      <p className="mb-3 text-sm text-gray-500 dark:text-gray-400">{t('chat.pickMember')}</p>
      {others.length ? (
        <MemberPicker members={others} onPick={(m) => openDirect.mutate(m.userId)} />
      ) : (
        <p className="py-6 text-center text-sm text-gray-400">{t('chat.noOtherMembers')}</p>
      )}
    </Modal>
  )
}
