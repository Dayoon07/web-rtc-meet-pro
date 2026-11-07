import type { ErrorCategory, ErrorSource } from './api'

/** 원인 분류: 이름, 무엇을 뜻하는지, 배지 색 */
export const CATEGORY: Record<ErrorCategory, { label: string; hint: string; tone: string }> = {
  DB_SCHEMA: {
    label: 'DB 구조',
    hint: '테이블·컬럼 구조와 코드가 맞지 않아요 (없는 테이블·컬럼, 길이 초과, 타입 불일치). db/schema.sql 과 엔티티를 확인하세요.',
    tone: 'bg-rose-100 text-rose-800 dark:bg-rose-500/15 dark:text-rose-300',
  },
  DB_CONSTRAINT: {
    label: 'DB 규칙 위반',
    hint: '중복·필수값·참조·체크 제약을 어겼어요. 서비스 코드가 저장 전에 확인해야 하는 규칙이 빠졌을 수 있어요.',
    tone: 'bg-orange-100 text-orange-800 dark:bg-orange-500/15 dark:text-orange-300',
  },
  DB_CONNECTION: {
    label: 'DB 연결',
    hint: 'DB 에 연결하지 못했어요 (DB 꺼짐, 계정·접속 정보, 커넥션 부족). 코드보다 서버 상태를 먼저 보세요.',
    tone: 'bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-300',
  },
  DB_ETC: {
    label: 'DB 기타',
    hint: '그 밖의 DB 오류예요. 메시지의 ORA 번호를 확인하세요.',
    tone: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300',
  },
  EXTERNAL: {
    label: '외부 연동',
    hint: '토스페이먼츠·구글·미디어 서버 같은 외부 서비스가 응답하지 않았어요.',
    tone: 'bg-sky-100 text-sky-800 dark:bg-sky-500/15 dark:text-sky-300',
  },
  CODE: {
    label: '서버 코드',
    hint: '서버 소스 코드 버그예요. 스택 트레이스의 맨 위 com.webrtcmeetpro 줄부터 보세요.',
    tone: 'bg-violet-100 text-violet-800 dark:bg-violet-500/15 dark:text-violet-300',
  },
  CLIENT: {
    label: '화면 코드',
    hint: '웹·앱 화면 코드에서 난 오류예요. 경로와 브라우저(User-Agent)를 함께 보세요.',
    tone: 'bg-fuchsia-100 text-fuchsia-800 dark:bg-fuchsia-500/15 dark:text-fuchsia-300',
  },
  NETWORK: {
    label: '네트워크',
    hint: '화면이 서버에 연결하지 못했어요. 그 시각 서버가 꺼져 있었거나 사용자 네트워크 문제일 수 있어요.',
    tone: 'bg-gray-200 text-gray-800 dark:bg-gray-500/20 dark:text-gray-300',
  },
}

export const SOURCE: Record<ErrorSource, string> = { SERVER: '서버', WEB: '웹', APP: '앱' }

const dateTime = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))
