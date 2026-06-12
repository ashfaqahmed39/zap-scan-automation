package utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.zaproxy.clientapi.core.*;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ZapUtil {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() // so it won't crash if not found (optional)
            .load();

    private static final String zapAddress = getEnv("ZAP_ADDRESS", "127.0.0.1");
    private static final int zapPort = Integer.parseInt(getEnv("ZAP_PORT", "8080"));
    private static final String apiKey = getEnv("ZAP_API_KEY", "");
    private static final ClientApi clientApi = new ClientApi(zapAddress, zapPort, apiKey);
    private static ApiResponse apiResponse;
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
//    public static void addURLToScanTree(String site_to_test) throws ClientApiException {
//        clientApi.core.accessUrl(site_to_test, "false");
//        if(getUrlsFromScanTree().contains(site_to_test))
//            System.out.println(site_to_test+ " has been added to scan tree");
//        else
//            throw new RuntimeException(site_to_test +" not added to scan tree, active scan will not be possible");
//    }
//
//    public static List<String> getUrlsFromScanTree() throws ClientApiException {
//        apiResponse=clientApi.core.urls();
//        List<ApiResponse> responses=((ApiResponseList)apiResponse).getItems();
//        return responses.stream().map(r->((ApiResponseElement)r).getValue()).collect(Collectors.toList());
//    }
//
//    public static void performSpidering(String site_to_test, String contextName) throws ClientApiException {
//        apiResponse=clientApi.spider.scan(site_to_test,null,null,null,null);
//        String spiderScanId=((ApiResponseElement)apiResponse).getValue();
//
//        apiResponse=clientApi.spider.status(spiderScanId);
//        String spiderScanStatus=((ApiResponseElement)apiResponse).getValue();
//
//        while (!spiderScanStatus.equals("100")){
//            apiResponse=clientApi.spider.status(spiderScanId);
//            spiderScanStatus=((ApiResponseElement)apiResponse).getValue();
//            System.out.println("Spidering is in progress, current status="+spiderScanStatus);
//        }
//
//        waitTillPassiveScanCompleted();
//
//        System.out.println("starting active scan--");
//        performActiveScan(site_to_test, contextName);
//    }

//    public static void performActiveScan(String site_to_test, String contextName) throws ClientApiException {
//        if (contextName == null || contextName.isBlank()) {
//            performActiveScan(site_to_test);
//            return;
//        }
//
//        String url = site_to_test;
//        String recurse = null;
//        String inscopeonly = null;
//        String scanpolicyname = null;
//        String method = null;
//        String postdata = null;
//        Integer contextId= getContextAfterImporting(contextName);
//        System.out.println("context id imported "+ contextId);
//        apiResponse = clientApi.ascan.scan(url, recurse, inscopeonly, scanpolicyname, method, postdata, contextId);
//        String scanId = ((ApiResponseElement) apiResponse).getValue();
//
//        waitTillActiveScanIsCompleted(scanId);
//
//        apiResponse=clientApi.context.removeContext(contextName);
//        if(((ApiResponseElement)apiResponse).getValue().equals("OK"))
//            System.out.println("context has been removed");
//        else
//            throw new RuntimeException("context was not removed after active scan");
//
//
//    }

    public static void performActiveScan(String site_to_test) throws ClientApiException {
        String url = site_to_test;
        String recurse = null;
        String inscopeonly = null;
        String scanpolicyname = null;
        String method = null;
        String postdata = null;

        apiResponse = clientApi.ascan.scan(url, recurse, inscopeonly, scanpolicyname, method, postdata);
        String scanId = ((ApiResponseElement) apiResponse).getValue();
        waitTillActiveScanIsCompleted(scanId);
    }

    public static void performTraditionalSpider(String siteToTest, String contextName) throws ClientApiException {
        String maxChildren = null;
        String recurse = "true";
        String subtreeOnly = "true";

        apiResponse = clientApi.spider.scan(siteToTest, maxChildren, recurse, contextName, subtreeOnly);
        String spiderScanId = ((ApiResponseElement) apiResponse).getValue();

        waitTillSpiderIsCompleted(spiderScanId);
    }

    public static void performAjaxSpider(String siteToTest, String contextName) throws ClientApiException {
        String inScope = "true";

        clientApi.ajaxSpider.setOptionBrowserId("htmlunit");
        clientApi.ajaxSpider.scan(siteToTest, inScope, contextName, null);

        waitTillAjaxSpiderIsCompleted();
    }

    public static void performForcedBrowse(String siteToTest) throws ClientApiException {
        List<String> paths = getForcedBrowsePaths();

        System.out.println("Starting forced browse with " + paths.size() + " paths");
        for (String path : paths) {
            String url = resolveForcedBrowseUrl(siteToTest, path);
            clientApi.core.accessUrl(url, "true");
            System.out.println("Forced browse requested: " + url);
        }
        System.out.println("Forced browse completed");
    }

    public static void performForcedBrowseWithSelenium(WebDriver driver, String siteToTest) {
        List<String> paths = getForcedBrowsePaths();

        System.out.println("Starting authenticated forced browse with " + paths.size() + " paths");
        for (String path : paths) {
            String url = resolveForcedBrowseUrl(siteToTest, path);
            driver.get(url);
            System.out.println("Authenticated forced browse requested: " + url);
        }
        System.out.println("Authenticated forced browse completed");
    }

    public static String createScopeContextForTarget(String siteToTest) throws ClientApiException {
        String contextName = "ctx-" + sanitizeContextName(siteToTest);
        String host = URI.create(siteToTest).getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Unable to determine host from APP_BASE_URL: " + siteToTest);
        }

        String includeRegex = "^https?://" + java.util.regex.Pattern.quote(host) + "(:\\\\d+)?(/.*)?$";

        try {
            clientApi.context.removeContext(contextName);
        } catch (Exception ignored) {
        }

        clientApi.context.newContext(contextName);
        clientApi.context.includeInContext(contextName, includeRegex);
        clientApi.context.setContextInScope(contextName, "true");

        return contextName;
    }

    public static void performActiveScanInScope(String siteToTest) throws ClientApiException {
        String recurse = "true";
        String inScopeOnly = "true";
        String scanPolicyName = null;
        String method = null;
        String postData = null;

        apiResponse = clientApi.ascan.scan(siteToTest, recurse, inScopeOnly, scanPolicyName, method, postData);
        String scanId = ((ApiResponseElement) apiResponse).getValue();
        waitTillActiveScanIsCompleted(scanId);
    }

    public static List<String> getUrlsFromScanTree() throws ClientApiException {
        apiResponse = clientApi.core.urls();
        List<ApiResponse> responses = ((ApiResponseList) apiResponse).getItems();
        return responses.stream().map(r -> ((ApiResponseElement) r).getValue()).collect(Collectors.toList());
    }

    public static int getAlertCountForBaseUrl(String baseUrl) throws ClientApiException {
        ApiResponse response = clientApi.core.numberOfAlerts(baseUrl);
        return Integer.parseInt(((ApiResponseElement) response).getValue());
    }

    private static Integer getContextAfterImporting(String contextName) throws ClientApiException {
        apiResponse=clientApi.context.importContext(contextName);
        return Integer.parseInt(((ApiResponseElement)apiResponse).getValue());
    }

    private static void waitTillActiveScanIsCompleted(String scanId) throws ClientApiException {
        apiResponse=clientApi.ascan.status(scanId);
        String status=((ApiResponseElement)apiResponse).getValue();

        while (!status.equals("100")){
            apiResponse=clientApi.ascan.status(scanId);
            status=((ApiResponseElement)apiResponse).getValue();
            System.out.println("Active scan is in progress");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for active scan completion", e);
            }
        }

        System.out.println("Active scan has completed");
    }

    private static void waitTillSpiderIsCompleted(String spiderScanId) throws ClientApiException {
        apiResponse = clientApi.spider.status(spiderScanId);
        String status = ((ApiResponseElement) apiResponse).getValue();

        while (!"100".equals(status)) {
            apiResponse = clientApi.spider.status(spiderScanId);
            status = ((ApiResponseElement) apiResponse).getValue();
            System.out.println("Spider scan is in progress: " + status + "%");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for spider completion", e);
            }
        }

        System.out.println("Spider scan has completed");
    }

    private static void waitTillAjaxSpiderIsCompleted() throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.ajaxSpider.status()).getValue();

        while (!"stopped".equalsIgnoreCase(status)) {
            status = ((ApiResponseElement) clientApi.ajaxSpider.status()).getValue();
            System.out.println("Ajax spider is in progress: " + status);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Ajax spider completion", e);
            }
        }

        System.out.println("Ajax spider has completed");
    }

    private static String sanitizeContextName(String siteToTest) {
        return siteToTest
                .replaceAll("https?://", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String resolveForcedBrowseUrl(String siteToTest, String path) {
        URI baseUri = URI.create(siteToTest.endsWith("/") ? siteToTest : siteToTest + "/");
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return baseUri.resolve(normalizedPath).toString();
    }

    private static List<String> getForcedBrowsePaths() {
        String configuredPaths = getEnv("ZAP_FORCED_BROWSE_PATHS", "");
        if (!configuredPaths.isBlank()) {
            return Arrays.stream(configuredPaths.split(","))
                    .map(String::trim)
                    .filter(path -> !path.isBlank())
                    .collect(Collectors.toList());
        }

        return List.of(
                "/admin",
                "/administrator",
                "/backup",
                "/backups",
                "/config",
                "/debug",
                "/dev",
                "/login",
                "/logs",
                "/old",
                "/private",
                "/server-status",
                "/staging",
                "/test",
                "/uploads",
                "/.env",
                "/.git/HEAD",
                "/robots.txt",
                "/sitemap.xml"
        );
    }

//    public static void cleanTheScanTree() throws ClientApiException {
//        List<String> urls=getUrlsFromScanTree();
//        for (String url:urls){
//            if(getUrlsFromScanTree().stream().anyMatch(s->s.contains(url))){
//                clientApi.core.deleteSiteNode(url,"","");
//            }
//        }
//        if(getUrlsFromScanTree().size()==0)
//            System.out.println("scan tree has been cleared successfully");
//        else
//            throw new RuntimeException("scan tree was not cleared");
//
//    }
    public static void generateZapReport(String siteToTest) {

        String title = "ZAP Security Report";
        String description = "Automated scan report";
        String template = "traditional-html";
        String theme = null;
        String contexts = null;

        String sections = null;
        String includedRisks = null;
        String includedConfidences = null;

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
