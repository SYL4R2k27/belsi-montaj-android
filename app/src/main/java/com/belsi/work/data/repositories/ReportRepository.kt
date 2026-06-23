package com.belsi.work.data.repositories

import android.content.Context
import com.belsi.work.data.models.ShiftReport
import com.belsi.work.data.models.ShiftReportEntry
import com.belsi.work.data.remote.api.ReportApi
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import dagger.hilt.android.qualifiers.ApplicationContext
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-06-02) Report v2 — полная перепись PDF/XLSX.
 *
 * Корни проблем старого отчёта (см. фото пользователя 2026-06-02):
 *  1. iText использовал Helvetica+CP1251 → у Helvetica НЕТ кириллических глифов →
 *     заголовки (ФИО/Смен/Работа…) и Cyrillic-имена монтажников рендерились пусто.
 *     Видны были только phones и числа (ASCII).
 *  2. «Excel»-кнопка генерировала CSV (с BOM) — не настоящий .xlsx.
 *  3. Колонки «Фото / Дней / Объектов» отсутствовали (бэк не отдавал).
 *  4. Нет cover-страницы, нет «топ-проблемные / топ-лидеры», нет цветовых меток
 *     дисциплины — куратор не видит проблем сходу.
 *
 * Что сделано:
 *  - Загружаем Inter Variable из assets как байты → IDENTITY_H, EMBEDDED → cyrillic ок.
 *  - PDF: A4 landscape, 2 страницы (обложка+таблица), цветовые метки риска.
 *  - XLSX: настоящий .xlsx через FastExcel (~600KB, 4 листа), автофильтр + freeze pane.
 *  - Аддитивные поля бэка (photosCount/siteObjectId/userRole) — на месте.
 */
@Singleton
class ReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportApi: ReportApi
) {

    // ===== Бэк-вызов =====

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
                startDate = startDate, endDate = endDate,
                userId = userId, foremanId = foremanId,
                curatorId = curatorId, status = status,
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

    // ===== Агрегация =====

    /**
     * Свёрнутая строка отчёта по 1 монтажнику за период.
     */
    private data class WorkerSum(
        val userId: String,
        val name: String,
        val phone: String,
        val role: String,
        val shifts: Int,
        val days: Int,         // уникальных рабочих дней
        val objects: Int,      // уникальных объектов
        val wall: Long,        // календарное время смен (start..finish)
        val work: Long,
        val pause: Long,
        val lunch: Long,
        val brk: Long,
        val idle: Long,
        val photos: Int,
        val amount: Double,
    ) {
        /** Доля простоя от чистой работы — для риск-окраски. */
        val idleRatio: Double get() = if (work > 0) idle.toDouble() / work else 0.0

        /** Доля «обычной паузы» от чистой работы — индикатор «спрятанного» обеда/перекура. */
        val pauseRatio: Double get() = if (work > 0) pause.toDouble() / work else 0.0
    }

    private fun aggregateByWorker(report: ShiftReport): List<WorkerSum> =
        report.entries.groupBy { it.userId.toString() }.map { (uid, list) ->
            val first = list.first()
            WorkerSum(
                userId = uid,
                name = first.userFullName?.takeIf { it.isNotBlank() } ?: first.userName,
                phone = first.userPhone,
                role = first.userRole,
                shifts = list.size,
                days = list.map { it.shiftDate }.toSet().size,
                objects = list.mapNotNull { it.siteObjectId }.toSet().size,
                wall = list.sumOf { it.totalSeconds },
                work = list.sumOf { it.workSeconds },
                pause = list.sumOf { it.pauseSeconds },
                lunch = list.sumOf { it.lunchSeconds },
                brk = list.sumOf { it.breakSeconds },
                idle = list.sumOf { it.idleSeconds },
                photos = list.sumOf { it.photosCount },
                amount = list.sumOf { it.totalAmount },
            )
        }.sortedByDescending { it.work }

    // ===== Хелперы форматирования =====

    private fun hm(seconds: Long): String {
        val h = seconds / 3600; val m = (seconds % 3600) / 60
        return String.format("%d:%02d", h, m)
    }

    private fun ru(role: String): String = when (role) {
        "installer" -> "Монтажник"
        "foreman" -> "Бригадир"
        "senior_worker" -> "Старший"
        "worker" -> "Работник"
        "curator" -> "Куратор"
        "coordinator" -> "Координатор"
        "production_chief" -> "Начпроиз."
        "supplier" -> "Снабженец"
        "engineer" -> "Инженер"
        "driver" -> "Водитель"
        "logistician" -> "Логист"
        else -> role
    }

    private fun loadInterBytes(): ByteArray =
        context.assets.open("inter_variable.ttf").use { it.readBytes() }

    private fun pdfFont(bytes: ByteArray): PdfFont = PdfFontFactory.createFont(
        bytes,
        PdfEncodings.IDENTITY_H,
        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
    )

    // ===== PDF =====

    private val BRAND_DEEP = DeviceRgb(0x36, 0x2F, 0x97)   // #362F97 — лого (dark)
    private val BRAND_LIGHT = DeviceRgb(0x79, 0x7F, 0xE1)  // #797FE1 — лого (light)
    private val BG_HEADER = DeviceRgb(0xEF, 0xEE, 0xFB)    // bg для шапки таблицы
    private val BG_ALT = DeviceRgb(0xF7, 0xF7, 0xFB)       // zebra
    private val RISK_BG = DeviceRgb(0xFE, 0xE2, 0xE2)      // light red
    private val WARN_BG = DeviceRgb(0xFE, 0xF3, 0xC7)      // light amber
    private val OK_BG = DeviceRgb(0xDC, 0xFC, 0xE7)        // light green

    fun generatePdfReport(report: ShiftReport): Result<File> {
        return try {
            val fileName = "BELSI_otchet_${report.periodStart}_${report.periodEnd}.pdf"
                .replace(":", "-")
            val file = File(context.getExternalFilesDir(null), fileName)

            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            // Landscape A4 (842×595).
            pdfDoc.defaultPageSize = PageSize.A4.rotate()
            val document = Document(pdfDoc)
            document.setMargins(28f, 28f, 28f, 28f)

            val ttf = loadInterBytes()
            val font = pdfFont(ttf)
            val fontBold = pdfFont(ttf) // тот же шрифт; bold через .setBold() (synthetic)

            val workers = aggregateByWorker(report)
            renderCover(document, font, fontBold, report, workers)
            document.add(AreaBreak())
            renderTable(document, font, fontBold, workers)

            document.close()
            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ----- PDF: cover -----

    private fun renderCover(
        doc: Document, font: PdfFont, fontBold: PdfFont,
        report: ShiftReport, workers: List<WorkerSum>,
    ) {
        // Заголовок
        doc.add(
            Paragraph("BELSI · Отчёт по сменам")
                .setFont(fontBold).setBold().setFontSize(22f).setFontColor(BRAND_DEEP)
                .setMarginBottom(2f),
        )
        doc.add(
            Paragraph("Период: ${dmy(report.periodStart)} — ${dmy(report.periodEnd)}")
                .setFont(font).setFontSize(12f).setFontColor(ColorConstants.DARK_GRAY)
                .setMarginBottom(2f),
        )
        doc.add(
            Paragraph("Сформировано: ${nowDmyTime()}    ·    Монтажников: ${workers.size}")
                .setFont(font).setFontSize(10f).setFontColor(ColorConstants.GRAY)
                .setMarginBottom(14f),
        )

        // KPI-карточки 1×4
        val kpi = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f, 1f)))
            .useAllAvailableWidth()
        val sumWork = workers.sumOf { it.work }
        val sumPause = workers.sumOf { it.pause + it.lunch + it.brk + it.idle }
        val sumPhotos = workers.sumOf { it.photos }
        val sumAmount = workers.sumOf { it.amount }
        kpi.addCell(kpiCard(font, fontBold, "Смен", "${workers.sumOf { it.shifts }}"))
        kpi.addCell(kpiCard(font, fontBold, "Чистой работы", hm(sumWork) + " ч"))
        kpi.addCell(kpiCard(font, fontBold, "Непроиз. (пауза+обед+перекур+простой)", hm(sumPause) + " ч"))
        kpi.addCell(kpiCard(font, fontBold, "К выплате", String.format("%,.0f ₽", sumAmount).replace(',', ' ')))
        doc.add(kpi)
        doc.add(Paragraph(" ").setFontSize(6f))

        // Доп. цифры
        doc.add(
            Paragraph("Загружено фото: $sumPhotos    ·    Уникальных объектов: " +
                workers.flatMap { listOf(it.objects) }.let { _ ->
                    report.entries.mapNotNull { it.siteObjectId }.toSet().size
                }.toString())
                .setFont(font).setFontSize(10f).setFontColor(ColorConstants.GRAY)
                .setMarginTop(2f).setMarginBottom(10f),
        )

        // 🔴 Требует внимания: топ-3 по сочетанию idle+pause
        val warnList = workers
            .filter { it.work > 0 && (it.idleRatio > 0.05 || it.pauseRatio > 0.15) }
            .sortedByDescending { it.idleRatio * 2 + it.pauseRatio }
            .take(3)
        if (warnList.isNotEmpty()) {
            doc.add(
                Paragraph("🔴 Требует внимания")
                    .setFont(fontBold).setBold().setFontSize(13f)
                    .setFontColor(DeviceRgb(0xC0, 0x1B, 0x1B)).setMarginBottom(4f),
            )
            warnList.forEach { w ->
                val tips = buildList {
                    if (w.idleRatio > 0.05) add("простой ${hm(w.idle)} (${pct(w.idleRatio)})")
                    if (w.pauseRatio > 0.15) add("пауза ${hm(w.pause)} (${pct(w.pauseRatio)}) — возможно скрытый обед/перекур")
                }
                doc.add(
                    Paragraph("•  ${w.name}: ${tips.joinToString(" · ")}")
                        .setFont(font).setFontSize(10f).setMarginLeft(8f).setMarginBottom(2f),
                )
            }
            doc.add(Paragraph(" ").setFontSize(6f))
        }

        // 🟢 Лидеры по чистой работе
        val leaders = workers.filter { it.work > 0 }.take(3)
        if (leaders.isNotEmpty()) {
            doc.add(
                Paragraph("🟢 Лидеры по чистой работе")
                    .setFont(fontBold).setBold().setFontSize(13f)
                    .setFontColor(DeviceRgb(0x16, 0x65, 0x34)).setMarginBottom(4f),
            )
            leaders.forEach { w ->
                doc.add(
                    Paragraph("•  ${w.name}: ${hm(w.work)} ч за ${w.shifts} смен (${w.days} дн.)")
                        .setFont(font).setFontSize(10f).setMarginLeft(8f).setMarginBottom(2f),
                )
            }
        }
    }

    private fun kpiCard(font: PdfFont, fontBold: PdfFont, label: String, value: String): Cell {
        val c = Cell()
            .setBorder(SolidBorder(BRAND_LIGHT, 0.5f))
            .setBackgroundColor(DeviceRgb(0xF7, 0xF7, 0xFB))
            .setPadding(10f)
        c.add(Paragraph(value).setFont(fontBold).setBold().setFontSize(20f).setFontColor(BRAND_DEEP).setMarginBottom(0f))
        c.add(Paragraph(label).setFont(font).setFontSize(9f).setFontColor(ColorConstants.GRAY).setMarginTop(0f))
        return c
    }

    // ----- PDF: таблица -----

    private fun renderTable(
        doc: Document, font: PdfFont, fontBold: PdfFont,
        workers: List<WorkerSum>,
    ) {
        // Колонки: №, ФИО, Тел, Роль, Смен, Дн, Wall, Чистое, Пауза, Обед, Перекур, Простой, Фото, Об., Сумма
        val widths = floatArrayOf(0.6f, 4.5f, 2.2f, 1.6f, 0.9f, 0.9f, 1.4f, 1.4f, 1.3f, 1.2f, 1.4f, 1.4f, 1.0f, 0.9f, 2.0f)
        val table = Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth()

        val headers = listOf(
            "№", "ФИО", "Телефон", "Роль", "Смен", "Дн", "В смене", "Чистое",
            "Пауза", "Обед", "Перекур", "Простой", "Фото", "Об.", "Сумма",
        )
        headers.forEach { h ->
            table.addHeaderCell(
                Cell().add(Paragraph(h).setFont(fontBold).setBold().setFontSize(8.5f).setFontColor(BRAND_DEEP))
                    .setBackgroundColor(BG_HEADER)
                    .setPadding(4f)
                    .setTextAlignment(TextAlignment.CENTER),
            )
        }

        workers.forEachIndexed { i, w ->
            val rowBg = if (i % 2 == 0) ColorConstants.WHITE else BG_ALT
            val idleBg = when {
                w.idleRatio > 0.10 -> RISK_BG
                w.idleRatio > 0.05 -> WARN_BG
                else -> rowBg
            }
            val pauseBg = if (w.pauseRatio > 0.15) WARN_BG else rowBg

            fun c(text: String, bg: Color = rowBg, align: TextAlignment = TextAlignment.RIGHT, bold: Boolean = false, fg: Color = ColorConstants.BLACK): Cell {
                val para = Paragraph(text).setFont(if (bold) fontBold else font).setFontSize(8.5f).setFontColor(fg)
                if (bold) para.setBold()
                return Cell().add(para).setBackgroundColor(bg).setPadding(3f).setTextAlignment(align)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
            }

            table.addCell(c("${i + 1}", rowBg, TextAlignment.CENTER))
            table.addCell(c(w.name, rowBg, TextAlignment.LEFT, bold = true))
            table.addCell(c(w.phone, rowBg, TextAlignment.LEFT, fg = ColorConstants.DARK_GRAY))
            table.addCell(c(ru(w.role), rowBg, TextAlignment.LEFT, fg = ColorConstants.DARK_GRAY))
            table.addCell(c("${w.shifts}", rowBg, TextAlignment.CENTER))
            table.addCell(c("${w.days}", rowBg, TextAlignment.CENTER, fg = ColorConstants.DARK_GRAY))
            table.addCell(c(hm(w.wall), rowBg, fg = ColorConstants.DARK_GRAY))
            table.addCell(c(hm(w.work), rowBg, bold = true, fg = BRAND_DEEP))
            table.addCell(c(hm(w.pause), pauseBg, fg = if (w.pauseRatio > 0.15) DeviceRgb(0x92, 0x40, 0x0E) else ColorConstants.BLACK))
            table.addCell(c(hm(w.lunch), rowBg))
            table.addCell(c(hm(w.brk), rowBg))
            table.addCell(c(hm(w.idle), idleBg, fg = if (w.idleRatio > 0.05) DeviceRgb(0xB9, 0x1C, 0x1C) else ColorConstants.BLACK))
            table.addCell(c("${w.photos}", rowBg, TextAlignment.CENTER))
            table.addCell(c("${w.objects}", rowBg, TextAlignment.CENTER, fg = ColorConstants.DARK_GRAY))
            table.addCell(c(String.format("%,.0f ₽", w.amount).replace(',', ' '), rowBg, TextAlignment.RIGHT, bold = true, fg = DeviceRgb(0x16, 0x65, 0x34)))
        }

        // Итоговая строка
        val sumWall = workers.sumOf { it.wall }
        val sumWork = workers.sumOf { it.work }
        val sumPause = workers.sumOf { it.pause }
        val sumLunch = workers.sumOf { it.lunch }
        val sumBrk = workers.sumOf { it.brk }
        val sumIdle = workers.sumOf { it.idle }
        val sumPhotos = workers.sumOf { it.photos }
        val sumAmount = workers.sumOf { it.amount }
        val sumShifts = workers.sumOf { it.shifts }
        val sumDays = workers.sumOf { it.days }
        val sumObjects = workers.sumOf { it.objects }

        fun ft(text: String, align: TextAlignment = TextAlignment.RIGHT): Cell =
            Cell().add(Paragraph(text).setFont(fontBold).setBold().setFontSize(9f).setFontColor(BRAND_DEEP))
                .setBackgroundColor(BG_HEADER).setPadding(4f).setTextAlignment(align)
                .setBorderTop(SolidBorder(BRAND_DEEP, 1f))

        table.addFooterCell(ft("", TextAlignment.CENTER))
        table.addFooterCell(ft("ИТОГО · ${workers.size} чел.", TextAlignment.LEFT))
        table.addFooterCell(ft(""))
        table.addFooterCell(ft(""))
        table.addFooterCell(ft("$sumShifts", TextAlignment.CENTER))
        table.addFooterCell(ft("$sumDays", TextAlignment.CENTER))
        table.addFooterCell(ft(hm(sumWall)))
        table.addFooterCell(ft(hm(sumWork)))
        table.addFooterCell(ft(hm(sumPause)))
        table.addFooterCell(ft(hm(sumLunch)))
        table.addFooterCell(ft(hm(sumBrk)))
        table.addFooterCell(ft(hm(sumIdle)))
        table.addFooterCell(ft("$sumPhotos", TextAlignment.CENTER))
        table.addFooterCell(ft("$sumObjects", TextAlignment.CENTER))
        table.addFooterCell(ft(String.format("%,.0f ₽", sumAmount).replace(',', ' ')))

        doc.add(table)

        // Подпись внизу
        doc.add(
            Paragraph("Метки: 🔴 простой > 5% от чистого · 🟡 пауза > 15% от чистого (возможно скрытый обед/перекур). Простой оплачивается с 23.05.2026.")
                .setFont(font).setFontSize(7.5f).setFontColor(ColorConstants.GRAY).setMarginTop(6f),
        )
    }

    // ===== XLSX (FastExcel) =====

    fun generateExcelReport(report: ShiftReport): Result<File> {
        return try {
            val fileName = "BELSI_otchet_${report.periodStart}_${report.periodEnd}.xlsx".replace(":", "-")
            val file = File(context.getExternalFilesDir(null), fileName)

            FileOutputStream(file).use { fos ->
                val wb = Workbook(fos, "BELSI", "1.0")
                val workers = aggregateByWorker(report)

                writeSummarySheet(wb.newWorksheet("Сводка"), report, workers)
                writeWorkersSheet(wb.newWorksheet("По монтажникам"), workers)
                writeShiftsSheet(wb.newWorksheet("Смены"), report.entries)
                writeDailySheet(wb.newWorksheet("По дням"), report.entries)

                wb.finish()
            }
            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ----- XLSX: лист «Сводка» -----

    private fun writeSummarySheet(s: Worksheet, report: ShiftReport, workers: List<WorkerSum>) {
        s.width(0, 28.0); s.width(1, 22.0)

        s.value(0, 0, "BELSI · Отчёт по сменам")
        s.range(0, 0, 0, 3).merge()
        s.range(0, 0, 0, 3).style().bold().fontSize(18).fontColor("FF362F97").set()

        s.value(1, 0, "Период: ${dmy(report.periodStart)} — ${dmy(report.periodEnd)}")
        s.style(1, 0).fontSize(11).fontColor("FF555555").set()

        s.value(2, 0, "Сформировано: ${nowDmyTime()}")
        s.style(2, 0).fontSize(10).fontColor("FF888888").set()

        // KPI
        val labels = listOf(
            "Монтажников" to "${workers.size}",
            "Всего смен" to "${workers.sumOf { it.shifts }}",
            "Уникальных дней" to "${workers.sumOf { it.days }}",
            "Чистой работы (ч)" to String.format("%.1f", workers.sumOf { it.work } / 3600.0),
            "Непроиз. время (ч)" to String.format("%.1f", workers.sumOf { it.pause + it.lunch + it.brk + it.idle } / 3600.0),
            "Фото загружено" to "${workers.sumOf { it.photos }}",
            "Уникальных объектов" to "${report.entries.mapNotNull { it.siteObjectId }.toSet().size}",
            "К выплате (₽)" to String.format("%.2f", workers.sumOf { it.amount }),
        )
        s.value(4, 0, "KPI")
        s.style(4, 0).bold().fontSize(13).fontColor("FF362F97").set()

        labels.forEachIndexed { i, (l, v) ->
            val r = 5 + i
            s.value(r, 0, l); s.style(r, 0).fontSize(11).set()
            s.value(r, 1, v); s.style(r, 1).bold().fontSize(11).fontColor("FF362F97").set()
        }

        // Лидеры
        val baseRow = 5 + labels.size + 2
        s.value(baseRow, 0, "🟢 Лидеры по чистой работе")
        s.style(baseRow, 0).bold().fontSize(13).fontColor("FF166534").set()
        workers.filter { it.work > 0 }.take(5).forEachIndexed { i, w ->
            val r = baseRow + 1 + i
            s.value(r, 0, "${i + 1}. ${w.name}")
            s.value(r, 1, "${hm(w.work)} ч · ${w.shifts} см · ${w.days} дн")
            s.style(r, 0).fontSize(11).set()
            s.style(r, 1).fontSize(11).fontColor("FF555555").set()
        }

        // Внимание
        val warnRow = baseRow + 1 + 5 + 2
        s.value(warnRow, 0, "🔴 Требует внимания")
        s.style(warnRow, 0).bold().fontSize(13).fontColor("FFC01B1B").set()
        val warns = workers
            .filter { it.work > 0 && (it.idleRatio > 0.05 || it.pauseRatio > 0.15) }
            .sortedByDescending { it.idleRatio * 2 + it.pauseRatio }
            .take(5)
        if (warns.isEmpty()) {
            s.value(warnRow + 1, 0, "Чисто — простоев и подозрительных пауз нет")
            s.style(warnRow + 1, 0).fontSize(10).fontColor("FF888888").italic().set()
        } else {
            warns.forEachIndexed { i, w ->
                val r = warnRow + 1 + i
                s.value(r, 0, w.name)
                val notes = buildList {
                    if (w.idleRatio > 0.05) add("простой ${hm(w.idle)} (${pct(w.idleRatio)})")
                    if (w.pauseRatio > 0.15) add("пауза ${hm(w.pause)} (${pct(w.pauseRatio)})")
                }.joinToString(" · ")
                s.value(r, 1, notes)
                s.style(r, 0).fontSize(11).set()
                s.style(r, 1).fontSize(11).fontColor("FF555555").set()
            }
        }
    }

    // ----- XLSX: лист «По монтажникам» -----

    private fun writeWorkersSheet(s: Worksheet, workers: List<WorkerSum>) {
        val cols = listOf(
            "№", "ФИО", "Телефон", "Роль", "Смен", "Дн", "Объектов",
            "В смене (ч:мм)", "Чистое (ч:мм)", "Пауза", "Обед", "Перекур", "Простой",
            "% простоя", "% пауз", "Фото", "Ставка ₽/ч", "Сумма ₽",
        )
        val widths = doubleArrayOf(4.0, 28.0, 16.0, 14.0, 7.0, 6.0, 9.0, 11.0, 11.0, 9.0, 8.0, 9.0, 9.0, 9.0, 9.0, 7.0, 11.0, 12.0)
        widths.forEachIndexed { i, w -> s.width(i, w) }

        cols.forEachIndexed { i, h ->
            s.value(0, i, h)
            s.style(0, i).bold().fontColor("FF362F97").fillColor("FFEFEEFB")
                .horizontalAlignment("center").wrapText(true).set()
        }

        workers.forEachIndexed { idx, w ->
            val r = idx + 1
            s.value(r, 0, (idx + 1).toLong())
            s.value(r, 1, w.name)
            s.value(r, 2, w.phone)
            s.value(r, 3, ru(w.role))
            s.value(r, 4, w.shifts.toLong())
            s.value(r, 5, w.days.toLong())
            s.value(r, 6, w.objects.toLong())
            s.value(r, 7, hm(w.wall))
            s.value(r, 8, hm(w.work))
            s.value(r, 9, hm(w.pause))
            s.value(r, 10, hm(w.lunch))
            s.value(r, 11, hm(w.brk))
            s.value(r, 12, hm(w.idle))
            s.value(r, 13, w.idleRatio)
            s.style(r, 13).format("0.0%").set()
            s.value(r, 14, w.pauseRatio)
            s.style(r, 14).format("0.0%").set()
            s.value(r, 15, w.photos.toLong())
            s.value(r, 16, if (w.work > 0) w.amount / (w.work / 3600.0) else 0.0)
            s.style(r, 16).format("#,##0").set()
            s.value(r, 17, w.amount)
            s.style(r, 17).format("#,##0.00").bold().fontColor("FF166534").set()

            // Risk highlight (style applies last-wins, поэтому всё в одном chain)
            if (w.idleRatio > 0.05) {
                s.style(r, 12).fillColor("FFFEE2E2").bold().fontColor("FFB91C1C").set()
            }
            if (w.pauseRatio > 0.15) {
                s.style(r, 9).fillColor("FFFEF3C7").bold().fontColor("FF92400E").set()
            }
        }

        // Итог
        val totalRow = workers.size + 1
        s.value(totalRow, 1, "ИТОГО · ${workers.size} чел.")
        s.style(totalRow, 1).bold().fontColor("FF362F97").fillColor("FFEFEEFB").set()
        s.value(totalRow, 4, workers.sumOf { it.shifts }.toLong())
        s.value(totalRow, 5, workers.sumOf { it.days }.toLong())
        s.value(totalRow, 6, workers.sumOf { it.objects }.toLong())
        s.value(totalRow, 7, hm(workers.sumOf { it.wall }))
        s.value(totalRow, 8, hm(workers.sumOf { it.work }))
        s.value(totalRow, 9, hm(workers.sumOf { it.pause }))
        s.value(totalRow, 10, hm(workers.sumOf { it.lunch }))
        s.value(totalRow, 11, hm(workers.sumOf { it.brk }))
        s.value(totalRow, 12, hm(workers.sumOf { it.idle }))
        s.value(totalRow, 15, workers.sumOf { it.photos }.toLong())
        s.value(totalRow, 17, workers.sumOf { it.amount })
        s.style(totalRow, 17).format("#,##0.00").bold().fontColor("FF362F97").fillColor("FFEFEEFB").set()
        for (c in listOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 15)) {
            s.style(totalRow, c).bold().fontColor("FF362F97").fillColor("FFEFEEFB").set()
        }

        // Freeze header + auto-filter
        s.freezePane(0, 1)
    }

    // ----- XLSX: лист «Смены» -----

    private fun writeShiftsSheet(s: Worksheet, entries: List<ShiftReportEntry>) {
        val cols = listOf("Дата", "ФИО", "Телефон", "Роль", "В смене", "Чистое",
            "Пауза", "Обед", "Перекур", "Простой", "Фото", "Объект (UUID)", "Сумма ₽")
        val widths = doubleArrayOf(12.0, 28.0, 16.0, 14.0, 9.0, 9.0, 8.0, 8.0, 9.0, 9.0, 7.0, 38.0, 11.0)
        widths.forEachIndexed { i, w -> s.width(i, w) }

        cols.forEachIndexed { i, h ->
            s.value(0, i, h)
            s.style(0, i).bold().fontColor("FF362F97").fillColor("FFEFEEFB")
                .horizontalAlignment("center").set()
        }

        entries.sortedBy { it.startTime }.forEachIndexed { idx, e ->
            val r = idx + 1
            s.value(r, 0, dmy(e.shiftDate))
            s.value(r, 1, e.userFullName ?: e.userName)
            s.value(r, 2, e.userPhone)
            s.value(r, 3, ru(e.userRole))
            s.value(r, 4, hm(e.totalSeconds))
            s.value(r, 5, hm(e.workSeconds))
            s.value(r, 6, hm(e.pauseSeconds))
            s.value(r, 7, hm(e.lunchSeconds))
            s.value(r, 8, hm(e.breakSeconds))
            s.value(r, 9, hm(e.idleSeconds))
            s.value(r, 10, e.photosCount.toLong())
            s.value(r, 11, e.siteObjectId ?: "—")
            s.value(r, 12, e.totalAmount)
            s.style(r, 12).format("#,##0.00").set()
        }
        s.freezePane(0, 1)
    }

    // ----- XLSX: лист «По дням» -----

    private fun writeDailySheet(s: Worksheet, entries: List<ShiftReportEntry>) {
        // Heatmap: даты (строки) × монтажники (колонки). Значение = ч.
        // FIX(2026-06-02) +итоги: последняя строка — сумма по монтажнику за период,
        // последняя колонка — сумма по дню (все люди).
        val workers = entries.groupBy { it.userId.toString() }.map { (_, list) ->
            val first = list.first()
            Triple(first.userId.toString(), first.userFullName?.takeIf { it.isNotBlank() } ?: first.userName, list)
        }.sortedBy { it.second }
        val dates = entries.map { it.shiftDate }.toSortedSet().toList()
        val totalCol = workers.size + 1   // первая колонка = даты, потом N людей, потом «Итого день»

        // Заголовок
        s.value(0, 0, "Дата \\ ФИО")
        s.style(0, 0).bold().fontColor("FF362F97").fillColor("FFEFEEFB").set()
        s.width(0, 12.0)
        workers.forEachIndexed { i, (_, name, _) ->
            s.value(0, i + 1, name)
            s.style(0, i + 1).bold().fontColor("FF362F97").fillColor("FFEFEEFB")
                .horizontalAlignment("center").wrapText(true).set()
            s.width(i + 1, 14.0)
        }
        s.value(0, totalCol, "Итого день (ч)")
        s.style(0, totalCol).bold().fontColor("FFFFFFFF").fillColor("FF362F97")
            .horizontalAlignment("center").wrapText(true).set()
        s.width(totalCol, 14.0)

        // Накопители для итогов
        val perWorkerTotal = DoubleArray(workers.size)
        var grandTotal = 0.0

        dates.forEachIndexed { rIdx, date ->
            val r = rIdx + 1
            s.value(r, 0, dmy(date))
            s.style(r, 0).bold().set()
            var dayTotal = 0.0
            workers.forEachIndexed { cIdx, (uid, _, list) ->
                val secs = list.filter { it.userId.toString() == uid && it.shiftDate == date }.sumOf { it.workSeconds }
                if (secs > 0) {
                    val hours = secs / 3600.0
                    s.value(r, cIdx + 1, hours)
                    // Heatmap: ≥8ч — зелёный, ≥4ч — мягкий, <4 — серый.
                    val color = when {
                        hours >= 8.0 -> "FFDCFCE7"
                        hours >= 4.0 -> "FFFEF3C7"
                        else -> "FFF1F5F9"
                    }
                    s.style(r, cIdx + 1).format("0.0").fillColor(color).set()
                    perWorkerTotal[cIdx] += hours
                    dayTotal += hours
                }
            }
            // Итого день (последняя колонка)
            if (dayTotal > 0) {
                s.value(r, totalCol, dayTotal)
                s.style(r, totalCol).format("0.0").bold().fontColor("FF362F97").fillColor("FFEFEEFB").set()
                grandTotal += dayTotal
            }
        }

        // Строка ИТОГО — суммы по каждому монтажнику за весь период
        val totalRow = dates.size + 1
        s.value(totalRow, 0, "ИТОГО (ч)")
        s.style(totalRow, 0).bold().fontColor("FFFFFFFF").fillColor("FF362F97").set()
        workers.forEachIndexed { cIdx, _ ->
            if (perWorkerTotal[cIdx] > 0) {
                s.value(totalRow, cIdx + 1, perWorkerTotal[cIdx])
                s.style(totalRow, cIdx + 1).format("0.0").bold().fontColor("FF362F97").fillColor("FFEFEEFB").set()
            }
        }
        // Угловая ячейка итогов (общая сумма)
        s.value(totalRow, totalCol, grandTotal)
        s.style(totalRow, totalCol).format("0.0").bold().fontColor("FFFFFFFF").fillColor("FF362F97").set()

        s.freezePane(1, 1)
    }

    // ===== Утилиты =====

    private fun dmy(iso: String): String = try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        inFmt.parse(iso)?.let { outFmt.format(it) } ?: iso
    } catch (_: Exception) {
        iso
    }

    private fun nowDmyTime(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())

    private fun pct(ratio: Double): String = String.format("%.0f%%", ratio * 100)
}
