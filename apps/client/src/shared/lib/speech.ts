import { useEffect, useRef, useState } from 'react'

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

export const speechLang = (lng: string) =>
  ({ ko: 'ko-KR', en: 'en-US', ja: 'ja-JP', zh: 'zh-CN' } as Record<string, string>)[lng.slice(0, 2)] ?? lng

type Options = {
  enabled: boolean
  lang: string
  onFinal: (text: string) => void
  onInterim?: (text: string) => void
}

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
