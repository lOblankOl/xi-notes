package com.xinotes.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Все картинки заметок хранятся строго во внутренней приватной папке приложения
 * (context.filesDir/images), НЕ во внешнем/общем хранилище. Благодаря этому при
 * удалении приложения система сама стирает все картинки вместе с базой — вручную
 * чистить ничего не нужно.
 */
object ImageStore {
    private const val MAX_DIMENSION = 1600

    private fun imagesDir(context: Context): File =
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    /**
     * Копирует картинку из выбранного Uri во внутреннее хранилище с уменьшением размера.
     *
     * ВАЖНО: поток читается ОДИН раз, целиком, в byte[] — а не дважды через
     * contentResolver.openInputStream(uri). Некоторые поставщики картинок (в частности
     * облачные/виртуальные документы вроде Google Photos) не позволяют открыть один и тот
     * же Uri повторно в рамках одного разрешения на чтение — второй openInputStream может
     * вернуть null или упасть с исключением. Декодирование размеров и самого битмапа теперь
     * идёт из одного и того же массива байт в памяти, что работает со всеми провайдерами.
     */
    fun importImage(context: Context, uri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION || bounds.outHeight / sampleSize > MAX_DIMENSION) {
                sampleSize *= 2
            }

            val bitmap: Bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return null

            val filename = "${UUID.randomUUID()}.jpg"
            FileOutputStream(File(imagesDir(context), filename)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            filename
        } catch (e: Exception) {
            null
        }
    }

    fun fileFor(context: Context, filename: String): File = File(imagesDir(context), filename)

    /** Парсит маркеры [[img:filename]] в тексте заметки и удаляет соответствующие файлы. */
    fun deleteImagesInContent(context: Context, content: String) {
        val regex = Regex("""\[\[img:([^]]+)]]""")
        regex.findAll(content).forEach { match ->
            fileFor(context, match.groupValues[1]).delete()
        }
    }
}
