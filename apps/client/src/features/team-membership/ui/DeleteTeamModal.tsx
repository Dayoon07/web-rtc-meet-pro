import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { teamApi, type TeamSummary } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'
import type { Dialog } from '@/shared/lib/use-dialog'

export function DeleteTeamModal({ team, dialog }: { team: TeamSummary; dialog: Dialog }) {
  const { open, close: onClose } = dialog
  const { t } = useTranslation()
  const [typed, setTyped] = useState('')
  const remove = useMutation({ mutationFn: () => teamApi.remove(team.id), onSuccess: () => dialog.done() })
  const close = () => {
    setTyped('')
    remove.reset()
    onClose()
  }
  return (
    <Modal
      open={open}
      onClose={close}
      title={t('teams.actions.deleteTitle')}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" loading={remove.isPending} disabled={typed.trim() !== team.name} onClick={() => remove.mutate()}>
            {t('teams.actions.delete')}
          </Button>
        </>
      }
    >
      <p className="text-sm text-gray-600 dark:text-gray-300">{t('teams.actions.deleteBody')}</p>
      <Field label={t('teams.actions.deleteConfirmLabel', { name: team.name })} className="mt-4">
        <Input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={team.name} autoComplete="off" />
      </Field>
      <div className="mt-2">
        <FieldError>{errorMessage(remove.error)}</FieldError>
      </div>
    </Modal>
  )
}
