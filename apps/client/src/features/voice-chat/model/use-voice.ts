import { useSyncExternalStore } from 'react'
import { voiceSession, type VoiceState } from '../model/voice-session'

export const useVoice = (): VoiceState => useSyncExternalStore(voiceSession.subscribe, voiceSession.getState)
