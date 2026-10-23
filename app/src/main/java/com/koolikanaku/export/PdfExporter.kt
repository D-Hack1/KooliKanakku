package com.koolikanaku.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.koolikanaku.capture.PhotoStore
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.Job
import com.koolikanaku.domain.JobQuote
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.RoomEntry
import com.koolikanaku.domain.RoomQuote
import com.koolikanaku.domain.TaskSource
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595   // Standard A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842  // Standard A4 height in points (72 dpi)
    private const val MARGIN = 36f

    /**
     * Exports a Job and its JobQuote into a multi-page, tamper-evident PDF report.
     *
     * @return FileProvider Uri for sharing via ACTION_SEND.
     */
    fun exportBillPdf(
        context: Context,
        job: Job,
        quote: JobQuote,
        worstToughByRoom: Map<String, String> = emptyMap()
    ): Uri {
        val document = PdfDocument()

        val pdfHelper = DocumentBuilder(context, document, job, quote, worstToughByRoom)
        pdfHelper.build()

        // Save PDF to cache directory
        val exportDir = File(context.cacheDir, "pdf").apply { if (!exists()) mkdirs() }
        val pdfFile = File(exportDir, "KooliKanakku_${job.mode.name}_${job.id.take(8)}.pdf")

        FileOutputStream(pdfFile).use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    /**
     * Helper to open the Android system share sheet for the exported PDF.
     */
    fun sharePdf(context: Context, pdfUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Share Cleaning Estimate / Quotation")
        )
    }

    private class DocumentBuilder(
        private val context: Context,
        private val document: PdfDocument,
        private val job: Job,
        private val quote: JobQuote,
        private val worstToughByRoom: Map<String, String>
    ) {
        private var pageNumber = 1
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var currentY = MARGIN

        // Paints
        private val titlePaint = Paint().apply {
            color = Color.rgb(24, 32, 44)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        private val subTitlePaint = Paint().apply {
            color = Color.rgb(80, 90, 105)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        private val sectionHeaderPaint = Paint().apply {
            color = Color.rgb(20, 80, 120)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        private val bodyBoldPaint = Paint().apply {
            color = Color.rgb(33, 37, 41)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        private val bodyTextPaint = Paint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        private val hashMonoPaint = Paint().apply {
            color = Color.rgb(40, 50, 60)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        private val linePaint = Paint().apply {
            color = Color.rgb(220, 225, 230)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        private val boxBgPaint = Paint().apply {
            color = Color.rgb(245, 247, 250)
            style = Paint.Style.FILL
        }

        fun build() {
            startNewPage()
            drawHeader()
            drawJobSummary()

            // Rooms breakdown
            job.rooms.forEachIndexed { index, room ->
                val roomQuote = quote.rooms.find { it.roomId == room.id }
                ensureSpace(120f)
                drawRoomSection(index + 1, room, roomQuote)
            }

            // Totals section
            ensureSpace(100f)
            drawGrandTotals()

            // Photo Evidence & Hash Appendix
            ensureSpace(140f)
            drawEvidenceAppendix()

            // Mandatory Disclaimer
            ensureSpace(64f)
            drawDisclaimer()

            finishCurrentPage()
        }

        private fun startNewPage() {
            finishCurrentPage()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage!!.canvas
            currentY = MARGIN
        }

        private fun finishCurrentPage() {
            currentPage?.let {
                // Footer
                canvas?.drawText(
                    "KooliKanakku On-Device Evidence Billing  •  Page ${pageNumber - 1}",
                    MARGIN,
                    PAGE_HEIGHT - 20f,
                    subTitlePaint
                )
                document.finishPage(it)
            }
            currentPage = null
            canvas = null
        }

        private fun ensureSpace(neededPoints: Float) {
            if (currentY + neededPoints > PAGE_HEIGHT - 40f) {
                startNewPage()
            }
        }

        private fun drawHeader() {
            val title = if (job.mode == AppMode.OWNER_QUOTE) "CLEANING QUOTATION" else "CLEANING ESTIMATE"
            canvas?.drawText(title, MARGIN, currentY + 18f, titlePaint)
            currentY += 24f

            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
            canvas?.drawText("Generated: $dateStr  •  Job ID: ${job.id.take(8)}  •  Mode: ${job.mode.name}", MARGIN, currentY, subTitlePaint)
            currentY += 16f

            canvas?.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
            currentY += 12f
        }

        private fun drawJobSummary() {
            val cardRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 44f)
            canvas?.drawRoundRect(cardRect, 6f, 6f, boxBgPaint)

            canvas?.drawText("${if (job.mode == AppMode.OWNER_QUOTE) "Market Rate" else "Worker Rate"}: ₹%.0f/hr".format(job.hourlyRateInr), MARGIN + 12f, currentY + 18f, bodyBoldPaint)
            canvas?.drawText("Pace Multiplier: %.2fx".format(job.paceFactor), MARGIN + 12f, currentY + 34f, bodyTextPaint)

            canvas?.drawText("Total Rooms: ${job.rooms.size}", MARGIN + 220f, currentY + 18f, bodyBoldPaint)
            val totalPhotos = job.rooms.sumOf { it.photos.size }
            canvas?.drawText("Verified Photos: $totalPhotos", MARGIN + 220f, currentY + 34f, bodyTextPaint)

            currentY += 56f
        }

        private fun drawRoomSection(index: Int, room: RoomEntry, roomQuote: RoomQuote?) {
            canvas?.drawText("Room $index: ${room.roomType.name}", MARGIN, currentY, sectionHeaderPaint)
            currentY += 14f

            val dimText = room.dimensions?.let {
                "Area: %.1f sq.ft (%s, source: %s)".format(it.computedAreaSqFt, it.unit.name, it.source.name)
            } ?: "Area: Not specified"

            val severity = room.severityOverride ?: room.stain?.severity
            val severityText = "Grime: ${severity?.name ?: "STANDARD"}" +
                    if (room.severityOverride != null) " (Worker Override)" else ""
            val clutterText = "Clutter: ${room.clutter.name}"

            canvas?.drawText("$dimText  •  $severityText  •  $clutterText", MARGIN, currentY, bodyTextPaint)
            currentY += 16f

            // Selected Tasks
            val selectedTasks = room.tasks.filter { it.isSelected }
            if (selectedTasks.isEmpty()) {
                canvas?.drawText("• Standard floor cleaning effort only (no additional fixture tasks selected)", MARGIN + 8f, currentY, bodyTextPaint)
                currentY += 14f
            } else {
                selectedTasks.forEach { task ->
                    val sourceBadge = when (task.source) {
                        TaskSource.DETECTED -> "[Seen in photo]"
                        TaskSource.TEMPLATE -> "[Suggested]"
                        TaskSource.MANUAL -> "[Added by worker]"
                    }
                    val taskLine = "• ${task.titleEn} (x${task.quantity})  $sourceBadge  -  ${task.minutesPerUnit * task.quantity} mins"
                    canvas?.drawText(taskLine, MARGIN + 8f, currentY, bodyTextPaint)
                    currentY += 13f
                }
            }

            // Room Quote Subtotal
            roomQuote?.let {
                val priceStr = "Subtotal: ₹%.0f - ₹%.0f  (Effort: %.0f - %.0f mins)".format(
                    it.priceInr.low, it.priceInr.high,
                    it.expectedMinutes.low, it.expectedMinutes.high
                )
                canvas?.drawText(priceStr, MARGIN + 8f, currentY + 2f, bodyBoldPaint)
                currentY += 16f
            }

            // Room Photos Thumbnails
            if (room.photos.isNotEmpty()) {
                drawRoomPhotoThumbnails(room)
            }

            // Before / after of the toughest spot
            val afterPhoto = room.photos.lastOrNull { it.role == PhotoRole.AFTER }
            if (afterPhoto != null) {
                val toughPhotos = room.photos.filter { it.role == PhotoRole.TOUGHEST }
                val beforePhoto = toughPhotos.firstOrNull { it.id == worstToughByRoom[room.id] } ?: toughPhotos.lastOrNull()
                if (beforePhoto != null) drawBeforeAfter(beforePhoto, afterPhoto)
            }

            canvas?.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
            currentY += 14f
        }

        private fun drawBeforeAfter(before: EvidencePhoto, after: EvidencePhoto) {
            val photoWidth = 170f
            val photoHeight = 120f
            ensureSpace(photoHeight + 56f)
            canvas?.drawText("Before / After (toughest spot)", MARGIN + 8f, currentY + 10f, bodyBoldPaint)
            currentY += 16f

            listOf("Before" to before, "After" to after).forEachIndexed { i, (label, photo) ->
                val x = MARGIN + 8f + i * (photoWidth + 16f)
                val bitmap = try {
                    PhotoStore.getDownscaledBitmap(context, photo.uri, maxLongEdge = 480)
                } catch (e: Exception) {
                    null
                }
                if (bitmap != null) {
                    canvas?.drawBitmap(bitmap, null, RectF(x, currentY, x + photoWidth, currentY + photoHeight), null)
                }
                canvas?.drawText("$label  ${photo.timestampIso.take(19)}", x, currentY + photoHeight + 11f, bodyTextPaint)
                canvas?.drawText(photo.sha256Hash.take(24) + "...", x, currentY + photoHeight + 22f, subTitlePaint)
            }
            currentY += photoHeight + 32f
        }

        private fun drawRoomPhotoThumbnails(room: RoomEntry) {
            val thumbWidth = 65f
            val thumbHeight = 48f
            var thumbX = MARGIN + 8f

            room.photos.take(6).forEach { photo ->
                val bitmap = try {
                    PhotoStore.getDownscaledBitmap(context, photo.uri, maxLongEdge = 200)
                } catch (e: Exception) {
                    null
                }

                if (bitmap != null) {
                    val destRect = RectF(thumbX, currentY, thumbX + thumbWidth, currentY + thumbHeight)
                    canvas?.drawBitmap(bitmap, null, destRect, null)
                    thumbX += thumbWidth + 8f
                }
            }

            currentY += thumbHeight + 10f
        }

        private fun drawGrandTotals() {
            val totalRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 54f)
            val highlightPaint = Paint().apply {
                color = Color.rgb(230, 245, 255)
                style = Paint.Style.FILL
            }
            canvas?.drawRoundRect(totalRect, 6f, 6f, highlightPaint)

            val priceLabel = "TOTAL ESTIMATED PRICE:  ₹%.0f  –  ₹%.0f".format(
                quote.totalPriceInr.low, quote.totalPriceInr.high
            )
            val timeLabel = "Estimated Duration:  %.1f hrs – %.1f hrs".format(
                quote.totalExpectedMinutes.low / 60.0,
                quote.totalExpectedMinutes.high / 60.0
            )

            val totalsTitlePaint = Paint(titlePaint).apply {
                textSize = 14f
                color = Color.rgb(10, 60, 110)
            }

            canvas?.drawText(priceLabel, MARGIN + 14f, currentY + 24f, totalsTitlePaint)
            canvas?.drawText(timeLabel, MARGIN + 14f, currentY + 42f, bodyBoldPaint)

            currentY += 66f
        }

        private fun drawEvidenceAppendix() {
            canvas?.drawText("EVIDENCE PHOTO INTEGRITY APPENDIX (SHA-256)", MARGIN, currentY, sectionHeaderPaint)
            currentY += 14f

            val allPhotos = job.rooms.flatMap { it.photos }
            if (allPhotos.isEmpty()) {
                canvas?.drawText("No camera photos attached to this estimate.", MARGIN, currentY, bodyTextPaint)
                currentY += 14f
                return
            }

            allPhotos.forEach { photo ->
                ensureSpace(20f)
                val roleStr = photo.role.name.padEnd(14)
                val hashStr = photo.sha256Hash
                canvas?.drawText("$roleStr  ${photo.timestampIso.take(19)}", MARGIN, currentY, bodyBoldPaint)
                currentY += 10f
                canvas?.drawText("SHA-256: $hashStr", MARGIN + 8f, currentY, hashMonoPaint)
                currentY += 12f
            }
            currentY += 6f
        }

        private fun drawDisclaimer() {
            val disclaimerText = "Disclaimer: Estimate computed completely on-device. Hashes are of the original captures.\nEmbedded photos are downscaled for document portability.\nTamper-evident, not tamper-proof: a phone clock can be changed.\nKooliKanakku operates 100% offline."
            val lines = disclaimerText.split("\n")
            lines.forEach { line ->
                canvas?.drawText(line, MARGIN, currentY, subTitlePaint)
                currentY += 12f
            }
        }
    }
}
