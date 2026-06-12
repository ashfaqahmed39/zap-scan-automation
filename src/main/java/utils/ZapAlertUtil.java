package utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.zaproxy.clientapi.core.Alert;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZapAlertUtil {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static final String zapAddress = getEnv("ZAP_ADDRESS", "127.0.0.1");
    private static final int zapPort = Integer.parseInt(getEnv("ZAP_PORT", "8080"));
    private static final String apiKey = getEnv("ZAP_API_KEY", "");
    private static final ClientApi clientApi = new ClientApi(zapAddress, zapPort, apiKey);
    private static final int ALERT_PAGE_SIZE = 500;
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{([^{}]*)}", Pattern.DOTALL);
    private static final Pattern JSON_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    public record BaselineIgnoreRule(String pluginId, String alert, String risk, String urlRegex, String reason) {

        boolean matches(Alert zapAlert) {
            return matchesText(pluginId, zapAlert.getPluginId())
                    && matchesText(alert, alertName(zapAlert))
                    && matchesText(risk, riskName(zapAlert))
                    && matchesUrl(urlRegex, zapAlert.getUrl());
        }

        private static boolean matchesText(String expected, String actual) {
            return expected == null || expected.isBlank() || expected.equalsIgnoreCase(actual == null ? "" : actual);
        }

        private static boolean matchesUrl(String expectedRegex, String actual) {
            return expectedRegex == null || expectedRegex.isBlank()
                    || Pattern.compile(expectedRegex).matcher(actual == null ? "" : actual).find();
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    public static List<Alert> fetchAlerts(String baseUrl) throws ClientApiException {
        List<Alert> alerts = new ArrayList<>();
        int start = 0;

        while (true) {
            List<Alert> page = clientApi.getAlerts(baseUrl, start, ALERT_PAGE_SIZE);
            alerts.addAll(page);

            if (page.size() < ALERT_PAGE_SIZE) {
                return alerts;
            }

            start += ALERT_PAGE_SIZE;
        }
    }

    public static Map<String, Integer> countByRisk(List<Alert> alerts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("High", 0);
        counts.put("Medium", 0);
        counts.put("Low", 0);
        counts.put("Informational", 0);

        for (Alert alert : alerts) {
            if (alert.getRisk() == null) {
                continue;
            }

            String risk = alert.getRisk().name();
            counts.put(risk, counts.getOrDefault(risk, 0) + 1);
        }

        return counts;
    }

    public static List<BaselineIgnoreRule> loadBaselineIgnoreRules(String ignoreFile) throws IOException {
        String json = readFileFromClasspathOrDisk(ignoreFile);
        Matcher objectMatcher = JSON_OBJECT.matcher(json);
        List<BaselineIgnoreRule> rules = new ArrayList<>();

        while (objectMatcher.find()) {
            Map<String, String> fields = parseJsonFields(objectMatcher.group(1));
            if (fields.isEmpty()) {
                continue;
            }

            rules.add(new BaselineIgnoreRule(
                    fields.get("pluginId"),
                    fields.get("alert"),
                    normalizeNullableRisk(fields.get("risk")),
                    fields.get("urlRegex"),
                    fields.get("reason")
            ));
        }

        return rules;
    }

    public static List<Alert> filterIgnoredAlerts(List<Alert> alerts, List<BaselineIgnoreRule> ignoreRules) {
        List<Alert> actionable = new ArrayList<>();

        for (Alert alert : alerts) {
            if (!isIgnored(alert, ignoreRules)) {
                actionable.add(alert);
            }
        }

        return actionable;
    }

    public static void writeDeltaReport(List<Alert> actionableAlerts, List<Alert> allAlerts,
                                        List<BaselineIgnoreRule> ignoreRules, String reportPath) throws IOException {
        List<Alert> ignoredAlerts = new ArrayList<>();
        for (Alert alert : allAlerts) {
            if (isIgnored(alert, ignoreRules)) {
                ignoredAlerts.add(alert);
            }
        }

        Path path = Path.of(reportPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        String report = reportPath.toLowerCase().endsWith(".html")
                ? buildHtmlDeltaReport(actionableAlerts, ignoredAlerts)
                : buildMarkdownDeltaReport(actionableAlerts, ignoredAlerts);
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    public static void assertThresholds(Map<String, Integer> actual, Map<String, Integer> limits) {
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, Integer> limit : limits.entrySet()) {
            int actualCount = actual.getOrDefault(limit.getKey(), 0);
            if (actualCount > limit.getValue()) {
                failures.add(limit.getKey() + " alerts: actual=" + actualCount + ", limit=" + limit.getValue());
            }
        }

        if (!failures.isEmpty()) {
            throw new AssertionError("ZAP alert thresholds exceeded: " + String.join("; ", failures));
        }
    }

    private static String readFileFromClasspathOrDisk(String fileName) throws IOException {
        try (InputStream stream = ZapAlertUtil.class.getClassLoader().getResourceAsStream(fileName)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return Files.readString(Path.of(fileName), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseJsonFields(String objectJson) {
        Matcher fieldMatcher = JSON_FIELD.matcher(objectJson);
        Map<String, String> fields = new LinkedHashMap<>();

        while (fieldMatcher.find()) {
            fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
        }

        return fields;
    }

    private static boolean isIgnored(Alert alert, List<BaselineIgnoreRule> ignoreRules) {
        for (BaselineIgnoreRule rule : ignoreRules) {
            if (rule.matches(alert)) {
                return true;
            }
        }

        return false;
    }

    private static String buildMarkdownDeltaReport(List<Alert> actionableAlerts, List<Alert> ignoredAlerts) {
        StringBuilder report = new StringBuilder();
        report.append("# ZAP Alert Delta Report\n\n");
        report.append("## New Actionable Alerts (").append(actionableAlerts.size()).append(")\n");
        appendMarkdownAlerts(report, actionableAlerts);
        report.append("\n## Ignored Baseline Alerts (").append(ignoredAlerts.size()).append(")\n");
        appendMarkdownAlerts(report, ignoredAlerts);
        return report.toString();
    }

    private static void appendMarkdownAlerts(StringBuilder report, List<Alert> alerts) {
        if (alerts.isEmpty()) {
            report.append("None\n");
            return;
        }

        for (Alert alert : alerts) {
            report.append("- [").append(riskName(alert)).append("] ")
                    .append(alertName(alert))
                    .append(" | pluginId=").append(nullToEmpty(alert.getPluginId()))
                    .append(" | url=").append(nullToEmpty(alert.getUrl()))
                    .append("\n");
        }
    }

    private static String buildHtmlDeltaReport(List<Alert> actionableAlerts, List<Alert> ignoredAlerts) {
        StringBuilder report = new StringBuilder();
        report.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>ZAP Alert Delta Report</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:32px;background:#f7f8fa;color:#17202a;}")
                .append("h1,h2{margin-bottom:8px;}table{border-collapse:collapse;width:100%;background:#fff;margin-bottom:28px;}")
                .append("th,td{border:1px solid #d8dee4;padding:10px;text-align:left;vertical-align:top;}")
                .append("th{background:#eef2f6;} .medium{color:#9a6700;font-weight:bold;}.high{color:#cf222e;font-weight:bold;}")
                .append(".low{color:#0969da;font-weight:bold;}.informational{color:#57606a;font-weight:bold;}")
                .append("</style></head><body>")
                .append("<h1>ZAP Alert Delta Report</h1>");

        appendHtmlAlerts(report, "New Actionable Alerts", actionableAlerts);
        appendHtmlAlerts(report, "Ignored Baseline Alerts", ignoredAlerts);

        report.append("</body></html>");
        return report.toString();
    }

    private static void appendHtmlAlerts(StringBuilder report, String title, List<Alert> alerts) {
        report.append("<h2>").append(escapeHtml(title)).append(" (").append(alerts.size()).append(")</h2>");
        if (alerts.isEmpty()) {
            report.append("<p>None</p>");
            return;
        }

        report.append("<table><thead><tr><th>Risk</th><th>Alert</th><th>Plugin ID</th><th>URL</th></tr></thead><tbody>");
        for (Alert alert : alerts) {
            String risk = riskName(alert);
            report.append("<tr><td class=\"").append(escapeHtml(risk.toLowerCase())).append("\">").append(escapeHtml(risk)).append("</td>")
                    .append("<td>").append(escapeHtml(alertName(alert))).append("</td>")
                    .append("<td>").append(escapeHtml(nullToEmpty(alert.getPluginId()))).append("</td>")
                    .append("<td>").append(escapeHtml(nullToEmpty(alert.getUrl()))).append("</td></tr>");
        }
        report.append("</tbody></table>");
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String normalizeNullableRisk(String risk) {
        if (risk == null || risk.isBlank()) {
            return null;
        }

        return switch (risk.trim().toLowerCase()) {
            case "high" -> "High";
            case "medium" -> "Medium";
            case "low" -> "Low";
            case "info", "informational" -> "Informational";
            default -> throw new IllegalArgumentException("Unsupported ZAP risk in baseline ignore file: " + risk);
        };
    }

    private static String alertName(Alert alert) {
        String name = alert.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }

        return nullToEmpty(alert.getAlert());
    }

    private static String riskName(Alert alert) {
        return alert.getRisk() == null ? "" : alert.getRisk().name();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
