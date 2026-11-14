import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { chatApi, chatKeys, type ChatCategory, type ChatMessage, type ChatRoom, useChatCategoriesQuery, useChatRoomsQuery } from '@/entities/chat'
import { hasTeamPermission, useCurrentTeamId, useTeamQuery, useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { toastError } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { useDialog } from '@/shared/lib/use-dialog'
import { useChatAddress } from './address'

export function useChatWorkspace() {
  const { t } = useTranslation()
  const address = useChatAddress()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const { data: teams, isPending: teamsPending } = useTeamsQuery()
  const activeTeamId = useCurrentTeamId(address.teamId) ?? 0
  const activeRoomId = address.roomId
  const { data: team } = useTeamQuery(activeTeamId)
  const { data: rooms = [], isPending: roomsPending } = useChatRoomsQuery(activeTeamId)
  const { data: categories = [] } = useChatCategoriesQuery(activeTeamId)

  const goTeam = (teamId: number) => navigate(ROUTES.chat(teamId))
  const openRoom = (room: ChatRoom) => navigate(ROUTES.chat(room.teamId, room.id))
  const backToList = () => goTeam(activeTeamId)

  const deleteCategory = (category: ChatCategory) => {
    if (!window.confirm(t('chat.deleteCategoryConfirm', { name: category.name }))) return
    chatApi.deleteCategory(category.id).then(() => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.categories(activeTeamId) })
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(activeTeamId) })
    }, toastError)
  }

  return {
    myId: me?.id,
    me,
    teams: teams ?? [],
    teamsPending,
    activeTeamId,
    team,
    rooms,
    roomsPending,
    categories,
    activeRoom: rooms.find((r) => r.id === activeRoomId),
    activeRoomId,
    canManage: hasTeamPermission(team?.team, 'CREATE_GROUP_ROOM'),
    goTeam,
    openRoom,
    backToList,
    deleteCategory,
    dialogs: {
      direct: useDialog<undefined, ChatRoom>(openRoom),
      channel: useDialog<number | null, ChatRoom>(openRoom),
      category: useDialog<ChatCategory | null>(),
      search: useDialog<undefined, ChatMessage>((m) =>
        navigate(`${ROUTES.chat(activeTeamId, m.roomId)}?${m.threadRootId ? `thread=${m.threadRootId}` : `msg=${m.id}`}`),
      ),
    },
  }
}

export type ChatWorkspaceModel = ReturnType<typeof useChatWorkspace>
