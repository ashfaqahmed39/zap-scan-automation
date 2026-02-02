package utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.Proxy;
import org.zaproxy.clientapi.core.ApiResponseElement;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZapUtil {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() // so it won't crash if not found (optional)
            .load();

    private static final String zapAddress = getEnv("ZAP_ADDRESS", "127.0.0.1");
    private static final int zapPort = Integer.parseInt(getEnv("ZAP_PORT", "8080"));
    private static final String apiKey = getEnv("ZAP_API_KEY", "");
    private static final ClientApi clientApi = new ClientApi(zapAddress, zapPort, apiKey);
    public static final Proxy proxy = new Proxy()
            .setSslProxy(zapAddress + ":" + zapPort)
            .setHttpProxy(zapAddress + ":" + zapPort);

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    public static Proxy getSeleniumProxy() {
        return proxy;
    }

    public static void waitTillPassiveScanCompleted(int pollMs, int stableSeconds, int maxWaitSeconds) {
        int last = -1;
        int stableMs = 0;
        long start = System.currentTimeMillis();

        try {
            while (true) {
                int current = Integer.parseInt(
                        ((ApiResponseElement) clientApi.pscan.recordsToScan()).getValue()
                );
                System.out.println("ZAP Passive Scan - Records to scan: " + current);

                // ✅ best-case: truly finished
                if (current == 0) {
                    System.out.println("✅ Passive scan completed (0 remaining).");
                    return;
                }

                // stable tracking
                if (current == last) {
                    stableMs += pollMs;
                } else {
                    stableMs = 0;
                    last = current;
                }

                // stable long enough -> consider done
                if (stableMs >= stableSeconds * 1000) {
                    System.out.println("✅ Passive scan queue stable, continuing...");
                    return;
                }

                // safety timeout
                if ((System.currentTimeMillis() - start) > maxWaitSeconds * 1000L) {
                    System.out.println("⚠️ Passive scan wait timed out, continuing anyway...");
                    return;
                }

                Thread.sleep(pollMs);
            }
        } catch (ClientApiException | InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
    public static void generateZapReport(String siteToTest) {

        String title = "ZAP Security Report";
        String description = "Automated scan report";
        String template = "traditional-html-plus";
        String theme = "light";
        String contexts = null;

        String sections = "chart|alertcount|insights|passingrules|instancecount|statistics|alertdetails|params|sequencedetails";
        String includedRisks = "High|Medium|Low|Informational|False Positives";
        String includedConfidences = "High|Medium|Low";

        String reportDir = System.getProperty("user.dir") + File.separator + "reports";

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss"));
        String safeSite = siteToTest.replaceAll("https?://", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        String reportFileName = ts + "_ZAP-Report-" + safeSite;
        String reportFileNamePattern = null;

        String display = "false";

        try {
            Files.createDirectories(Path.of(reportDir));

            clientApi.reports.generate(
                    title, template, theme, description,
                    contexts, siteToTest, sections,
                    includedConfidences, includedRisks,
                    reportFileName, reportFileNamePattern,
                    reportDir, display
            );

            String reportPath = reportDir + File.separator + reportFileName + ".html";
            System.out.println("✅ Report: " + reportPath);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new File(reportPath).toURI());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
