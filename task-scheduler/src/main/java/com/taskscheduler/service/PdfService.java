package com.taskscheduler.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * PDF report generation service using OpenPDF.
 *
 * Generates real PDF files with:
 *   - Title and metadata
 *   - Revenue data table with regional breakdown
 *   - Summary statistics
 *   - Saves to configurable output directory
 */
@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final Random random = new Random();

    @Value("${report.output-dir:/tmp/reports}")
    private String outputDir;

    /**
     * Generate a revenue report PDF and save to disk.
     *
     * @param reportName the name/title for the report
     * @return the absolute file path of the generated PDF
     */
    public String generateRevenueReport(String reportName) {
        // Ensure output directory exists
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(FILE_TS);
        String fileName = "report_" + timestamp + ".pdf";
        String filePath = new File(dir, fileName).getAbsolutePath();

        log.info("═══════════════════════════════════════════════");
        log.info("  PDF REPORT GENERATION: {}", reportName);
        log.info("  Output: {}", filePath);
        log.info("═══════════════════════════════════════════════");

        try {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // ── Title ──
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(33, 37, 41));
            Paragraph title = new Paragraph(reportName, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            // ── Subtitle ──
            Font subtitleFont = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(108, 117, 125));
            Paragraph subtitle = new Paragraph(
                    "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(25);
            document.add(subtitle);

            // ── Revenue Data Table ──
            List<Map<String, Object>> data = generateRevenueData();
            PdfPTable table = createRevenueTable(data);
            document.add(table);

            // ── Summary ──
            document.add(new Paragraph(" "));
            BigDecimal total = data.stream()
                    .map(d -> (BigDecimal) d.get("revenue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Font summaryFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            document.add(new Paragraph("Total Revenue: $" + total, summaryFont));
            document.add(new Paragraph("Regions: " + data.size(), summaryFont));
            document.add(new Paragraph("Report: " + reportName, summaryFont));

            document.close();
            log.info("✓ PDF generated successfully: {}", filePath);

        } catch (Exception e) {
            log.error("✗ PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF report: " + reportName, e);
        }

        return filePath;
    }

    private PdfPTable createRevenueTable(List<Map<String, Object>> data) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);

        // Header row
        Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
        Color headerBg = new Color(52, 58, 64);

        for (String header : new String[]{"Region", "Revenue ($)", "Transactions"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        // Data rows
        Font dataFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        boolean alternate = false;

        for (Map<String, Object> row : data) {
            Color rowBg = alternate ? new Color(248, 249, 250) : Color.WHITE;
            alternate = !alternate;

            addDataCell(table, String.valueOf(row.get("region")), dataFont, rowBg, Element.ALIGN_LEFT);
            addDataCell(table, "$" + row.get("revenue"), dataFont, rowBg, Element.ALIGN_RIGHT);
            addDataCell(table, String.valueOf(row.get("transactions")), dataFont, rowBg, Element.ALIGN_CENTER);
        }

        return table;
    }

    private void addDataCell(PdfPTable table, String text, Font font, Color bg, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private List<Map<String, Object>> generateRevenueData() {
        String[] regions = {"North America", "Europe", "Asia Pacific", "Latin America", "Middle East"};
        List<Map<String, Object>> data = new ArrayList<>();

        for (String region : regions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("region", region);
            entry.put("revenue", BigDecimal.valueOf(random.nextDouble() * 500_000 + 50_000)
                    .setScale(2, RoundingMode.HALF_UP));
            entry.put("transactions", random.nextInt(3000) + 500);
            data.add(entry);
        }

        return data;
    }
}
