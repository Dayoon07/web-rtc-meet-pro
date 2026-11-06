import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { sessionApi, useSessionStore } from '@/entities/session'

export function useLogout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return async () => {
    const { refreshToken, clear } = useSessionStore.getState()
    if (refreshToken) await sessionApi.logout(refreshToken).catch(() => undefined)
    clear()
    queryClient.clear()
    navigate('/', { replace: true })
    toast(t('auth.loggedOut'))
  }
}
