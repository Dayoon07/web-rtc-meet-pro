import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { chatApi, chatKeys, type ChatCategory, type ChatMessage, type ChatRoom, useChatCategoriesQuery, useChatRoomsQuery } from '@/entities/chat'
import { hasTeamPermission, useTeamQuery, useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { toastError } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { useDialog } from '@/shared/lib/use-dialog'
import { useChatAddress } from './address'

/**
 * 채팅 상태: 주소의 팀·방, 방 목록·카테고리, 만들기 창들 (채팅 화면과 앱 사이드바의 방 목록이 각각 쓴다).
 * 실시간 이벤트(useChatRealtime)는 앱 틀(AppLayout)에서 한 번만 받는다.
 * 화면 조각들은 이 값을 WorkspaceContext 로 받아 쓴다 (props 로 줄줄이 넘기지 않게)
 */
export function useChatWorkspace() {
  const { t } = useTranslation()
  const address = useChatAddress()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const { data: teams, isPending: teamsPending } = useTeamsQuery()
  const activeTeamId = address.teamId ?? teams?.[0]?.id ?? 0
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
    /** 채널·카테고리를 만들고 고칠 수 있는지 (워크스페이스 OWNER·ADMIN) */
    canManage: hasTeamPermission(team?.team, 'CREATE_GROUP_ROOM'),
    goTeam,
    openRoom,
    backToList,
    deleteCategory,
    dialogs: {
      /** 새 1:1 채팅 → 열린 방으로 */
      direct: useDialog<undefined, ChatRoom>(openRoom),
      /** 새 채널 (data = 미리 고를 카테고리) → 만든 방으로 */
      channel: useDialog<number | null, ChatRoom>(openRoom),
      /** 카테고리 만들기·이름 바꾸기 (data = 고칠 카테고리) */
      category: useDialog<ChatCategory | null>(),
      /** 메시지 검색 → 고른 메시지가 있는 방으로 가서 그 메시지로 */
      search: useDialog<undefined, ChatMessage>((m) => navigate(`${ROUTES.chat(activeTeamId, m.roomId)}?msg=${m.id}`)),
    },
  }
}

export type ChatWorkspaceModel = ReturnType<typeof useChatWorkspace>
