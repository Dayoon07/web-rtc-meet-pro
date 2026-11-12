import { useTranslation } from 'react-i18next'
import { errorMessage } from '@/shared/api'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'
import { useCreateTeam } from '../model/use-create-team'

/** 이름 · 설명 칸 (창과 시작 화면이 같이 쓴다) */
function TeamFields({ form }: { form: ReturnType<typeof useCreateTeam> }) {
  const { t } = useTranslation()
  return (
    <>
      <Field label={t('teams.create.name')} htmlFor="team-name" required>
        <Input id="team-name" autoFocus maxLength={100} placeholder={t('teams.create.namePlaceholder')} value={form.name} onChange={(e) => form.setName(e.target.value)} />
      </Field>
      <Field label={t('teams.create.description')} htmlFor="team-desc" hint={t('common.optional')}>
        <Input
          id="team-desc"
          maxLength={500}
          placeholder={t('teams.create.descriptionPlaceholder')}
          value={form.description}
          onChange={(e) => form.setDescription(e.target.value)}
        />
      </Field>
      <p className="text-xs text-gray-500 dark:text-gray-400">{t('teams.create.freeHint')}</p>
      <FieldError>{errorMessage(form.create.error)}</FieldError>
    </>
  )
}

/** 시작 화면 안에 바로 보이는 팀 만들기 */
export function CreateTeamForm() {
  const { t } = useTranslation()
  const form = useCreateTeam()
  return (
    <form onSubmit={form.submit} className="flex flex-col gap-4">
      <TeamFields form={form} />
      <Button type="submit" loading={form.create.isPending} disabled={!form.name.trim()}>
        {t('teams.create.submit')}
      </Button>
    </form>
  )
}

export function CreateTeamModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const form = useCreateTeam(onClose)
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
          <Button type="submit" form="create-team-form" loading={form.create.isPending} disabled={!form.name.trim()}>
            {t('teams.create.submit')}
          </Button>
        </>
      }
    >
      <form id="create-team-form" onSubmit={form.submit} className="flex flex-col gap-4">
        <TeamFields form={form} />
      </form>
    </Modal>
  )
}
