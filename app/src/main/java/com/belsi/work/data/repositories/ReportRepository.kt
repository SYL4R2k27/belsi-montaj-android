package com.belsi.work.data.repositories

import android.content.Context
import com.belsi.work.data.models.ShiftReport
import com.belsi.work.data.remote.api.ReportApi
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportApi: ReportApi
) {

    /**
     * Получить отчет с сервера
     */
    suspend fun getShiftReport(
        startDate: String,
        endDate: String,
        userId: UUID? = null,
        foremanId: UUID? = null,
        curatorId: UUID? = null,
        status: String? = null
    ): Result<ShiftReport> {
        return try {
            val response = reportApi.getShiftReport(
                startDate = startDate,
                endDate = endDate,
                userId = userId,
                foremanId = foremanId,
                curatorId = curatorId,
                status = status
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка получения отчета: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Генерация CSV отчета (для Excel)
     */
    fun generateExcelReport(report: ShiftReport): Result<File> {
        return try {
            val fileName = "shift_report_${System.currentTimeMillis()}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileWriter(file, Charsets.UTF_8).use { writer ->
                // BOM для корректного отображения кириллицы в Excel
                writer.write("\uFEFF")

                // Заголовок отчета
                writer.write("Отчет по сменам за период: ${report.periodStart} - ${report.periodEnd}\n")
                writer.write("\n")

                // Заголовки столбцов
                val headers = listOf(
                    "№",
                    "ФИО",
                    "Телефон",
                    "Дата смены",
                    "Общее время",
                    "Время работы",
                    "Пауза",
                    "Простой",
                    "Причина простоя",
                    "Ставка (руб/час)",
                    "Сумма",
                    "Закреплён за",
                    "Статус"
                )
                writer.write(headers.joinToString(";") + "\n")

                // Данные
                report.entries.forEachIndexed { index, entry ->
                    val row = listOf(
                        (index + 1).toString(),
                        escapeCSV(entry.userFullName ?: entry.userName),
                        escapeCSV(entry.userPhone),
                        formatDate(entry.shiftDate),
                        entry.formattedTotalTime,
                        entry.formattedWorkTime,
                        entry.formattedPauseTime,
                        entry.formattedIdleTime,
                        escapeCSV(entry.idleReason ?: "-"),
                        String.format("%.2f", entry.hourlyRate),
                        String.format("%.2f", entry.totalAmount),
                        escapeCSV(entry.assignedTo),
                        entry.status.name
                    )
                    writer.write(row.joinToString(";") + "\n")
                }

                // Пустая строка
                writer.write("\n")

                // Итоговая строка
                writer.write("ИТОГО;")
                writer.write("Смен: ${report.totalShifts};;;;")
                writer.write("Часов работы: ${String.format("%.2f", report.totalWorkHours)};;;;")
                writer.write("${String.format("%.2f", report.totalAmount)};;;\n")
            }

            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Экранирование специальных символов для CSV
     */
    private fun escapeCSV(value: String): String {
        return if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Генерация PDF отчета
     */
    fun generatePdfReport(report: ShiftReport): Result<File> {
        return try {
            val fileName = "shift_report_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)

            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)

            // Используем встроенный шрифт с поддержкой кириллицы
            val font = PdfFontFactory.createFont("Helvetica", "CP1251")

            // Заголовок
            val title = Paragraph("Отчет по сменам")
                .setFont(font)
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
            document.add(title)

            val period = Paragraph("Период: ${report.periodStart} - ${report.periodEnd}")
                .setFont(font)
                .setFontSize(12f)
                .setTextAlignment(TextAlignment.CENTER)
            document.add(period)

            document.add(Paragraph("\n"))

            // Таблица
            val columnWidths = floatArrayOf(1f, 3f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 2f, 2f, 2f)
            val table = Table(UnitValue.createPercentArray(columnWidths))
                .useAllAvailableWidth()

            // Заголовки
            val headers = listOf(
                "№", "ФИО", "Дата", "Общее", "Работа",
                "Пауза", "Простой", "Причина", "Ставка", "Сумма", "За кем", "Статус"
            )

            headers.forEach { header ->
                table.addHeaderCell(
                    Cell().add(Paragraph(header).setFont(font).setFontSize(9f).setBold())
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                )
            }

            // Данные
            report.entries.forEachIndexed { index, entry ->
                table.addCell(Cell().add(Paragraph("${index + 1}").setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.userFullName ?: entry.userName).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(formatDate(entry.shiftDate)).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.formattedTotalTime).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.formattedWorkTime).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.formattedPauseTime).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.formattedIdleTime).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.idleReason ?: "-").setFont(font).setFontSize(7f)))
                table.addCell(Cell().add(Paragraph(String.format("%.2f", entry.hourlyRate)).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(String.format("%.2f", entry.totalAmount)).setFont(font).setFontSize(8f)))
                table.addCell(Cell().add(Paragraph(entry.assignedTo).setFont(font).setFontSize(7f)))
                table.addCell(Cell().add(Paragraph(entry.status.name).setFont(font).setFontSize(7f)))
            }

            document.add(table)

            // Итого
            document.add(Paragraph("\n"))
            val summary = Paragraph()
                .setFont(font)
                .setFontSize(12f)
                .setBold()
                .add("ИТОГО: Смен: ${report.totalShifts} | ")
                .add("Часов работы: ${String.format("%.2f", report.totalWorkHours)} | ")
                .add("Сумма: ${String.format("%.2f ₽", report.totalAmount)}")
            document.add(summary)

            document.close()

            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(isoDate)
            date?.let { outputFormat.format(it) } ?: isoDate
        } catch (e: Exception) {
            isoDate
        }
    }
}
