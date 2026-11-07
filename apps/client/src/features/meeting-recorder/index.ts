import { useCallback, useEffect, useRef, useState } from 'react'
import { meetingApi } from '@/entities/meeting'

/** 10초마다 조각을 서버로 보낸다 (창을 닫아도 그때까지는 남는다) */
const SLICE_MS = 10_000
const MIME_TYPES = ['video/webm;codecs=vp8,opus', 'video/webm;codecs=vp9,opus', 'video/webm']

type Status = 'idle' | 'starting' | 'recording' | 'stopping'

/**
 * 주최자 브라우저 녹화: 회의 탭(다른 사람 영상·소리)을 화면 공유로 잡고, 내 마이크 소리를 섞어서 MediaRecorder 로 녹화한다.
 * 10초 조각을 순서대로 서버에 올리고(실패하면 3번까지 다시), 멈추면 마무리 요청을 보낸다.
 *
 * @param getMicTrack 내 마이크 트랙 (마이크를 끄면 트랙도 꺼져서 그동안은 조용하게 녹화된다)
 */
export function useMeetingRecorder(meetingId: number, getMicTrack: () => MediaStreamTrack | null) {
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<unknown>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const cleanup = useRef<(() => void) | null>(null)
  const queue = useRef<Promise<void>>(Promise.resolve())
  const startedAt = useRef(0)

  const upload = useCallback(
    (recordingId: number, seq: number, blob: Blob) => {
      queue.current = queue.current.then(async () => {
        for (let attempt = 0; attempt < 3; attempt++) {
          try {
            await meetingApi.uploadRecordingChunk(meetingId, recordingId, seq, blob)
            return
          } catch (e) {
            if (attempt === 2) setError(e)
            await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)))
          }
        }
      })
    },
    [meetingId],
  )

  const stop = useCallback(() => {
    const r = recorder.current
    if (!r || r.state === 'inactive') return
    setStatus('stopping')
    r.stop()
  }, [])

  const start = useCallback(async () => {
    if (status !== 'idle') return
    setError(null)
    setStatus('starting')
    let display: MediaStream | null = null
    try {
      // 회의 탭을 고르기 쉽게: 지금 탭을 먼저 보여 주고, 탭 소리(다른 사람 목소리)도 함께
      display = await navigator.mediaDevices.getDisplayMedia({
        video: { frameRate: 15 },
        audio: true,
        preferCurrentTab: true,
        selfBrowserSurface: 'include',
        systemAudio: 'include',
      } as DisplayMediaStreamOptions)
    } catch {
      setStatus('idle') // 고르기 창에서 취소
      return
    }

    // 소리 섞기: 탭 소리 + 내 마이크
    const audio = new AudioContext()
    const mix = audio.createMediaStreamDestination()
    const tabAudio = display.getAudioTracks()[0]
    if (tabAudio) audio.createMediaStreamSource(new MediaStream([tabAudio])).connect(mix)
    const mic = getMicTrack()
    if (mic) audio.createMediaStreamSource(new MediaStream([mic])).connect(mix)
    const stream = new MediaStream([...display.getVideoTracks(), ...mix.stream.getAudioTracks()])

    let recordingId: number
    try {
      recordingId = (await meetingApi.startRecording(meetingId)).id
    } catch (e) {
      display.getTracks().forEach((t) => t.stop())
      void audio.close()
      setError(e)
      setStatus('idle')
      return
    }

    const mimeType = MIME_TYPES.find((m) => MediaRecorder.isTypeSupported(m))
    const r = new MediaRecorder(stream, { mimeType, videoBitsPerSecond: 1_500_000, audioBitsPerSecond: 96_000 })
    let seq = 0
    r.ondataavailable = (e) => {
      if (e.data.size > 0) upload(recordingId, seq++, e.data)
    }
    r.onstop = () => {
      cleanup.current?.()
      const seconds = Math.round((Date.now() - startedAt.current) / 1000)
      // 마지막 조각까지 다 올린 뒤에 마무리
      void queue.current.then(() => meetingApi.finishRecording(meetingId, recordingId, seconds).catch(setError)).finally(() => {
        recorder.current = null
        setStatus('idle')
      })
    }
    // 브라우저 위쪽의 "공유 중지" 를 누르면 녹화도 멈춘다
    display.getVideoTracks()[0]?.addEventListener('ended', () => stop())
    cleanup.current = () => {
      display?.getTracks().forEach((t) => t.stop())
      void audio.close()
      cleanup.current = null
    }
    recorder.current = r
    startedAt.current = Date.now()
    r.start(SLICE_MS)
    setStatus('recording')
  }, [status, getMicTrack, meetingId, upload, stop])

  // 회의실을 나가면 녹화도 멈춘다 (받은 조각까지는 저장된다)
  useEffect(() => () => stop(), [stop])

  return { status, recording: status === 'recording' || status === 'stopping', start, stop, error }
}
