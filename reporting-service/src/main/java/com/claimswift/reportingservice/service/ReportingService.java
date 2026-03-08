package com.claimswift.reportingservice.service;

import com.claimswift.reportingservice.client.ClaimServiceClient;
import com.claimswift.reportingservice.client.PaymentServiceClient;
import com.claimswift.reportingservice.dto.AdjusterPerformanceReport;
import com.claimswift.reportingservice.dto.ClaimSummaryReport;
import com.claimswift.reportingservice.dto.PaymentReport;
import com.claimswift.reportingservice.entity.ReportEvent;
import com.claimswift.reportingservice.repository.ReportEventRepository;
import com.claimswift.reportingservice.util.PdfGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportingService {

    private static final Set<String> PAYMENT_PENDING_STATUSES = Set.of("PENDING_VERIFICATION", "INITIATED", "PROCESSING");
    private static final Set<String> PAYMENT_REJECTED_STATUSES = Set.of("REJECTED", "FAILED", "CANCELLED");
    private static final Set<String> CLAIM_TERMINAL_STATUSES = Set.of("APPROVED", "REJECTED", "PAID", "PAYMENT_FAILED", "CANCELLED");

    private final ClaimServiceClient claimServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final PdfGenerator pdfGenerator;
    private final ReportEventRepository reportEventRepository;
    private final ObjectMapper objectMapper;

    public ReportingService(
            ClaimServiceClient claimServiceClient,
            PaymentServiceClient paymentServiceClient,
            PdfGenerator pdfGenerator,
            ReportEventRepository reportEventRepository,
            ObjectMapper objectMapper) {
        this.claimServiceClient = claimServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.pdfGenerator = pdfGenerator;
        this.reportEventRepository = reportEventRepository;
        this.objectMapper = objectMapper;
    }

    public ClaimSummaryReport generateClaimSummaryReport(String startDate, String endDate) {
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");

        List<Map<String, Object>> allClaims = safeList(claimServiceClient.getAllClaims());
        List<Map<String, Object>> claims = filterByDateRange(allClaims, start, end, "createdAt", "submittedAt", "incidentDate");

        long total = claims.size();
        long submitted = countByStatus(claims, "SUBMITTED");
        long underReview = countByStatus(claims, "UNDER_REVIEW");
        long approved = countByStatus(claims, "APPROVED");
        long rejected = countByStatus(claims, "REJECTED");
        long paid = countByStatus(claims, "PAID");

        BigDecimal totalAmount = sumDecimal(claims, "claimAmount");
        BigDecimal averageAmount = total > 0
                ? totalAmount.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal approvedAmount = claims.stream()
                .filter(c -> "APPROVED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                .map(c -> resolvedApprovedAmount(c, "approvedAmount", "claimAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = claims.stream()
                .filter(c -> "PAID".equalsIgnoreCase(String.valueOf(c.get("status"))))
                .map(c -> resolvedApprovedAmount(c, "approvedAmount", "claimAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byStatus = claims.stream()
                .collect(Collectors.groupingBy(
                        c -> normalizeKey(c.get("status")),
                        Collectors.counting()
                ));

        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        return ClaimSummaryReport.builder()
                .reportGeneratedAt(LocalDateTime.now())
                .reportPeriod(buildPeriod(startDate, endDate))
                .totalClaims(total)
                .submittedClaims(submitted)
                .underReviewClaims(underReview)
                .approvedClaims(approved)
                .rejectedClaims(rejected)
                .paidClaims(paid)
                .totalClaimAmount(totalAmount)
                .totalApprovedAmount(approvedAmount)
                .totalPaidAmount(paidAmount)
                .averageClaimAmount(averageAmount)
                .claimsByStatus(byStatus)
                .claimsThisMonth(countInMonth(allClaims, currentMonth, "createdAt", "submittedAt", "incidentDate"))
                .claimsLastMonth(countInMonth(allClaims, previousMonth, "createdAt", "submittedAt", "incidentDate"))
                .amountThisMonth(sumAmountInMonth(allClaims, currentMonth, "claimAmount", "createdAt", "submittedAt", "incidentDate"))
                .amountLastMonth(sumAmountInMonth(allClaims, previousMonth, "claimAmount", "createdAt", "submittedAt", "incidentDate"))
                .build();
    }

    public PaymentReport generatePaymentReport(String startDate, String endDate) {
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");

        List<Map<String, Object>> allPayments = safeList(paymentServiceClient.getAllPayments());
        List<Map<String, Object>> payments = filterByDateRange(allPayments, start, end, "createdAt", "updatedAt");

        long total = payments.size();
        long approved = countByStatus(payments, "APPROVED");
        long rejected = payments.stream().map(p -> normalizeKey(p.get("status"))).filter(PAYMENT_REJECTED_STATUSES::contains).count();
        long pending = payments.stream().map(p -> normalizeKey(p.get("status"))).filter(PAYMENT_PENDING_STATUSES::contains).count();

        BigDecimal totalAmount = sumDecimal(payments, "amount");
        BigDecimal average = total > 0
                ? totalAmount.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal approvedAmount = payments.stream()
                .filter(p -> "APPROVED".equalsIgnoreCase(String.valueOf(p.get("status"))))
                .map(p -> toBigDecimal(p.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rejectedAmount = payments.stream()
                .filter(p -> PAYMENT_REJECTED_STATUSES.contains(normalizeKey(p.get("status"))))
                .map(p -> toBigDecimal(p.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byStatus = payments.stream()
                .collect(Collectors.groupingBy(
                        p -> normalizeKey(p.get("status")),
                        Collectors.counting()
                ));

        Map<String, BigDecimal> amountByStatus = payments.stream()
                .collect(Collectors.groupingBy(
                        p -> normalizeKey(p.get("status")),
                        Collectors.reducing(BigDecimal.ZERO, p -> toBigDecimal(p.get("amount")), BigDecimal::add)
                ));

        Map<String, Long> paymentsByMethod = payments.stream()
                .collect(Collectors.groupingBy(
                        p -> normalizeKey(p.get("paymentMethod")),
                        Collectors.counting()
                ));

        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        return PaymentReport.builder()
                .reportGeneratedAt(LocalDateTime.now())
                .reportPeriod(buildPeriod(startDate, endDate))
                .totalPayments(total)
                .approvedPayments(approved)
                .rejectedPayments(rejected)
                .pendingPayments(pending)
                .totalAmount(totalAmount)
                .approvedAmount(approvedAmount)
                .rejectedAmount(rejectedAmount)
                .averagePaymentAmount(average)
                .paymentsByStatus(byStatus)
                .amountByStatus(amountByStatus)
                .paymentsByMethod(paymentsByMethod)
                .paymentsThisMonth(countInMonth(allPayments, currentMonth, "createdAt", "updatedAt"))
                .paymentsLastMonth(countInMonth(allPayments, previousMonth, "createdAt", "updatedAt"))
                .amountThisMonth(sumAmountInMonth(allPayments, currentMonth, "amount", "createdAt", "updatedAt"))
                .amountLastMonth(sumAmountInMonth(allPayments, previousMonth, "amount", "createdAt", "updatedAt"))
                .build();
    }

    public AdjusterPerformanceReport generateAdjusterPerformanceReport(String startDate, String endDate) {
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");

        List<Map<String, Object>> allClaims = safeList(claimServiceClient.getAllClaims());
        List<Map<String, Object>> claims = filterByDateRange(allClaims, start, end, "createdAt", "submittedAt", "incidentDate");

        Map<Long, List<Map<String, Object>>> byAdjuster = claims.stream()
                .filter(c -> c.get("adjusterId") != null)
                .filter(c -> toLong(c.get("adjusterId")) != null)
                .collect(Collectors.groupingBy(c -> Objects.requireNonNull(toLong(c.get("adjusterId")))));

        List<AdjusterPerformanceReport.AdjusterPerformance> performances = byAdjuster.entrySet().stream()
                .map(entry -> {
                    Long adjusterId = entry.getKey();
                    List<Map<String, Object>> items = entry.getValue();
                    long totalAssigned = items.size();
                    long approved = items.stream()
                            .filter(c -> "APPROVED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                            .count();
                    long rejected = items.stream()
                            .filter(c -> "REJECTED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                            .count();
                    long pending = items.stream()
                            .filter(c -> {
                                String status = normalizeKey(c.get("status"));
                                return "UNDER_REVIEW".equals(status) || "ADJUSTED".equals(status);
                            })
                            .count();
                    BigDecimal totalClaimAmount = items.stream()
                            .map(c -> toBigDecimal(c.get("claimAmount")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal approvedAmount = items.stream()
                            .filter(c -> "APPROVED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                            .map(c -> resolvedApprovedAmount(c, "approvedAmount", "claimAmount"))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal rejectedAmount = items.stream()
                            .filter(c -> "REJECTED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                            .map(c -> toBigDecimal(c.get("claimAmount")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    double approvalRate = totalAssigned > 0 ? (approved * 100.0) / totalAssigned : 0.0;
                    double avgProcessingDays = calculateAverageProcessingDays(items);

                    String adjusterName = firstNonBlank(items, "adjusterName");
                    if (adjusterName == null) {
                        adjusterName = "Adjuster-" + adjusterId;
                    }

                    return AdjusterPerformanceReport.AdjusterPerformance.builder()
                            .adjusterId(adjusterId)
                            .adjusterName(adjusterName)
                            .adjusterEmail(firstNonBlank(items, "adjusterEmail"))
                            .totalClaimsAssigned(totalAssigned)
                            .claimsApproved(approved)
                            .claimsRejected(rejected)
                            .claimsPending(pending)
                            .totalClaimAmount(totalClaimAmount)
                            .approvedAmount(approvedAmount)
                            .rejectedAmount(rejectedAmount)
                            .averageProcessingTimeDays(avgProcessingDays)
                            .approvalRate(roundDouble(approvalRate))
                            .totalDecisions(approved + rejected)
                            .build();
                })
                .toList();

        BigDecimal totalAmountProcessed = performances.stream()
                .map(AdjusterPerformanceReport.AdjusterPerformance::getTotalClaimAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double overallAverage = calculateAverageProcessingDays(claims);

        return AdjusterPerformanceReport.builder()
                .reportGeneratedAt(LocalDateTime.now())
                .reportPeriod(buildPeriod(startDate, endDate))
                .totalAdjusters((long) performances.size())
                .totalClaimsProcessed((long) claims.size())
                .totalAmountProcessed(totalAmountProcessed)
                .averageProcessingTime(overallAverage)
                .adjusterPerformances(performances)
                .build();
    }

    public byte[] exportClaimSummaryToPdf(String startDate, String endDate) {
        return pdfGenerator.generateClaimSummaryPdf(generateClaimSummaryReport(startDate, endDate));
    }

    public byte[] exportPaymentReportToPdf(String startDate, String endDate) {
        return pdfGenerator.generatePaymentReportPdf(generatePaymentReport(startDate, endDate));
    }

    public byte[] exportAdjusterPerformanceToPdf(String startDate, String endDate) {
        return pdfGenerator.generateAdjusterPerformancePdf(generateAdjusterPerformanceReport(startDate, endDate));
    }

    @Transactional
    public void recordClaimEvent(Map<String, Object> eventData) {
        persistEvent("CLAIM_EVENT", eventData);
    }

    @Transactional
    public void recordStatusChange(Map<String, Object> statusChangeData) {
        persistEvent("STATUS_CHANGE", statusChangeData);
    }

    private void persistEvent(String eventCategory, Map<String, Object> payload) {
        LocalDateTime eventTimestamp = resolveDateTime(payload, "timestamp", "eventTimestamp", "createdAt");
        if (eventTimestamp == null) {
            eventTimestamp = LocalDateTime.now();
        }

        String oldStatus = toTrimmedString(payload.get("oldStatus"));
        String newStatus = toTrimmedString(payload.get("newStatus"));
        if (newStatus == null) {
            newStatus = toTrimmedString(payload.get("status"));
        }

        String eventType = toTrimmedString(payload.get("eventType"));
        if (eventType == null && oldStatus != null && newStatus != null) {
            eventType = oldStatus + "->" + newStatus;
        }

        ReportEvent event = ReportEvent.builder()
                .eventCategory(eventCategory)
                .claimId(toLong(payload.get("claimId")))
                .claimNumber(toTrimmedString(payload.get("claimNumber")))
                .eventType(eventType)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .eventTimestamp(eventTimestamp)
                .payloadJson(toJson(payload))
                .build();

        reportEventRepository.save(event);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize event payload for reporting: {}", ex.getMessage());
            return "{}";
        }
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> input) {
        return input == null ? Collections.emptyList() : input;
    }

    private long countByStatus(List<Map<String, Object>> items, String status) {
        return items.stream()
                .map(i -> normalizeKey(i.get("status")))
                .filter(status::equals)
                .count();
    }

    private BigDecimal sumDecimal(List<Map<String, Object>> items, String key) {
        return items.stream()
                .map(i -> toBigDecimal(i.get(key)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal resolvedApprovedAmount(Map<String, Object> item, String approvedKey, String fallbackKey) {
        BigDecimal approvedAmount = toBigDecimal(item.get(approvedKey));
        if (approvedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return approvedAmount;
        }
        return toBigDecimal(item.get(fallbackKey));
    }

    private String normalizeKey(Object value) {
        String raw = toTrimmedString(value);
        if (raw == null) {
            return "UNKNOWN";
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private String buildPeriod(String startDate, String endDate) {
        if (startDate == null && endDate == null) {
            return "All Time";
        }
        if (startDate != null && endDate != null) {
            return startDate + " to " + endDate;
        }
        if (startDate != null) {
            return "From " + startDate;
        }
        return "Up to " + endDate;
    }

    private List<Map<String, Object>> filterByDateRange(
            List<Map<String, Object>> items,
            LocalDate startDate,
            LocalDate endDate,
            String... dateKeys) {
        if (startDate == null && endDate == null) {
            return items;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : items) {
            LocalDate itemDate = resolveDate(item, dateKeys);
            if (itemDate == null) {
                continue;
            }
            if (startDate != null && itemDate.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && itemDate.isAfter(endDate)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private long countInMonth(List<Map<String, Object>> items, YearMonth month, String... dateKeys) {
        return items.stream()
                .map(item -> resolveDate(item, dateKeys))
                .filter(Objects::nonNull)
                .filter(date -> YearMonth.from(date).equals(month))
                .count();
    }

    private BigDecimal sumAmountInMonth(List<Map<String, Object>> items, YearMonth month, String amountKey, String... dateKeys) {
        return items.stream()
                .filter(item -> {
                    LocalDate date = resolveDate(item, dateKeys);
                    return date != null && YearMonth.from(date).equals(month);
                })
                .map(item -> toBigDecimal(item.get(amountKey)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate resolveDate(Map<String, Object> item, String... dateKeys) {
        LocalDateTime dateTime = resolveDateTime(item, dateKeys);
        if (dateTime != null) {
            return dateTime.toLocalDate();
        }

        for (String key : dateKeys) {
            LocalDate parsed = parseFlexibleDate(item.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDateTime resolveDateTime(Map<String, Object> item, String... dateKeys) {
        for (String key : dateKeys) {
            LocalDateTime parsed = parseFlexibleDateTime(item.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDate parseFlexibleDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof String raw) {
            if (raw.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(raw);
            } catch (DateTimeParseException ignored) {
                LocalDateTime dateTime = parseFlexibleDateTime(raw);
                return dateTime == null ? null : dateTime.toLocalDate();
            }
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            try {
                int year = Integer.parseInt(String.valueOf(list.get(0)));
                int month = Integer.parseInt(String.valueOf(list.get(1)));
                int day = Integer.parseInt(String.valueOf(list.get(2)));
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime parseFlexibleDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneId.systemDefault());
        }
        if (value instanceof String raw) {
            String trimmed = raw.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return OffsetDateTime.parse(trimmed).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
            }
            try {
                return Instant.parse(trimmed).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
            }
            return null;
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            try {
                int year = Integer.parseInt(String.valueOf(list.get(0)));
                int month = Integer.parseInt(String.valueOf(list.get(1)));
                int day = Integer.parseInt(String.valueOf(list.get(2)));
                int hour = list.size() > 3 ? Integer.parseInt(String.valueOf(list.get(3))) : 0;
                int minute = list.size() > 4 ? Integer.parseInt(String.valueOf(list.get(4))) : 0;
                int second = list.size() > 5 ? Integer.parseInt(String.valueOf(list.get(5))) : 0;
                int nano = list.size() > 6 ? Integer.parseInt(String.valueOf(list.get(6))) : 0;
                return LocalDateTime.of(year, month, day, hour, minute, second, nano);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (value instanceof Map<?, ?> map) {
            try {
                Object yearObj = map.get("year");
                Object monthObj = map.get("monthValue") != null ? map.get("monthValue") : map.get("month");
                Object dayObj = map.get("dayOfMonth") != null ? map.get("dayOfMonth") : map.get("day");
                if (yearObj == null || monthObj == null || dayObj == null) {
                    return null;
                }
                int year = Integer.parseInt(String.valueOf(yearObj));
                int month = Integer.parseInt(String.valueOf(monthObj));
                int day = Integer.parseInt(String.valueOf(dayObj));
                int hour = map.get("hour") != null ? Integer.parseInt(String.valueOf(map.get("hour"))) : 0;
                int minute = map.get("minute") != null ? Integer.parseInt(String.valueOf(map.get("minute"))) : 0;
                int second = map.get("second") != null ? Integer.parseInt(String.valueOf(map.get("second"))) : 0;
                int nano = map.get("nano") != null ? Integer.parseInt(String.valueOf(map.get("nano"))) : 0;
                return LocalDateTime.of(year, month, day, hour, minute, second, nano);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDate parseDate(String rawDate, String fieldName) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid " + fieldName + " format. Use YYYY-MM-DD.");
        }
    }

    private String firstNonBlank(List<Map<String, Object>> items, String key) {
        return items.stream()
                .map(item -> toTrimmedString(item.get(key)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private double calculateAverageProcessingDays(List<Map<String, Object>> items) {
        List<Double> processingDays = items.stream()
                .map(this::resolveProcessingDays)
                .filter(Objects::nonNull)
                .toList();

        if (processingDays.isEmpty()) {
            return 0.0;
        }

        double average = processingDays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return roundDouble(average);
    }

    private Double resolveProcessingDays(Map<String, Object> claim) {
        String status = normalizeKey(claim.get("status"));
        if (!CLAIM_TERMINAL_STATUSES.contains(status)) {
            return null;
        }

        LocalDateTime start = resolveDateTime(claim, "submittedAt", "createdAt");
        LocalDateTime end = resolveDateTime(claim, "paidAt", "approvedAt", "updatedAt");
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }

        long hours = Duration.between(start, end).toHours();
        return BigDecimal.valueOf(hours)
                .divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String resolved = String.valueOf(value).trim();
        return resolved.isBlank() ? null : resolved;
    }
}
