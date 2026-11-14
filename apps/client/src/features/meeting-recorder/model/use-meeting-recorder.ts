import { useCallback, useEffect, useRef, useState } from 'react'
import { meetingApi } from '@/entities/meeting'

const SLICE_MS = 10_000
const MIME_TYPES = ['video/webm;codecs=vp8,opus', 'video/webm;codecs=vp9,opus', 'video/webm']

type Status = 'idle' | 'starting' | 'recording' | 'stopping'

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
      display = await navigator.mediaDevices.getDisplayMedia({
        video: { frameRate: 15 },
        audio: true,
        preferCurrentTab: true,
        selfBrowserSurface: 'include',
        systemAudio: 'include',
      } as DisplayMediaStreamOptions)
    } catch {
      setStatus('idle')
      return
    }

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
      void queue.current.then(() => meetingApi.finishRecording(meetingId, recordingId, seconds).catch(setError)).finally(() => {
        recorder.current = null
        setStatus('idle')
      })
    }
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

  useEffect(() => () => stop(), [stop])

  return { status, recording: status === 'recording' || status === 'stopping', start, stop, error }
}
