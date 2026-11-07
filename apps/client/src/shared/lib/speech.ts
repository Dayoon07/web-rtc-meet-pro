import { useEffect, useRef, useState } from 'react'

/** 브라우저 받아쓰기 (Web Speech API). Chrome·Edge·Safari 에만 있다 (Firefox·안드로이드 앱 화면은 없음) */
type Recognition = {
  lang: string
  continuous: boolean
  interimResults: boolean
  start(): void
  stop(): void
  abort(): void
  onresult: ((e: { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
}

const Ctor = (): (new () => Recognition) | undefined => {
  const w = window as unknown as { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition
}

export const speechSupported = () => typeof window !== 'undefined' && !!Ctor()

/** i18n 언어 → 받아쓰기 언어 */
export const speechLang = (lng: string) =>
  ({ ko: 'ko-KR', en: 'en-US', ja: 'ja-JP', zh: 'zh-CN' } as Record<string, string>)[lng.slice(0, 2)] ?? lng

type Options = {
  /** 켜져 있는 동안만 받아쓴다 (AI 회의록이 켜져 있고 마이크가 켜져 있을 때) */
  enabled: boolean
  lang: string
  /** 한 문장이 끝남 */
  onFinal: (text: string) => void
  /** 말하는 중인 문장 (자막용) */
  onInterim?: (text: string) => void
}

/**
 * 내 마이크를 받아쓴다. 브라우저가 조용하면 저절로 멈추므로 켜져 있는 동안은 다시 시작한다.
 * 마이크 권한이 없거나 브라우저가 지원하지 않으면 error 에 남긴다.
 */
export function useLiveTranscriber({ enabled, lang, onFinal, onInterim }: Options) {
  const [error, setError] = useState<'unsupported' | 'denied' | null>(null)
  const [listening, setListening] = useState(false)
  const handlers = useRef({ onFinal, onInterim })
  handlers.current = { onFinal, onInterim }

  useEffect(() => {
    if (!enabled) return
    const C = Ctor()
    if (!C) {
      setError('unsupported')
      return
    }
    let active = true
    let rec: Recognition | null = null
    let restartTimer: ReturnType<typeof setTimeout> | undefined

    const begin = () => {
      if (!active) return
      rec = new C()
      rec.lang = lang
      rec.continuous = true
      rec.interimResults = true
      rec.onresult = (e) => {
        let interim = ''
        for (let i = e.resultIndex; i < e.results.length; i++) {
          const r = e.results[i]
          const text = r[0].transcript.trim()
          if (!text) continue
          if (r.isFinal) handlers.current.onFinal(text)
          else interim += (interim ? ' ' : '') + text
        }
        handlers.current.onInterim?.(interim)
      }
      rec.onerror = (e) => {
        if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
          active = false
          setError('denied')
        }
      }
      rec.onend = () => {
        setListening(false)
        handlers.current.onInterim?.('')
        // 조용해서 멈췄거나 네트워크가 잠깐 끊김 → 켜져 있으면 다시
        if (active) restartTimer = setTimeout(begin, 300)
      }
      try {
        rec.start()
        setListening(true)
        setError(null)
      } catch {
        restartTimer = setTimeout(begin, 1000)
      }
    }
    begin()
    return () => {
      active = false
      clearTimeout(restartTimer)
      if (rec) {
        rec.onend = null
        rec.abort()
      }
      setListening(false)
      handlers.current.onInterim?.('')
    }
  }, [enabled, lang])

  return { listening, error }
}
