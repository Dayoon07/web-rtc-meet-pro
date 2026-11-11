import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { useTeamQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'
import { useMemberSelection } from '../model/use-member-selection'
import { CategorySelect } from './CategorySelect'
import { MemberChecklist } from './MemberList'

/** 채널(그룹 채팅방) 만들기 — 워크스페이스 OWNER·ADMIN. 카테고리(부서)를 고르면 그 안에 */
export function CreateGroupRoomModal({
  teamId,
  dialog,
}: {
  teamId: number
  /** data = 미리 고를 카테고리 (카테고리 옆 + 로 열면), done(방) = 만든 방으로 가기 */
  dialog: Dialog<number | null, ChatRoom>
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: team } = useTeamQuery(teamId)
  const { data: me } = useMeQuery()
  const members = (team?.members ?? []).filter((m) => m.userId !== me?.id)
  const { open, data: defaultCategoryId, close: onClose } = dialog
  const [name, setName] = useState('')
  const selection = useMemberSelection()
  const [categoryId, setCategoryId] = useState<number | null>(defaultCategoryId ?? null)
  useEffect(() => {
    if (open) setCategoryId(defaultCategoryId ?? null)
  }, [open, defaultCategoryId])

  const create = useMutation({
    mutationFn: () => chatApi.createGroup(teamId, name.trim(), [...selection.selected], categoryId),
    onSuccess: (room) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
      setName('')
      selection.reset()
      dialog.done(room)
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) create.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('chat.newGroup')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="group-room-form" loading={create.isPending} disabled={!name.trim()}>
            {t('common.create')}
          </Button>
        </>
      }
    >
      <form id="group-room-form" onSubmit={onSubmit} className="flex flex-col gap-4">
        <Field label={t('chat.groupName')} htmlFor="group-name" required>
          <Input id="group-name" autoFocus maxLength={100} placeholder={t('chat.groupNamePlaceholder')} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <CategorySelect teamId={teamId} field={{ id: 'group-category', value: categoryId, onChange: setCategoryId }} />
        <div>
          <p className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
            {t('chat.groupMembers')} <span className="text-gray-400">({selection.selected.size})</span>
          </p>
          <MemberChecklist members={members} selection={selection} />
        </div>
        <FieldError>{errorMessage(create.error)}</FieldError>
      </form>
    </Modal>
  )
}
