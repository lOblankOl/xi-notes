package com.xinotes.app.util

/**
 * Общий разбор строк чек-листа вида "[ ] текст", "[x]#itemId@reminderAt!D текст"
 * (где !D/!W/!Y — необязательный признак повтора: ежедневно/еженедельно/ежегодно).
 * Вынесено сюда (а не оставлено внутри экрана редактирования), потому что эта же
 * разметка нужна и NotesViewModel — чтобы собрать секцию "Напоминания" в панели
 * с учётом напоминаний на отдельные пункты, а не только на заметку целиком.
 */
object ChecklistParser {
    val LINE_REGEX = Regex("""^\[([ xX])](?:#(\d+))?(?:@(\d+))?(?:!([DWY]))? (.*)$""")

    data class ItemReminder(
        val itemId: Int,
        val text: String,
        val reminderAt: Long,
        val repeatMode: String?
    )

    /** Достаёт из текста заметки все пункты чек-листа, у которых стоит своё напоминание. */
    fun extractItemReminders(content: String): List<ItemReminder> {
        return content.split("\n").mapNotNull { line ->
            val match = LINE_REGEX.find(line) ?: return@mapNotNull null
            val itemId = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
                ?: return@mapNotNull null
            val reminderAt = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull()
                ?: return@mapNotNull null
            val repeat = repeatCodeToMode(match.groupValues[4])
            ItemReminder(itemId, match.groupValues[5], reminderAt, repeat)
        }
    }

    /**
     * Текущий текст пункта по его id — независимо от того, стоит ли на нём сейчас
     * напоминание. Нужно, чтобы в момент срабатывания напоминания показать именно
     * то, что сейчас написано в пункте, а не то, что было на момент её установки.
     */
    fun findItemText(content: String, itemId: Int): String? {
        content.split("\n").forEach { line ->
            val match = LINE_REGEX.find(line) ?: return@forEach
            val id = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
            if (id == itemId) return match.groupValues[5]
        }
        return null
    }

    fun repeatCodeToMode(code: String): String? = when (code) {
        "D" -> "DAILY"
        "W" -> "WEEKLY"
        "Y" -> "YEARLY"
        else -> null
    }

    fun repeatModeToCode(mode: String?): String = when (mode) {
        "DAILY" -> "D"
        "WEEKLY" -> "W"
        "YEARLY" -> "Y"
        else -> ""
    }
}
