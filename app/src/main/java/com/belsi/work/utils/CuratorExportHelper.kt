package com.belsi.work.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.belsi.work.data.remote.dto.curator.*
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экспорт данных куратора в CSV и PDF форматы.
 * Используется для формирования отчётов по дашборду, бригадам и фото.
 */
object CuratorExportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    /**
     * Экспорт сводного отчёта в CSV
     */
    fun exportDashboardCsv(
        context: Context,
        dashboard: CuratorDashboardDto,
        foremen: List<CuratorForemanDto>,
        photos: List<CuratorPhotoDto>
    ): Result<File> {
        return try {
            val file = createExportFile(context, "curator_report_${dateFormat.format(Date())}.csv")
            CSVWriter(FileWriter(file)).use { writer ->
                // Заголовок отчёта
                writer.writeNext(arrayOf("BELSI.Work — Отчёт куратора", displayDateFormat.format(Date())))
                writer.writeNext(arrayOf())

                // Дашборд
                writer.writeNext(arrayOf("=== СВОДКА ==="))
                writer.writeNext(arrayOf("Показатель", "Значение"))
                writer.writeNext(arrayOf("Смен сегодня", "${dashboard.totalShiftsToday}"))
                writer.writeNext(arrayOf("Активных монтажников", "${dashboard.activeInstallersToday}"))
                writer.writeNext(arrayOf("Всего монтажников", "${dashboard.totalInstallers}"))
                writer.writeNext(arrayOf("Бригадиров", "${dashboard.totalForemen}"))
                writer.writeNext(arrayOf("Координаторов", "${dashboard.totalCoordinators}"))
                writer.writeNext(arrayOf("Фото на модерации", "${dashboard.pendingPhotos}"))
                writer.writeNext(arrayOf("Тикетов открытых", "${dashboard.openSupportTickets}"))
                writer.writeNext(arrayOf("Инструментов выдано", "${dashboard.toolsIssued} / ${dashboard.totalTools}"))
                writer.writeNext(arrayOf())

                // Бригадиры
                if (foremen.isNotEmpty()) {
                    writer.writeNext(arrayOf("=== БРИГАДИРЫ ==="))
                    writer.writeNext(arrayOf("Имя", "Телефон", "Монтажников", "Фото на модерации", "Последняя активность"))
                    foremen.forEach { f ->
                        writer.writeNext(arrayOf(
                            f.fullName,
                            f.phone,
                            "${f.teamSize}",
                            "${f.pendingPhotosCount}",
                            f.createdAt ?: "-"
                        ))
                    }
                    writer.writeNext(arrayOf())
                }

                // Фото (последние)
                if (photos.isNotEmpty()) {
                    writer.writeNext(arrayOf("=== ФОТО (последние ${photos.size}) ==="))
                    writer.writeNext(arrayOf("Монтажник", "Бригадир", "Категория", "Статус", "Комментарий", "AI-анализ", "Дата"))
                    photos.forEach { p ->
                        writer.writeNext(arrayOf(
                            p.userName ?: "-",
                            p.foremanName ?: "-",
                            when (p.category) {
                                "hourly" -> "Ежечасное"
                                "problem" -> "Проблема"
                                "question" -> "Вопрос"
                                else -> p.category
                            },
                            when (p.status) {
                                "approved" -> "Одобрено"
                                "rejected" -> "Отклонено"
                                "pending", null -> "На модерации"
                                else -> p.status ?: "-"
                            },
                            p.comment ?: "",
                            p.aiComment ?: "",
                            p.timestamp ?: "-"
                        ))
                    }
                }
            }
            Result.success(file)
        } catch (e: Exception) {
            android.util.Log.e("CuratorExport", "CSV export error", e)
            Result.failure(Exception("Ошибка экспорта CSV: ${e.message}"))
        }
    }

    /**
     * Экспорт сводного отчёта в PDF
     */
    fun exportDashboardPdf(
        context: Context,
        dashboard: CuratorDashboardDto,
        foremen: List<CuratorForemanDto>,
        photos: List<CuratorPhotoDto>
    ): Result<File> {
        return try {
            val file = createExportFile(context, "curator_report_${dateFormat.format(Date())}.pdf")
            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)

            // Заголовок
            document.add(
                Paragraph("BELSI.Work — Отчёт куратора")
                    .setFontSize(18f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5f)
            )
            document.add(
                Paragraph(displayDateFormat.format(Date()))
                    .setFontSize(11f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY)
                    .setMarginBottom(20f)
            )

            // === Сводка ===
            document.add(Paragraph("СВОДКА").setFontSize(14f).setBold().setMarginBottom(8f))

            val statsTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 1f)))
                .setWidth(UnitValue.createPercentValue(100f))

            fun addStatRow(label: String, value: String) {
                statsTable.addCell(Cell().add(Paragraph(label).setFontSize(11f)))
                statsTable.addCell(Cell().add(Paragraph(value).setFontSize(11f).setBold()).setTextAlignment(TextAlignment.RIGHT))
            }

            addStatRow("Смен сегодня", "${dashboard.totalShiftsToday}")
            addStatRow("Активных монтажников", "${dashboard.activeInstallersToday} / ${dashboard.totalInstallers}")
            addStatRow("Бригадиров (активных)", "${dashboard.activeForemenToday} / ${dashboard.totalForemen}")
            addStatRow("Координаторов (активных)", "${dashboard.activeCoordinatorsToday} / ${dashboard.totalCoordinators}")
            addStatRow("Фото на модерации", "${dashboard.pendingPhotos}")
            addStatRow("Тикетов открытых", "${dashboard.openSupportTickets}")
            addStatRow("Инструментов выдано", "${dashboard.toolsIssued} / ${dashboard.totalTools}")

            document.add(statsTable)
            document.add(Paragraph("\n"))

            // === Бригадиры ===
            if (foremen.isNotEmpty()) {
                document.add(Paragraph("БРИГАДИРЫ").setFontSize(14f).setBold().setMarginBottom(8f))

                val foremanTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 2f, 1f, 1f)))
                    .setWidth(UnitValue.createPercentValue(100f))

                // Headers
                listOf("Имя", "Телефон", "Монтажников", "Фото").forEach { header ->
                    foremanTable.addHeaderCell(
                        Cell().add(Paragraph(header).setBold().setFontSize(10f))
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    )
                }

                foremen.forEach { f ->
                    foremanTable.addCell(Cell().add(Paragraph(f.fullName).setFontSize(10f)))
                    foremanTable.addCell(Cell().add(Paragraph(f.phone).setFontSize(10f)))
                    foremanTable.addCell(Cell().add(Paragraph("${f.teamSize}").setFontSize(10f)).setTextAlignment(TextAlignment.CENTER))
                    foremanTable.addCell(Cell().add(Paragraph("${f.pendingPhotosCount}").setFontSize(10f)).setTextAlignment(TextAlignment.CENTER))
                }

                document.add(foremanTable)
                document.add(Paragraph("\n"))
            }

            // === Фото (краткая сводка) ===
            if (photos.isNotEmpty()) {
                val pendingCount = photos.count { it.status == "pending" || it.status == null }
                val approvedCount = photos.count { it.status == "approved" }
                val rejectedCount = photos.count { it.status == "rejected" }
                val problemCount = photos.count { it.category == "problem" }

                document.add(Paragraph("ФОТО (${photos.size} всего)").setFontSize(14f).setBold().setMarginBottom(8f))
                document.add(Paragraph("На модерации: $pendingCount  |  Одобрено: $approvedCount  |  Отклонено: $rejectedCount  |  Проблемных: $problemCount").setFontSize(10f).setMarginBottom(10f))

                // Таблица последних 50 фото
                val displayPhotos = photos.take(50)
                val photoTable = Table(UnitValue.createPercentArray(floatArrayOf(2f, 2f, 1.5f, 1.5f, 2f)))
                    .setWidth(UnitValue.createPercentValue(100f))

                listOf("Монтажник", "Бригадир", "Категория", "Статус", "Дата").forEach { header ->
                    photoTable.addHeaderCell(
                        Cell().add(Paragraph(header).setBold().setFontSize(9f))
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    )
                }

                displayPhotos.forEach { p ->
                    photoTable.addCell(Cell().add(Paragraph(p.userName ?: "-").setFontSize(9f)))
                    photoTable.addCell(Cell().add(Paragraph(p.foremanName ?: "-").setFontSize(9f)))
                    photoTable.addCell(Cell().add(Paragraph(
                        when (p.category) {
                            "problem" -> "Проблема"
                            "question" -> "Вопрос"
                            else -> "Ежечасное"
                        }
                    ).setFontSize(9f)))
                    photoTable.addCell(Cell().add(Paragraph(
                        when (p.status) {
                            "approved" -> "Одобрено"
                            "rejected" -> "Отклонено"
                            else -> "Ожидает"
                        }
                    ).setFontSize(9f)))
                    photoTable.addCell(Cell().add(Paragraph(formatTimestamp(p.timestamp)).setFontSize(9f)))
                }

                document.add(photoTable)
            }

            // Footer
            document.add(Paragraph("\n\n"))
            document.add(
                Paragraph("Создано в приложении BELSI.Work")
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
            )

            document.close()
            Result.success(file)
        } catch (e: Exception) {
            android.util.Log.e("CuratorExport", "PDF export error", e)
            Result.failure(Exception("Ошибка экспорта PDF: ${e.message}"))
        }
    }

    /**
     * Открыть Share Intent для экспортированного файла
     */
    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = when {
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".pdf") -> "application/pdf"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BELSI.Work — Отчёт куратора")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
        } catch (e: Exception) {
            android.util.Log.e("CuratorExport", "Share error", e)
        }
    }

    private fun createExportFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    private fun formatTimestamp(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "-"
        return try {
            val dt = java.time.OffsetDateTime.parse(isoString)
            dt.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        } catch (e: Exception) {
            isoString.take(16)
        }
    }
}
