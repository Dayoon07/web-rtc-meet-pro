import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { useTeamQuery } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Button, FieldError, Modal } from '@/shared/ui'
import { useMemberSelection } from '../model/use-member-selection'
import { MemberChecklist } from './MemberList'

export function InviteToRoomModal({ room, dialog }: { room: ChatRoom; dialog: Dialog }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { open, close: onClose } = dialog
  const { data: team } = useTeamQuery(room.teamId)
  const selection = useMemberSelection()
  const inRoom = new Set(room.members.map((m) => m.id))
  const candidates = (team?.members ?? []).filter((m) => !inRoom.has(m.userId))

  const invite = useMutation({
    mutationFn: () => chatApi.invite(room.id, [...selection.selected]),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      void queryClient.invalidateQueries({ queryKey: chatKeys.messages(room.id) })
      selection.reset()
      onClose()
    },
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('chat.inviteTitle')}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button loading={invite.isPending} disabled={!selection.selected.size} onClick={() => invite.mutate()}>
            {t('common.invite')} {selection.selected.size > 0 && `(${selection.selected.size})`}
          </Button>
        </>
      }
    >
      {candidates.length ? (
        <MemberChecklist members={candidates} selection={selection} />
      ) : (
        <p className="py-6 text-center text-sm text-gray-400">{t('chat.allInRoom')}</p>
      )}
      <FieldError>{errorMessage(invite.error)}</FieldError>
    </Modal>
  )
}
