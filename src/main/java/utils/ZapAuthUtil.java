package utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.zaproxy.clientapi.core.*;

import java.net.URI;
import java.time.Duration;

public class ZapAuthUtil {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static final String zapAddress = getEnv("ZAP_ADDRESS", "127.0.0.1");
    private static final int zapPort = Integer.parseInt(getEnv("ZAP_PORT", "8080"));
    private static final String apiKey = getEnv("ZAP_API_KEY", "");
    private static final ClientApi clientApi = new ClientApi(zapAddress, zapPort, apiKey);

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    public static String createAuthContext(String baseUrl, String contextName) throws ClientApiException {
        String resolvedContextName = contextName;
        if (resolvedContextName == null || resolvedContextName.isBlank()) {
            resolvedContextName = "auth-ctx-" + sanitizeContextName(baseUrl);
        }

        String host = URI.create(baseUrl).getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Unable to determine host from APP_BASE_URL: " + baseUrl);
        }

        String includeRegex = "^https?://" + java.util.regex.Pattern.quote(host) + "(:\\\\d+)?(/.*)?$";

        try {
            clientApi.context.removeContext(resolvedContextName);
        } catch (Exception ignored) {
        }

        clientApi.context.newContext(resolvedContextName);
        includeUrlsInContext(resolvedContextName, includeRegex);
        clientApi.context.setContextInScope(resolvedContextName, "true");
        return resolvedContextName;
    }

    public static void includeUrlsInContext(String contextName, String includeRegex) throws ClientApiException {
        clientApi.context.includeInContext(contextName, includeRegex);
    }

    public static void excludeUrlsFromContext(String contextName, String excludeRegex) throws ClientApiException {
        clientApi.context.excludeFromContext(contextName, excludeRegex);
    }

    public static void setFormBasedAuthentication(
            String contextName,
            String loginUrl,
            String loginRequestData,
            String loggedInIndicatorRegex,
            String loggedOutIndicatorRegex
    ) throws ClientApiException {
        int contextId = getContextId(contextName);

        String methodConfig = "loginUrl=" + encode(loginUrl)
                + "&loginRequestData=" + encode(loginRequestData);
        clientApi.authentication.setAuthenticationMethod(String.valueOf(contextId), "formBasedAuthentication", methodConfig);

        if (loggedInIndicatorRegex != null && !loggedInIndicatorRegex.isBlank()) {
            clientApi.authentication.setLoggedInIndicator(String.valueOf(contextId), loggedInIndicatorRegex);
        }

        if (loggedOutIndicatorRegex != null && !loggedOutIndicatorRegex.isBlank()) {
            clientApi.authentication.setLoggedOutIndicator(String.valueOf(contextId), loggedOutIndicatorRegex);
        }
    }

    public static String createAndEnableUser(
            String contextName,
            String username,
            String credentialConfigParams,
            boolean forceUserMode
    ) throws ClientApiException {
        int contextId = getContextId(contextName);

        ApiResponse response = clientApi.users.newUser(String.valueOf(contextId), username);
        String userId = ((ApiResponseElement) response).getValue();

        clientApi.users.setAuthenticationCredentials(String.valueOf(contextId), userId, credentialConfigParams);
        clientApi.users.setUserEnabled(String.valueOf(contextId), userId, "true");

        if (forceUserMode) {
            clientApi.forcedUser.setForcedUser(String.valueOf(contextId), userId);
            clientApi.forcedUser.setForcedUserModeEnabled(true);
        }

        return userId;
    }

    public static void performTraditionalSpiderAsUser(
            String siteToTest,
            String contextName,
            String userId
    ) throws ClientApiException {
        String contextId = String.valueOf(getContextId(contextName));
        String maxChildren = null;
        String recurse = "true";
        String subtreeOnly = "true";

        ApiResponse response = clientApi.spider.scanAsUser(contextId, userId, siteToTest, maxChildren, recurse, subtreeOnly);
        String spiderScanId = ((ApiResponseElement) response).getValue();
        waitTillSpiderIsCompleted(spiderScanId);
    }

    public static void performAjaxSpiderAsUser(
            String siteToTest,
            String contextName,
            String username
    ) throws ClientApiException {
        String subtreeOnly = "true";

        clientApi.ajaxSpider.setOptionBrowserId("htmlunit");
        clientApi.ajaxSpider.scanAsUser(contextName, username, siteToTest, subtreeOnly);
        waitTillAjaxSpiderIsCompleted();
    }

    public static void runActiveScanAsUserWithPolicy(
            String siteToTest,
            String contextName,
            String userId,
            String policyName
    ) throws ClientApiException {
        String contextId = String.valueOf(getContextId(contextName));
        String recurse = "true";
        String method = null;
        String postData = null;

        ApiResponse response = clientApi.ascan.scanAsUser(siteToTest, contextId, userId, recurse, policyName, method, postData);
        String scanId = ((ApiResponseElement) response).getValue();
        waitTillActiveScanIsCompleted(scanId);
    }

    public static void loginWithSelenium(
            WebDriver driver,
            String loginPageUrl,
            String username,
            String password,
            String usernameCss,
            String passwordCss,
            String submitCss,
            String postLoginWaitCss
    ) {
        driver.get(loginPageUrl);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        WebElement usernameInput = findFirstVisibleByCss(wait, usernameCss, "#email", "input[name='email']", "input[name='username']");
        WebElement passwordInput = findFirstVisibleByCss(wait, passwordCss, "#password", "input[name='password']");
        WebElement submitButton = findFirstClickableByCss(wait, submitCss, "button[type='submit']", "button[id='login']");

        usernameInput.sendKeys(username);
        passwordInput.sendKeys(password);
        submitButton.click();

        if (postLoginWaitCss != null && !postLoginWaitCss.isBlank()) {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(postLoginWaitCss)));
        }
    }

    private static WebElement findFirstVisibleByCss(WebDriverWait wait, String primaryCss, String... fallbackCssList) {
        String[] selectors = buildSelectorList(primaryCss, fallbackCssList);
        for (String selector : selectors) {
            try {
                return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector)));
            } catch (Exception ignored) {
            }
        }
        throw new NoSuchElementException("Unable to find visible element with selectors: " + String.join(", ", selectors));
    }

    private static WebElement findFirstClickableByCss(WebDriverWait wait, String primaryCss, String... fallbackCssList) {
        String[] selectors = buildSelectorList(primaryCss, fallbackCssList);
        for (String selector : selectors) {
            try {
                return wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
            } catch (Exception ignored) {
            }
        }
        throw new NoSuchElementException("Unable to find clickable element with selectors: " + String.join(", ", selectors));
    }

    private static void waitTillSpiderIsCompleted(String spiderScanId) throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.spider.status(spiderScanId)).getValue();

        while (!"100".equals(status)) {
            status = ((ApiResponseElement) clientApi.spider.status(spiderScanId)).getValue();
            System.out.println("Authenticated spider scan is in progress: " + status + "%");
            sleepOneSecond("Interrupted while waiting for authenticated spider completion");
        }

        System.out.println("Authenticated spider scan has completed");
    }

    private static void waitTillAjaxSpiderIsCompleted() throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.ajaxSpider.status()).getValue();

        while (!"stopped".equalsIgnoreCase(status)) {
            status = ((ApiResponseElement) clientApi.ajaxSpider.status()).getValue();
            System.out.println("Authenticated Ajax spider is in progress: " + status);
            sleepOneSecond("Interrupted while waiting for authenticated Ajax spider completion");
        }

        System.out.println("Authenticated Ajax spider has completed");
    }

    private static void waitTillActiveScanIsCompleted(String scanId) throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.ascan.status(scanId)).getValue();

        while (!"100".equals(status)) {
            status = ((ApiResponseElement) clientApi.ascan.status(scanId)).getValue();
            System.out.println("Authenticated policy active scan is in progress: " + status + "%");
            sleepOneSecond("Interrupted while waiting for authenticated active scan completion");
        }

        System.out.println("Authenticated policy active scan has completed");
    }

    private static void sleepOneSecond(String message) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(message, e);
        }
    }

    private static String[] buildSelectorList(String primaryCss, String... fallbackCssList) {
        java.util.List<String> selectors = new java.util.ArrayList<>();
        if (primaryCss != null && !primaryCss.isBlank()) {
            selectors.add(primaryCss);
        }
        for (String fallback : fallbackCssList) {
            if (fallback != null && !fallback.isBlank() && !selectors.contains(fallback)) {
                selectors.add(fallback);
            }
        }
        return selectors.toArray(new String[0]);
    }

    private static int getContextId(String contextName) throws ClientApiException {
        ApiResponse response = clientApi.context.context(contextName);
        ApiResponseSet responseSet = (ApiResponseSet) response;
        return Integer.parseInt(responseSet.getAttribute("id"));
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sanitizeContextName(String siteToTest) {
        return siteToTest
                .replaceAll("https?://", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
