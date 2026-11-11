/** 체크해서 고르는 팀원 목록: 고른 사람과 넣고 빼기 */
export type MemberSelection = { selected: Set<number>; toggle: (userId: number) => void }
