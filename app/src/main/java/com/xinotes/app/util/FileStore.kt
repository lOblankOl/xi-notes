package com.xinotes.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Произвольные файлы (не картинки) — например .apk, .pdf, документы и т.д.
 * Хранятся так же, строго во внутренней приватной папке приложения
 * (context.filesDir/attachments), поэтому удаление приложения стирает их
 * автоматически, как и картинки.
 */
object FileStore {
    data class StoredFile(val storedName: String, val displayName: String)

    private fun attachmentsDir(context: Context): File =
        File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }

    fun importFile(context: Context, uri: Uri): StoredFile? {
        return try {
            val displayName = queryDisplayName(context, uri) ?: "файл"
            val ext = displayName.substringAfterLast('.', "")
            val storedName = if (ext.isNotEmpty()) "${UUID.randomUUID()}.$ext" else UUID.randomUUID().toString()
            val outFile = File(attachmentsDir(context), storedName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            StoredFile(storedName, displayName)
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    fun fileFor(context: Context, storedName: String): File = File(attachmentsDir(context), storedName)

    /** Открывает файл системным приложением — так же, как тап по файлу в Telegram. */
    fun openExternally(context: Context, storedName: String, displayName: String) {
        try {
            val file = fileFor(context, storedName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = context.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
                ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Нет приложения, чтобы открыть этот файл", Toast.LENGTH_SHORT).show()
        }
    }

    /** Удаляет файлы, на которые в тексте заметки есть маркеры [[file:...::...]] — вызывается при удалении заметки. */
    fun deleteFilesInContent(context: Context, content: String) {
        Regex("""\[\[file:([^:\]]+)::[^\]]+]]""").findAll(content).forEach { match ->
            fileFor(context, match.groupValues[1]).delete()
        }
    }
}
