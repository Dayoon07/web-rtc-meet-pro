// 이모지 고르기 창에서 쓰는 목록과 '최근 쓴 이모지' 저장
// 운영체제 기본 이모지 글꼴로 그린다 (이미지 파일 없음). 띄어쓰기로 나눈 목록
export const CATEGORIES = {
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

export type EmojiCategory = keyof typeof CATEGORIES | 'recent'

export const TAB_ICON: Record<EmojiCategory, string> = {
  recent: '🕘', smileys: '😀', people: '👋', animals: '🐶', food: '🍔', activity: '⚽', travel: '🚗', objects: '💡', symbols: '❤️',
}

const RECENT_KEY = 'mq.recentEmoji'

export const loadRecent = (): string[] => {
  try {
    const v = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]')
    return Array.isArray(v) ? v.filter((x) => typeof x === 'string').slice(0, 32) : []
  } catch {
    return []
  }
}

export const saveRecent = (emoji: string) => {
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify([emoji, ...loadRecent().filter((e) => e !== emoji)].slice(0, 32)))
  } catch {
    // 저장이 막힌 브라우저(사생활 보호 창 등)면 최근 목록만 안 남는다
  }
}
