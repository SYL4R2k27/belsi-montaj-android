package com.belsi.work.utils

import android.content.Context
import android.os.Environment
import com.belsi.work.data.models.Tool
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    /**
     * Генерация PDF отчета о выдаче инструментов
     *
     * @param context Контекст приложения
     * @param installerName Имя монтажника (кому выдано)
     * @param installerPhone Телефон монтажника
     * @param tools Список выданных инструментов
     * @param issuedBy Кто выдал (имя бригадира/куратора)
     * @param comment Комментарий к выдаче (опционально)
     * @return File - созданный PDF файл
     */
    fun generateToolIssueReport(
        context: Context,
        installerName: String,
        installerPhone: String,
        tools: List<Tool>,
        issuedBy: String,
        comment: String? = null
    ): Result<File> {
        return try {
            // Создаем папку для PDF если не существует
            val pdfDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "BelsiWork/Reports"
            )
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }

            // Имя файла с датой
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val fileName = "Tool_Issue_Report_$timestamp.pdf"
            val pdfFile = File(pdfDir, fileName)

            // Создаем PDF
            val writer = PdfWriter(pdfFile)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)

            // Заголовок
            document.add(
                Paragraph("ОТЧЕТ О ВЫДАЧЕ ИНСТРУМЕНТОВ")
                    .setFontSize(18f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )

            // Дата и время
            val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            document.add(
                Paragraph("Дата выдачи: ${dateTimeFormat.format(Date())}")
                    .setFontSize(12f)
                    .setMarginBottom(10f)
            )

            // Информация о получателе
            document.add(
                Paragraph("ПОЛУЧАТЕЛЬ:")
                    .setFontSize(14f)
                    .setBold()
                    .setMarginTop(10f)
            )
            document.add(Paragraph("ФИО: $installerName").setFontSize(12f))
            document.add(Paragraph("Телефон: $installerPhone").setFontSize(12f).setMarginBottom(10f))

            // Информация о выдавшем
            document.add(
                Paragraph("ВЫДАЛ:")
                    .setFontSize(14f)
                    .setBold()
                    .setMarginTop(10f)
            )
            document.add(Paragraph(issuedBy).setFontSize(12f).setMarginBottom(15f))

            // Таблица инструментов
            document.add(
                Paragraph("СПИСОК ИНСТРУМЕНТОВ:")
                    .setFontSize(14f)
                    .setBold()
                    .setMarginTop(10f)
                    .setMarginBottom(10f)
            )

            val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 3f, 2f)))
                .setWidth(UnitValue.createPercentValue(100f))

            // Заголовки таблицы
            table.addHeaderCell(
                Cell().add(Paragraph("№").setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
            )
            table.addHeaderCell(
                Cell().add(Paragraph("Наименование").setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            )
            table.addHeaderCell(
                Cell().add(Paragraph("Серийный номер").setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            )

            // Данные
            tools.forEachIndexed { index, tool ->
                table.addCell(
                    Cell().add(Paragraph("${index + 1}"))
                        .setTextAlignment(TextAlignment.CENTER)
                )
                table.addCell(Cell().add(Paragraph(tool.name)))
                table.addCell(Cell().add(Paragraph(tool.serialNumber ?: "-")))
            }

            document.add(table)

            // Итого
            document.add(
                Paragraph("ИТОГО: ${tools.size} ${getToolsCountText(tools.size)}")
                    .setFontSize(12f)
                    .setBold()
                    .setMarginTop(15f)
            )

            // Комментарий
            if (!comment.isNullOrBlank()) {
                document.add(
                    Paragraph("КОММЕНТАРИЙ:")
                        .setFontSize(12f)
                        .setBold()
                        .setMarginTop(15f)
                )
                document.add(Paragraph(comment).setFontSize(11f))
            }

            // Подписи
            document.add(
                Paragraph("\n\n\n")
            )
            document.add(
                Paragraph("Выдал: _________________ ($issuedBy)")
                    .setFontSize(11f)
            )
            document.add(
                Paragraph("\n")
            )
            document.add(
                Paragraph("Получил: _________________ ($installerName)")
                    .setFontSize(11f)
            )

            // Footer
            document.add(
                Paragraph("\n\n")
            )
            document.add(
                Paragraph("Создано в приложении BELSI.Work")
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
            )

            document.close()

            Result.success(pdfFile)
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "Error generating PDF", e)
            Result.failure(Exception("Ошибка создания PDF: ${e.message}"))
        }
    }

    private fun getToolsCountText(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "инструмент"
            count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "инструмента"
            else -> "инструментов"
        }
    }
}
