import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatCategory } from '@/entities/chat'
import { errorMessage } from '@/shared/api'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'

export function CategoryModal({ teamId, dialog }: { teamId: number; dialog: Dialog<ChatCategory | null> }) {
  const { t } = useTranslation()
  const { open, data: category, close: onClose } = dialog
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  useEffect(() => {
    if (open) setName(category?.name ?? '')
  }, [open, category])

  const save = useMutation({
    mutationFn: () => (category ? chatApi.renameCategory(category.id, name.trim()) : chatApi.createCategory(teamId, name.trim())),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.categories(teamId) })
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
      title={category ? t('chat.renameCategory') : t('chat.newCategory')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="category-form" loading={save.isPending} disabled={!name.trim()}>
            {category ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <form id="category-form" onSubmit={onSubmit} className="flex flex-col gap-3">
        <Field label={t('chat.categoryName')} htmlFor="category-name" required hint={t('chat.categoryNameHint')}>
          <Input id="category-name" autoFocus maxLength={50} placeholder={t('chat.categoryPlaceholder')} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <FieldError>{errorMessage(save.error)}</FieldError>
      </form>
    </Modal>
  )
}
