package com.claimswift.reportingservice.util;

import com.claimswift.reportingservice.dto.AdjusterPerformanceReport;
import com.claimswift.reportingservice.dto.ClaimSummaryReport;
import com.claimswift.reportingservice.dto.PaymentReport;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * PDF Generator Utility using OpenPDF
 */
@Slf4j
@Component
public class PdfGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generateClaimSummaryPdf(ClaimSummaryReport report) {
        log.info("Generating PDF for claim summary report");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE);
            Paragraph title = new Paragraph("Claim Summary Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Report period
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            document.add(new Paragraph("Report Period: " + report.getReportPeriod(), infoFont));
            document.add(new Paragraph("Generated At: " + report.getReportGeneratedAt().format(DATE_FORMATTER), infoFont));
            document.add(new Paragraph(" "));

            // Summary table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);

            addTableHeader(table, "Metric", "Value");
            addTableRow(table, "Total Claims", String.valueOf(report.getTotalClaims()));
            addTableRow(table, "Submitted Claims", String.valueOf(report.getSubmittedClaims()));
            addTableRow(table, "Under Review", String.valueOf(report.getUnderReviewClaims()));
            addTableRow(table, "Approved Claims", String.valueOf(report.getApprovedClaims()));
            addTableRow(table, "Rejected Claims", String.valueOf(report.getRejectedClaims()));
            addTableRow(table, "Paid Claims", String.valueOf(report.getPaidClaims()));
            addTableRow(table, "Total Claim Amount", formatCurrency(report.getTotalClaimAmount()));
            addTableRow(table, "Total Approved Amount", formatCurrency(report.getTotalApprovedAmount()));
            addTableRow(table, "Total Paid Amount", formatCurrency(report.getTotalPaidAmount()));
            addTableRow(table, "Average Claim Amount", formatCurrency(report.getAverageClaimAmount()));

            document.add(table);

            // Monthly comparison
            document.add(new Paragraph(" "));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            document.add(new Paragraph("Monthly Comparison", sectionFont));

            PdfPTable monthTable = new PdfPTable(3);
            monthTable.setWidthPercentage(100);
            monthTable.setSpacingBefore(10);

            addTableHeader(monthTable, "Period", "Claims", "Amount");
            addTableRow(monthTable, "This Month", String.valueOf(report.getClaimsThisMonth()), formatCurrency(report.getAmountThisMonth()));
            addTableRow(monthTable, "Last Month", String.valueOf(report.getClaimsLastMonth()), formatCurrency(report.getAmountLastMonth()));

            document.add(monthTable);

            document.close();
            log.info("PDF generated successfully for claim summary");
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for claim summary", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    public byte[] generatePaymentReportPdf(PaymentReport report) {
        log.info("Generating PDF for payment report");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE);
            Paragraph title = new Paragraph("Payment Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Report period
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            document.add(new Paragraph("Report Period: " + report.getReportPeriod(), infoFont));
            document.add(new Paragraph("Generated At: " + report.getReportGeneratedAt().format(DATE_FORMATTER), infoFont));
            document.add(new Paragraph(" "));

            // Summary table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);

            addTableHeader(table, "Metric", "Value");
            addTableRow(table, "Total Payments", String.valueOf(report.getTotalPayments()));
            addTableRow(table, "Approved Payments", String.valueOf(report.getApprovedPayments()));
            addTableRow(table, "Rejected Payments", String.valueOf(report.getRejectedPayments()));
            addTableRow(table, "Pending Payments", String.valueOf(report.getPendingPayments()));
            addTableRow(table, "Total Amount", formatCurrency(report.getTotalAmount()));
            addTableRow(table, "Approved Amount", formatCurrency(report.getApprovedAmount()));
            addTableRow(table, "Rejected Amount", formatCurrency(report.getRejectedAmount()));
            addTableRow(table, "Average Payment", formatCurrency(report.getAveragePaymentAmount()));

            document.add(table);

            // Monthly comparison
            document.add(new Paragraph(" "));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            document.add(new Paragraph("Monthly Comparison", sectionFont));

            PdfPTable monthTable = new PdfPTable(3);
            monthTable.setWidthPercentage(100);
            monthTable.setSpacingBefore(10);

            addTableHeader(monthTable, "Period", "Payments", "Amount");
            addTableRow(monthTable, "This Month", String.valueOf(report.getPaymentsThisMonth()), formatCurrency(report.getAmountThisMonth()));
            addTableRow(monthTable, "Last Month", String.valueOf(report.getPaymentsLastMonth()), formatCurrency(report.getAmountLastMonth()));

            document.add(monthTable);

            document.close();
            log.info("PDF generated successfully for payment report");
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for payment report", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    public byte[] generateAdjusterPerformancePdf(AdjusterPerformanceReport report) {
        log.info("Generating PDF for adjuster performance report");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE);
            Paragraph title = new Paragraph("Adjuster Performance Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Report period
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            document.add(new Paragraph("Report Period: " + report.getReportPeriod(), infoFont));
            document.add(new Paragraph("Generated At: " + report.getReportGeneratedAt().format(DATE_FORMATTER), infoFont));
            document.add(new Paragraph(" "));

            // Overall summary
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(50);
            summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            summaryTable.setSpacingBefore(10);

            addTableHeader(summaryTable, "Metric", "Value");
            addTableRow(summaryTable, "Total Adjusters", String.valueOf(report.getTotalAdjusters()));
            addTableRow(summaryTable, "Total Claims Processed", String.valueOf(report.getTotalClaimsProcessed()));
            addTableRow(summaryTable, "Total Amount Processed", formatCurrency(report.getTotalAmountProcessed()));

            document.add(summaryTable);
            document.add(new Paragraph(" "));

            // Individual adjuster performance
            if (report.getAdjusterPerformances() != null && !report.getAdjusterPerformances().isEmpty()) {
                Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
                document.add(new Paragraph("Individual Performance", sectionFont));
                document.add(new Paragraph(" "));

                PdfPTable performanceTable = new PdfPTable(8);
                performanceTable.setWidthPercentage(100);
                performanceTable.setSpacingBefore(10);

                addTableHeader(performanceTable, "Adjuster", "Assigned", "Approved", "Rejected", "Pending", "Total Amount", "Approval Rate", "Decisions");

                for (AdjusterPerformanceReport.AdjusterPerformance adjuster : report.getAdjusterPerformances()) {
                    addTableRow(performanceTable,
                            adjuster.getAdjusterName(),
                            String.valueOf(adjuster.getTotalClaimsAssigned()),
                            String.valueOf(adjuster.getClaimsApproved()),
                            String.valueOf(adjuster.getClaimsRejected()),
                            String.valueOf(adjuster.getClaimsPending()),
                            formatCurrency(adjuster.getTotalClaimAmount()),
                            adjuster.getApprovalRate() + "%",
                            String.valueOf(adjuster.getTotalDecisions()));
                }

                document.add(performanceTable);
            }

            document.close();
            log.info("PDF generated successfully for adjuster performance");
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for adjuster performance", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, String... values) {
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "$0.00";
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
