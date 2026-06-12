import io.github.cdimascio.dotenv.Dotenv;
import org.zaproxy.clientapi.core.Alert;
import org.zaproxy.clientapi.core.ClientApiException;
import org.testng.annotations.Test;

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

public class AlertThresholdGateTest {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static final Pattern THRESHOLD_ENTRY = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\d+)");

    private final String baseUrl = getEnv("APP_BASE_URL", "http://127.0.0.1:8080");

    @Test
    public void failWhenAlertThresholdExceeded() throws ClientApiException, IOException {
        Map<String, Integer> limits = loadThresholds();
        List<Alert> alerts = fetchAlerts(baseUrl);
        Map<String, Integer> actual = countByRisk(alerts);

        System.out.println("ZAP alert counts: " + actual);
        System.out.println("ZAP alert limits: " + limits);

        assertThresholds(actual, limits);
    }

    private static Map<String, Integer> loadThresholds() throws IOException {
        String json = readThresholdFile();
        Matcher matcher = THRESHOLD_ENTRY.matcher(json);
        Map<String, Integer> limits = new LinkedHashMap<>();

        while (matcher.find()) {
            limits.put(normalizeRisk(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }

        if (limits.isEmpty()) {
            throw new IllegalArgumentException("scan-thresholds.json must define at least one risk threshold");
        }

        return limits;
    }

    private static String readThresholdFile() throws IOException {
        try (InputStream stream = AlertThresholdGateTest.class.getClassLoader()
                .getResourceAsStream("scan-thresholds.json")) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return Files.readString(Path.of("scan-thresholds.json"), StandardCharsets.UTF_8);
    }

    private static String normalizeRisk(String risk) {
        return switch (risk.trim().toLowerCase()) {
            case "high" -> "High";
            case "medium" -> "Medium";
            case "low" -> "Low";
            case "info", "informational" -> "Informational";
            default -> throw new IllegalArgumentException("Unsupported ZAP risk in scan-thresholds.json: " + risk);
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
