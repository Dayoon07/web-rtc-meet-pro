import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { teamApi, teamKeys } from '@/entities/team'
import { ROUTES } from '@/shared/config/routes'

/** 팀 만들기 입력값과 보내기. 만들면 그 팀 채팅으로 간다 (onCreated: 창 닫기 등) */
export function useCreateTeam(onCreated?: () => void) {
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
      onCreated?.()
      navigate(ROUTES.chat(team.id))
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) create.mutate()
  }

  return { name, setName, description, setDescription, create, submit }
}
