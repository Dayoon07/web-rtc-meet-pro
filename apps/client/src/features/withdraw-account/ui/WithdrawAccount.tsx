import { useMutation, useQueryClient } from '@tanstack/react-query'
import { UserX } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useSessionStore } from '@/entities/session'
import { type Me, userApi } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

export function WithdrawAccount({ me }: { me: Me }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [typed, setTyped] = useState('')
  const confirmWord = t('settings.withdraw.confirmWord')

  const withdraw = useMutation({
    mutationFn: () => userApi.withdraw(me.hasPassword ? password : undefined),
    onSuccess: () => {
      useSessionStore.getState().clear()
      queryClient.clear()
      navigate(ROUTES.home, { replace: true })
      toast.success(t('settings.withdraw.done'))
    },
  })
  const ready = me.hasPassword ? password.length > 0 : typed.trim() === confirmWord
  const close = () => {
    setOpen(false)
    setPassword('')
    setTyped('')
    withdraw.reset()
  }

  return (
    <>
      <Button variant="danger" icon={<UserX className="size-4" />} onClick={() => setOpen(true)}>
        {t('settings.withdraw.button')}
      </Button>
      <Modal
        open={open}
        onClose={close}
        title={t('settings.withdraw.modalTitle')}
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" loading={withdraw.isPending} disabled={!ready} onClick={() => withdraw.mutate()}>
              {t('settings.withdraw.button')}
            </Button>
          </>
        }
      >
        <p className="text-sm leading-relaxed text-gray-600 dark:text-gray-300">{t('settings.withdraw.body')}</p>
        <div className="mt-4">
          {me.hasPassword ? (
            <Field label={t('settings.withdraw.password')}>
              <Input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
          ) : (
            <Field label={t('settings.withdraw.confirmText', { word: confirmWord })}>
              <Input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={confirmWord} autoComplete="off" />
            </Field>
          )}
        </div>
        <div className="mt-2">
          <FieldError>{errorMessage(withdraw.error)}</FieldError>
        </div>
      </Modal>
    </>
  )
}
