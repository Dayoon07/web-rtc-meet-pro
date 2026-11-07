import { useNavigate, useParams } from 'react-router'
import { ChatWorkspace } from '@/widgets/chat-workspace'

/** /app/chat, /app/chat/:teamId, /app/chat/:teamId/:roomId */
export function ChatPage() {
  const params = useParams()
  const navigate = useNavigate()
  const teamId = params.teamId ? Number(params.teamId) : undefined
  const roomId = params.roomId ? Number(params.roomId) : undefined

  return (
    // 앱 레이아웃의 패딩을 지우고 상단 바(4rem) 아래를 꽉 채운다 (여백·카드 없이 화면에 붙임)
    <div className="-mx-4 -my-8 flex h-[calc(100dvh-4rem)] flex-col sm:-mx-6 lg:-mx-10">
      <ChatWorkspace
        teamId={teamId}
        roomId={roomId}
        onNavigate={(team, room) => navigate(room ? `/app/chat/${team}/${room}` : `/app/chat/${team}`)}
      />
    </div>
  )
}
