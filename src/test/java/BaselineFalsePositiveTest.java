import io.github.cdimascio.dotenv.Dotenv;
import org.zaproxy.clientapi.core.Alert;
import org.zaproxy.clientapi.core.ClientApiException;
import org.testng.annotations.Test;
import utils.ZapAlertUtil.BaselineIgnoreRule;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.ZapAlertUtil.assertThresholds;
import static utils.ZapAlertUtil.countByRisk;
import static utils.ZapAlertUtil.fetchAlerts;
import static utils.ZapAlertUtil.filterIgnoredAlerts;
import static utils.ZapAlertUtil.loadBaselineIgnoreRules;
import static utils.ZapAlertUtil.writeDeltaReport;

public class BaselineFalsePositiveTest {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static final Pattern THRESHOLD_ENTRY = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\d+)");

    private final String baseUrl = getEnv("APP_BASE_URL", "http://127.0.0.1:8080");
    private final String thresholdFile = getEnv("SCAN_THRESHOLDS_FILE", "scan-thresholds.json");
    private final String baselineIgnoreFile = getEnv("ZAP_BASELINE_IGNORE_FILE", "zap-baseline-ignore-rules.json");
    private final String deltaReportPath = getEnv("ZAP_DELTA_REPORT_PATH", "reports/zap-alert-delta-report.html");

    @Test
    public void failOnlyOnNewActionableAlertsAboveThreshold() throws ClientApiException, IOException {
        Map<String, Integer> limits = loadThresholds(thresholdFile);
        List<BaselineIgnoreRule> ignoreRules = loadBaselineIgnoreRules(baselineIgnoreFile);
        List<Alert> allAlerts = fetchAlerts(baseUrl);
        List<Alert> actionableAlerts = filterIgnoredAlerts(allAlerts, ignoreRules);
        Map<String, Integer> actionableCounts = countByRisk(actionableAlerts);

        writeDeltaReport(actionableAlerts, allAlerts, ignoreRules, deltaReportPath);
        openReport(deltaReportPath);

        System.out.println("ZAP total alert counts: " + countByRisk(allAlerts));
        System.out.println("ZAP new actionable alert counts: " + actionableCounts);
        System.out.println("ZAP alert limits: " + limits);
        System.out.println("ZAP delta report: " + deltaReportPath);

        assertThresholds(actionableCounts, limits);
    }

    private static Map<String, Integer> loadThresholds(String thresholdFile) throws IOException {
        String json = readFileFromClasspathOrDisk(thresholdFile);
        Matcher matcher = THRESHOLD_ENTRY.matcher(json);
        Map<String, Integer> limits = new LinkedHashMap<>();

        while (matcher.find()) {
            limits.put(normalizeRisk(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }

        if (limits.isEmpty()) {
            throw new IllegalArgumentException(thresholdFile + " must define at least one risk threshold");
        }

        return limits;
    }

    private static String readFileFromClasspathOrDisk(String fileName) throws IOException {
        try (InputStream stream = BaselineFalsePositiveTest.class.getClassLoader().getResourceAsStream(fileName)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return Files.readString(Path.of(fileName), StandardCharsets.UTF_8);
    }

    private static void openReport(String reportPath) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }

        try {
            Desktop.getDesktop().browse(Path.of(reportPath).toAbsolutePath().toUri());
        } catch (IOException e) {
            System.out.println("Unable to auto-open ZAP delta report: " + e.getMessage());
        }
    }

    private static String normalizeRisk(String risk) {
        return switch (risk.trim().toLowerCase()) {
            case "high" -> "High";
            case "medium" -> "Medium";
            case "low" -> "Low";
            case "info", "informational" -> "Informational";
            default -> throw new IllegalArgumentException("Unsupported ZAP risk in " + risk);
        };
    }

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }
}
