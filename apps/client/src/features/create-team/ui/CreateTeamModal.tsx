import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { teamApi, teamKeys } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

export function CreateTeamModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const create = useMutation({
    mutationFn: () => teamApi.create({ name: name.trim(), description: description.trim() || undefined }),
    onSuccess: (team) => {
      void queryClient.invalidateQueries({ queryKey: teamKeys.all })
      toast.success(t('teams.create.created', { name: team.name }))
      setName('')
      setDescription('')
      onClose()
      navigate(ROUTES.team(team.id))
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
      title={t('teams.create.title')}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="create-team-form" loading={create.isPending} disabled={!name.trim()}>
            {t('teams.create.submit')}
          </Button>
        </>
      }
    >
      <form id="create-team-form" onSubmit={onSubmit} className="flex flex-col gap-4">
        <Field label={t('teams.create.name')} htmlFor="team-name" required>
          <Input id="team-name" autoFocus maxLength={100} placeholder={t('teams.create.namePlaceholder')} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label={t('teams.create.description')} htmlFor="team-desc" hint={t('common.optional')}>
          <Input id="team-desc" maxLength={500} placeholder={t('teams.create.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <p className="text-xs text-gray-500 dark:text-gray-400">{t('teams.create.freeHint')}</p>
        <FieldError>{errorMessage(create.error)}</FieldError>
      </form>
    </Modal>
  )
}
