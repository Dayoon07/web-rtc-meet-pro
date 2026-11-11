import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Modal } from '@/shared/ui'

// 운영체제 기본 이모지 글꼴로 그린다 (이미지 파일 없음). 띄어쓰기로 나눈 목록
const CATEGORIES = {
  smileys:
    '😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🤢 🥵 🥶 😵 🤯 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 💀 💩 🤡 👻 👽 🤖',
  people:
    '👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🙏 🤝 💪 ✍️ 💅 👀 🧠 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙇 🙋 🙆 🙅 🤷 🤦 💁 🧑‍💻 👨‍💼 👩‍💼 🕺 💃',
  animals:
    '🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦉 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🐙 🦑 🐠 🐟 🐬 🐳 🐋 🦈 🌸 🌷 🌹 🌻 🌼 🌱 🌲 🌳 🍀 🍁 🍂 🌈 ☀️ ⭐ 🌙 ⚡ ❄️ 🔥 💧 🌊',
  food:
    '🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥝 🍅 🥑 🥦 🥕 🌽 🌶️ 🥔 🍞 🥐 🥨 🧀 🥚 🍳 🥞 🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🎂 🍰 🧁 🍫 🍬 🍭 🍩 🍪 ☕ 🍵 🧋 🥤 🍺 🍻 🥂 🍷',
  activity:
    '⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🎯 ⛳ 🎣 🎿 🏂 🏋️ 🚴 🏊 🧘 🏆 🥇 🥈 🥉 🏅 🎖️ 🎫 🎟️ 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎮 🕹️ 🧩 🎉 🎊 🎈 🎁 🎀 🎄 🎃',
  travel:
    '🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚚 🚲 🛴 🛵 🏍️ 🚨 🚃 🚄 🚅 🚆 🚇 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ 🗺️ 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🏖️ 🏝️ 🏔️ ⛰️ 🏕️ 🏠 🏡 🏢 🏬 🏥 🏦 🏨 🏫 ⛪ 🌃 🌆 🌇 🌉',
  objects:
    '⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⏳ 🔋 🔌 💡 🔦 🕯️ 💸 💵 💰 💳 💎 🔧 🔨 🛠️ ⚙️ 🔩 🧰 🧲 🔑 🗝️ 🔒 🔓 📦 📫 📮 📝 📄 📃 📑 📊 📈 📉 📅 📆 🗓️ 📇 📋 📁 📂 🗂️ 📌 📍 📎 🖇️ 📏 📐 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 🔍 🔎 📚 📖 🔖 🏷️ 📢 📣 🔔 🔕',
  symbols:
    '❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✨ 💯 💢 💥 💫 💦 💨 🕳️ 💬 💭 🗯️ ✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ ❓ ❔ ❕ ❗ ⭕ 🚫 ⛔ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔺 🔻 🔸 🔹 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ 🔁 🔄 🆗 🆕 🆙 🆒 🆓 🔝 ⚠️ ♻️ 💤',
} as const
type Category = keyof typeof CATEGORIES | 'recent'
const TAB_ICON: Record<Category, string> = {
  recent: '🕘', smileys: '😀', people: '👋', animals: '🐶', food: '🍔', activity: '⚽', travel: '🚗', objects: '💡', symbols: '❤️',
}

const RECENT_KEY = 'mq.recentEmoji'
const loadRecent = (): string[] => {
  try {
    const v = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]')
    return Array.isArray(v) ? v.filter((x) => typeof x === 'string').slice(0, 32) : []
  } catch {
    return []
  }
}
const saveRecent = (emoji: string) => {
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify([emoji, ...loadRecent().filter((e) => e !== emoji)].slice(0, 32)))
  } catch {
    // 저장이 막힌 브라우저(사생활 보호 창 등)면 최근 목록만 안 남는다
  }
}

/** 이모지 고르기 창: 분류 탭 + 최근 쓴 것. 고르면 onPick 후 닫힌다 */
export function EmojiPickerModal({ open, onClose, onPick }: { open: boolean; onClose: () => void; onPick: (emoji: string) => void }) {
  const { t } = useTranslation()
  const [recent, setRecent] = useState(loadRecent)
  const [tab, setTab] = useState<Category>(() => (loadRecent().length ? 'recent' : 'smileys'))
  const list = tab === 'recent' ? recent : CATEGORIES[tab].split(' ')

  const pick = (emoji: string) => {
    saveRecent(emoji)
    setRecent(loadRecent())
    onPick(emoji)
  }

  return (
    <Modal open={open} onClose={onClose} title={t('chat.emoji.title')} size="md">
      <div className="flex flex-col gap-3">
        <div role="tablist" aria-label={t('chat.emoji.title')} className="flex gap-1 overflow-x-auto rounded-xl bg-gray-100 p-1 dark:bg-gray-800">
          {(Object.keys(TAB_ICON) as Category[]).map((c) => (
            <button
              key={c}
              type="button"
              role="tab"
              aria-selected={tab === c}
              title={t(`chat.emoji.${c}`)}
              aria-label={t(`chat.emoji.${c}`)}
              onClick={() => setTab(c)}
              className={cn(
                'flex h-9 min-w-9 flex-1 items-center justify-center rounded-lg text-lg transition',
                tab === c ? 'bg-white shadow-sm dark:bg-gray-950' : 'opacity-60 hover:opacity-100',
              )}
            >
              {TAB_ICON[c]}
            </button>
          ))}
        </div>
        <p className="text-xs font-semibold text-gray-500 dark:text-gray-400">{t(`chat.emoji.${tab}`)}</p>
        {list.length ? (
          <div className="grid max-h-72 grid-cols-8 gap-1 overflow-y-auto sm:grid-cols-10">
            {list.map((e, i) => (
              <button
                key={e + i}
                type="button"
                onClick={() => pick(e)}
                aria-label={e}
                className="flex aspect-square items-center justify-center rounded-lg text-2xl transition hover:scale-110 hover:bg-gray-100 dark:hover:bg-gray-800"
              >
                {e}
              </button>
            ))}
          </div>
        ) : (
          <p className="py-10 text-center text-sm text-gray-400">{t('chat.emoji.noRecent')}</p>
        )}
      </div>
    </Modal>
  )
}
