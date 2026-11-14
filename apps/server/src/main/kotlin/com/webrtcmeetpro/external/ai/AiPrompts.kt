package com.webrtcmeetpro.external.ai

import com.webrtcmeetpro.domain.meeting.NotesActionItem
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesLine
import com.webrtcmeetpro.domain.meeting.NotesResult
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Claude 와 Ollama 가 같은 지시문·스키마를 쓴다 */
object NotesPrompt {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    val SYSTEM = """
        You write meeting notes for a team collaboration app. You receive the meeting's title, date, participant list,
        and a transcript. Each participant's own browser transcribed their microphone with speech recognition, so speaker
        names are reliable but the text can contain recognition mistakes, missing punctuation, and filler words.
        Read through those mistakes for the intended meaning; don't quote them.

        Write the notes in the language most of the meeting was spoken in, and report that language as a short code
        (ko, en, ja, zh, ...). Keep it faithful to the transcript: no invented facts, numbers, or commitments.

        - title: a short, specific title for what the meeting was actually about (the given title may be generic).
        - summary: 3 to 7 short bullet sentences covering the main topics and outcomes, most important first.
        - decisions: things the group agreed on or concluded. Empty if nothing was decided.
        - actionItems: concrete follow-up tasks someone took on or was asked to do. owner must be exactly one of the
          participant names when the transcript makes the owner clear, otherwise null.
          Deadlines: if the deadline was said as a weekday ("by Friday", "금요일까지", "다음 주 월요일"), set dueWeekday to that day
          (MON..SUN) and dueNextWeek to true only when they said "next week" (다음 주, 来週, 下周); leave due null.
          If it was said as a date ("on the 20th", "11월 20일"), set due to that ISO date (YYYY-MM-DD) and dueWeekday to null.
          Otherwise all three are null/false.
        - A plan the group settles on ("let's move it to Tuesday", "화요일로 하죠") is a decision, even if nobody says "decided".
        If the transcript is too short or empty of substance, say so briefly in the summary and leave the lists empty.
    """.trimIndent()

    private fun nullable(type: String) = mapOf("type" to listOf(type, "null"))

    val SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "language" to mapOf("type" to "string"),
            "summary" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "decisions" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "actionItems" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "task" to mapOf("type" to "string"),
                        "owner" to nullable("string"),
                        "due" to nullable("string"),
                        "dueWeekday" to mapOf("type" to listOf("string", "null"), "enum" to listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", null)),
                        "dueNextWeek" to mapOf("type" to "boolean"),
                    ),
                    "required" to listOf("task", "owner", "due", "dueWeekday", "dueNextWeek"),
                    "additionalProperties" to false,
                ),
            ),
        ),
        "required" to listOf("title", "language", "summary", "decisions", "actionItems"),
        "additionalProperties" to false,
    )

    fun render(input: NotesInput, lines: List<NotesLine> = input.lines): String = buildString {
        appendLine("<meeting>")
        appendLine("title: ${input.meetingTitle}")
        appendLine("date: ${input.meetingDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEE) HH:mm", Locale.ENGLISH))}")
        appendLine("participants: ${input.participants.joinToString(", ") { it.name }}")
        // 작은 모델은 "금요일까지" 같은 날짜 계산을 자주 틀려서 앞으로 2주 달력을 같이 준다
        val day = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)
        appendLine("upcoming dates: " + (0L..14L).joinToString(", ") { input.meetingDate.toLocalDate().plusDays(it).format(day) })
        appendLine("</meeting>")
        appendLine()
        appendLine("<transcript>")
        lines.forEach { appendLine("[${it.at.format(TIME)}] ${it.speaker}: ${it.text}") }
        appendLine("</transcript>")
    }

    private data class Item(
        val task: String = "",
        val owner: String? = null,
        val due: String? = null,
        val dueWeekday: String? = null,
        val dueNextWeek: Boolean? = null,
    )

    private val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    /**
     * 작은 모델은 날짜 계산을 자주 틀려서 요일 기한은 서버가 날짜로 바꾼다.
     * 이번 주 = 회의 다음 날부터 처음 오는 그 요일 (같은 요일이면 그날), 다음 주 = 다음 주 월요일부터의 그 요일
     */
    fun weekdayDate(meetingDay: java.time.LocalDate, weekday: String, nextWeek: Boolean): java.time.LocalDate? {
        val target = WEEKDAYS.indexOf(weekday.uppercase()).takeIf { it >= 0 }?.let { java.time.DayOfWeek.of(it + 1) } ?: return null
        return if (nextWeek) {
            meetingDay.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).with(java.time.temporal.TemporalAdjusters.nextOrSame(target))
        } else {
            meetingDay.with(java.time.temporal.TemporalAdjusters.nextOrSame(target))
        }
    }

    private data class Output(
        val title: String? = null,
        val language: String? = null,
        val summary: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
        val actionItems: List<Item> = emptyList(),
    )

    fun parse(json: JsonMapper, text: String, model: String, meetingDay: java.time.LocalDate? = null): NotesResult {
        val out: Output = json.readValue(text)
        return NotesResult(
            title = out.title?.trim()?.ifBlank { null },
            language = out.language?.trim()?.ifBlank { null },
            summary = out.summary.map(String::trim).filter(String::isNotEmpty),
            decisions = out.decisions.map(String::trim).filter(String::isNotEmpty),
            actionItems = out.actionItems.filter { it.task.isNotBlank() }.map {
                val byWeekday = if (meetingDay != null && it.dueWeekday != null) weekdayDate(meetingDay, it.dueWeekday, it.dueNextWeek == true)?.toString() else null
                NotesActionItem(it.task.trim(), it.owner?.trim()?.ifBlank { null }, byWeekday ?: it.due?.trim()?.takeIf { d -> DATE.matches(d) })
            },
            model = model,
        )
    }
}

object CaptionPrompt {
    val LANG = Regex("[a-z]{2}")

    private val NAMES = mapOf("ko" to "Korean", "en" to "English", "ja" to "Japanese", "zh" to "Simplified Chinese")

    val SYSTEM = """
        You translate live captions in a video meeting. Each line is one sentence a participant just said, produced by
        speech recognition, so it may lack punctuation or contain small recognition errors; translate the intended meaning.
        Return a natural, concise spoken-style translation for each requested language code (ko = Korean, en = English,
        ja = Japanese, zh = Simplified Chinese). Keep names, product names and numbers as they are. The previous lines are
        context only; translate just the line. If the line is already in a target language, return it unchanged for that language.
    """.trimIndent()

    fun targets(langs: Collection<String>): List<String> = langs.filter { it.matches(LANG) }.distinct().sorted()

    fun schema(targets: List<String>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to targets.associateWith { mapOf("type" to "string") },
        "required" to targets,
        "additionalProperties" to false,
    )

    fun render(text: String, sourceLang: String, targets: List<String>, context: List<String>): String = buildString {
        if (context.isNotEmpty()) {
            appendLine("<previous_lines>")
            context.forEach { appendLine(it) }
            appendLine("</previous_lines>")
        }
        appendLine("<line lang=\"$sourceLang\">$text</line>")
        append("Translate the line into: ${targets.joinToString(", ") { "$it (${NAMES[it] ?: it})" }}")
    }

    fun clean(out: Map<String, String>, targets: List<String>): Map<String, String> =
        out.filterKeys { it in targets }.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
}
