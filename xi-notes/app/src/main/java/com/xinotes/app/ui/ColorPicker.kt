package com.xinotes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Небольшая фиксированная палитра — этого достаточно, чтобы визуально отличать
 *  несколько заметок/групп друг от друга, не превращая это в полноценный color picker. */
val NOTE_COLOR_PALETTE = listOf(
    "#4CAF7D", "#64B5F6", "#FFD54F", "#E57373", "#BA68C8", "#FF8A65"
)

fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color.Gray
}

@Composable
fun ColorPickerDialog(onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Цветовая метка") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NOTE_COLOR_PALETTE.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex))
                            .clickable { onPick(hex) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) { Text("Без цвета") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
