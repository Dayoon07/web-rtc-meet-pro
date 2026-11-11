import { useSyncExternalStore } from 'react'
import { voiceSession, type VoiceState } from '../model/voice-session'

/** 지금 채팅 음성 상태 (화면을 옮겨도 이어진다) */
export const useVoice = (): VoiceState => useSyncExternalStore(voiceSession.subscribe, voiceSession.getState)
