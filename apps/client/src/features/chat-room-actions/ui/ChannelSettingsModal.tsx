import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { errorMessage } from '@/shared/api'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'
import { CategorySelect } from './CategorySelect'

/** 채널 설정: 이름 바꾸기, 다른 카테고리로 옮기기 — 워크스페이스 OWNER·ADMIN */
export function ChannelSettingsModal({ room, dialog }: { room: ChatRoom; dialog: Dialog }) {
  const { t } = useTranslation()
  const { open, close: onClose } = dialog
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  useEffect(() => {
    if (open) {
      setName(room.name ?? '')
      setCategoryId(room.categoryId)
    }
  }, [open, room])

  const save = useMutation({
    mutationFn: () => chatApi.updateChannel(room.id, { name: name.trim(), categoryId, moveCategory: true }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      onClose()
    },
  })
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) save.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title={t('chat.channelSettings')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="channel-form" loading={save.isPending} disabled={!name.trim()}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="channel-form" onSubmit={onSubmit} className="flex flex-col gap-4">
        <Field label={t('chat.groupName')} htmlFor="channel-name" required>
          <Input id="channel-name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <CategorySelect teamId={room.teamId} field={{ id: 'channel-category', value: categoryId, onChange: setCategoryId }} />
        <FieldError>{errorMessage(save.error)}</FieldError>
      </form>
    </Modal>
  )
}
