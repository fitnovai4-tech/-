package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.model.Note
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileHelper {
    private const val TAG = "FileHelper"

    /**
     * Copy any Uri (from system picker) to app private storage.
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri, subFolder: String): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            // Find file extension
            val mimeType = context.contentResolver.getType(uri)
            val extension = when {
                mimeType?.contains("pdf") == true -> "pdf"
                mimeType?.contains("png") == true -> "png"
                else -> "jpg"
            }

            val folder = File(context.filesDir, subFolder)
            if (!folder.exists()) folder.mkdirs()

            val fileName = "file_${UUID.randomUUID()}.$extension"
            val file = File(folder, fileName)
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(4 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to internal storage", e)
            null
        }
    }

    /**
     * Saves a bitmap to private storage as a drawing file.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, subFolder: String): String? {
        return try {
            val folder = File(context.filesDir, subFolder)
            if (!folder.exists()) folder.mkdirs()

            val fileName = "drawing_${UUID.randomUUID()}.png"
            val file = File(folder, fileName)
            val outputStream = FileOutputStream(file)

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save drawing bitmap", e)
            null
        }
    }

    /**
     * Create a temporary file path for audio recordings.
     */
    fun createAudioFile(context: Context): File {
        val folder = File(context.filesDir, "audio")
        if (!folder.exists()) folder.mkdirs()
        return File(folder, "audio_${UUID.randomUUID()}.m4a")
    }

    /**
     * Render the first page of a PDF file as a bitmap for drawing or preview.
     */
    fun renderPdfPageToBitmap(context: Context, pdfPath: String, pageIndex: Int = 0): Bitmap? {
        return try {
            val file = File(pdfPath)
            if (!file.exists()) return null

            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            if (pdfRenderer.pageCount <= pageIndex) {
                pdfRenderer.close()
                fileDescriptor.close()
                return null
            }

            val page = pdfRenderer.openPage(pageIndex)
            
            // Create a high quality bitmap
            val width = page.width * 2
            val height = page.height * 2
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Fill background white
            bitmap.eraseColor(Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pdfRenderer.close()
            fileDescriptor.close()

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF page", e)
            null
        }
    }

    /**
     * Export a Note to PDF format natively.
     */
    fun exportNoteToPdf(context: Context, note: Note): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width in postscript points
            val pageHeight = 842 // A4 height in postscript points
            var pageNum = 1
            
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT // RTL text flow
            }

            val tagPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textAlign = Paint.Align.RIGHT
            }

            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 14f
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.RIGHT
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var currentY = 50f

            // App Heading
            val appHeaderPaint = Paint().apply {
                color = Color.argb(255, 63, 81, 181) // Indigo Primary Color
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("الملاحظات الذكية - تصدير PDF", 50f, currentY, appHeaderPaint)
            canvas.drawLine(50f, currentY + 10f, (pageWidth - 50).toFloat(), currentY + 10f, linePaint)
            currentY += 40f

            // Draw Note Title (RTL Arabic alignment)
            canvas.drawText(note.title.ifEmpty { "بدون عنوان" }, (pageWidth - 50).toFloat(), currentY, titlePaint)
            currentY += 25f

            // Tags
            if (note.tags.isNotEmpty()) {
                canvas.drawText("الوسوم: ${note.tags}", (pageWidth - 50).toFloat(), currentY, tagPaint)
                currentY += 20f
            }

            // Separator
            canvas.drawLine(50f, currentY, (pageWidth - 50).toFloat(), currentY, linePaint)
            currentY += 25f

            // Content paragraphs (split by newline and wrap)
            val lines = note.content.split("\n")
            for (line in lines) {
                if (currentY > pageHeight - 100) {
                    // Start a new page if we run out of vertical space
                    pdfDocument.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 50f
                    canvas.drawLine(50f, currentY, (pageWidth - 50).toFloat(), currentY, linePaint)
                    currentY += 25f
                }

                // Simple text wrapping for PDF output (RTL)
                val words = line.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val measure = bodyPaint.measureText(testLine)
                    if (measure > pageWidth - 100) {
                        canvas.drawText(currentLine, (pageWidth - 50).toFloat(), currentY, bodyPaint)
                        currentY += 20f
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, (pageWidth - 50).toFloat(), currentY, bodyPaint)
                    currentY += 25f
                }
            }

            // Draw Images or drawings if present
            val imageToDraw = note.drawingPath ?: note.imagePath
            if (imageToDraw != null) {
                val file = File(imageToDraw)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        // Scale image to fit page width
                        val maxWidth = pageWidth - 100
                        val scale = maxWidth.toFloat() / bitmap.width.toFloat()
                        val scaledHeight = bitmap.height * scale

                        if (currentY + scaledHeight > pageHeight - 50) {
                            // Draw on a new page
                            pdfDocument.finishPage(page)
                            pageNum++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = 50f
                        }

                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, scaledHeight.toInt(), true)
                        canvas.drawBitmap(scaledBitmap, 50f, currentY, null)
                        currentY += scaledHeight + 20f
                    }
                }
            }

            pdfDocument.finishPage(page)

            // Save PDF to downloads or internal documents folder
            val pdfFolder = File(context.filesDir, "exported_pdf")
            if (!pdfFolder.exists()) pdfFolder.mkdirs()

            val cleanTitle = note.title.replace("[^a-zA-Z0-9أ-ي]".toRegex(), "_").ifEmpty { "note" }
            val pdfFile = File(pdfFolder, "pdf_${cleanTitle}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.flush()
            outputStream.close()

            pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export note to PDF", e)
            null
        }
    }
}
