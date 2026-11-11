import { Headphones, HeadphoneOff, MicOff, Mic, MonitorUp, PhoneOff, RotateCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { errorMessage } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { voiceSession } from '../model/voice-session'
import { ROUTES } from '@/shared/config/routes'
import { barButton } from '../lib/voice-chat'
import { useVoice } from '../model/use-voice'
import { SpeakingAvatar } from './SpeakingAvatar'

/**
 * 채팅 사이드바 아래 "음성 연결됨" 막대 (디스코드처럼): 들어와 있는 사람(말하면 초록 테두리), 마이크·소리 끄기,
 * 화상 화면으로 열기(카메라·화면 공유), 나가기.
 */
export function VoiceBar({ me }: { me?: { nickname: string; profileImageUrl: string | null } }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const voice = useVoice()
  if (voice.status === 'idle') return null

  const openVideo = () => {
    const { meetingId, teamId, roomId } = voice
    if (!meetingId) return
    // 같은 사람이 두 번 들어가지 않게 채팅 음성은 끊고 화상 화면으로
    voiceSession.leave()
    navigate(`${ROUTES.meet(meetingId)}?voice=1&back=${encodeURIComponent(ROUTES.chat(teamId ?? undefined, roomId ?? undefined))}`)
  }

  return (
    <div className="border-t border-gray-200 bg-gray-50 px-3 py-2.5 dark:border-gray-800 dark:bg-gray-900/60">
      <div className="flex items-center gap-2">
        <div className="min-w-0 flex-1">
          <p
            className={cn(
              'flex items-center gap-1.5 text-xs font-semibold',
              voice.status === 'connected' && 'text-green-700 dark:text-green-400',
              voice.status === 'connecting' && 'text-gray-500',
              voice.status === 'failed' && 'text-red-600 dark:text-red-400',
            )}
          >
            <span className={cn('size-2 rounded-full', voice.status === 'connected' ? 'bg-green-500' : voice.status === 'failed' ? 'bg-red-500' : 'animate-pulse bg-gray-400')} />
            {t(`voice.${voice.status}`)}
          </p>
          <p className="truncate text-xs text-gray-500" title={voice.status === 'failed' ? (errorMessage(voice.error) ?? undefined) : undefined}>
            # {voice.roomName}
            {voice.listenOnly && ` · ${t('voice.listenOnly')}`}
          </p>
        </div>
        {voice.status === 'failed' ? (
          <button
            type="button"
            className={barButton}
            aria-label={t('voice.retry')}
            title={t('voice.retry')}
            onClick={() => voice.roomId && voice.teamId && void voiceSession.join({ id: voice.roomId, teamId: voice.teamId, name: voice.roomName })}
          >
            <RotateCw className="size-4" />
          </button>
        ) : (
          <>
            <button
              type="button"
              className={cn(barButton, voice.muted && 'text-red-600 dark:text-red-400')}
              aria-label={t(voice.muted ? 'voice.unmute' : 'voice.mute')}
              title={t(voice.muted ? 'voice.unmute' : 'voice.mute')}
              disabled={voice.listenOnly || voice.status !== 'connected'}
              onClick={() => void voiceSession.toggleMute()}
            >
              {voice.muted || voice.listenOnly ? <MicOff className="size-4" /> : <Mic className="size-4" />}
            </button>
            <button
              type="button"
              className={cn(barButton, voice.deafened && 'text-red-600 dark:text-red-400')}
              aria-label={t(voice.deafened ? 'voice.undeafen' : 'voice.deafen')}
              title={t(voice.deafened ? 'voice.undeafen' : 'voice.deafen')}
              onClick={() => voiceSession.toggleDeafen()}
            >
              {voice.deafened ? <HeadphoneOff className="size-4" /> : <Headphones className="size-4" />}
            </button>
            <button type="button" className={barButton} aria-label={t('voice.openVideo')} title={t('voice.openVideo')} disabled={!voice.meetingId} onClick={openVideo}>
              <MonitorUp className="size-4" />
            </button>
          </>
        )}
        <button
          type="button"
          className={cn(barButton, 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10')}
          aria-label={t('voice.leave')}
          title={t('voice.leave')}
          onClick={() => voiceSession.leave()}
        >
          <PhoneOff className="size-4" />
        </button>
      </div>

      {voice.status === 'connected' && (
        <ul className="mt-2 flex flex-wrap gap-1.5">
          {me && (
            <li title={`${me.nickname} (${t('voice.you')})`}>
              <SpeakingAvatar person={{ name: me.nickname, src: me.profileImageUrl, speaking: voice.selfSpeaking, muted: voice.muted || voice.listenOnly }} />
            </li>
          )}
          {voice.peers.map((p) => (
            <li key={p.peerId} title={p.name + (p.sharing ? ` · ${t('voice.sharing')}` : '')}>
              <SpeakingAvatar person={{ name: p.name, src: p.avatar, speaking: p.speaking, muted: p.muted }} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
