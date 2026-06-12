import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.zaproxy.clientapi.core.ClientApiException;
import utils.ZapAuthUtil;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static utils.ZapPolicyUtil.configurePolicyProfile;
import static utils.ZapPolicyUtil.resolvePolicyName;
import static utils.ZapPolicyUtil.runActiveScanWithPolicy;
import static utils.ZapUtil.createScopeContextForTarget;
import static utils.ZapUtil.generateZapReport;
import static utils.ZapUtil.performAjaxSpider;
import static utils.ZapUtil.performForcedBrowse;
import static utils.ZapUtil.performForcedBrowseWithSelenium;
import static utils.ZapUtil.performTraditionalSpider;
import static utils.ZapUtil.proxy;
import static utils.ZapUtil.waitTillPassiveScanCompleted;

public class CombinedSecurityScanTest {
    private WebDriver driver;
    private String policyName;

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private final String urlToTest = getEnv("APP_BASE_URL", "http://127.0.0.1:8080");
    private final String loginUrl = getEnv("AUTH_LOGIN_URL", urlToTest + "/login");
    private final String username = getEnv("AUTH_USERNAME", "");
    private final String password = getEnv("AUTH_PASSWORD", "");
    private final String usernameCss = getEnv("AUTH_USERNAME_CSS", "#email");
    private final String passwordCss = getEnv("AUTH_PASSWORD_CSS", "input[name='password']");
    private final String submitCss = getEnv("AUTH_SUBMIT_CSS", "button[type='submit']");
    private final String postLoginCss = getEnv("AUTH_POST_LOGIN_CSS", "");
    private final String loggedInRegex = getEnv("AUTH_LOGGED_IN_REGEX", "(?i)(logout|sign out|my account)");
    private final String loggedOutRegex = getEnv("AUTH_LOGGED_OUT_REGEX", "(?i)(login|sign in)");
    private final String workflowUrls = getEnv("AUTH_WORKFLOW_URLS", "");
    private final String loginRequestData = getEnv(
            "AUTH_LOGIN_REQUEST_DATA",
            "username={%username%}&password={%password%}"
    );
    private final String credentials = getEnv(
            "AUTH_CREDENTIALS",
            "username=" + username + "&password=" + password
    );

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    @BeforeMethod
    @Parameters({"scanPolicyName"})
    public void setUp(@Optional("") String scanPolicyName) throws ClientApiException {
        policyName = resolvePolicyName(scanPolicyName);
        configurePolicyProfile(policyName);

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--ignore-certificate-errors");
        chromeOptions.addArguments("--allow-insecure-localhost");
        chromeOptions.addArguments("--disable-background-networking");
        chromeOptions.addArguments("--disable-sync");
        chromeOptions.addArguments("--disable-extensions");
        chromeOptions.addArguments("--disable-default-apps");
        chromeOptions.addArguments("--disable-popup-blocking");
        chromeOptions.addArguments("--disable-notifications");
        chromeOptions.addArguments("--incognito");
        chromeOptions.addArguments("--no-first-run");
        chromeOptions.addArguments("--no-default-browser-check");
        chromeOptions.setProxy(proxy);

        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver(chromeOptions);
    }

    @Test
    public void testCombinedUnauthenticatedSecurityScan() throws ClientApiException {
        System.out.println("Running combined unauthenticated scan with policy: " + policyName);

        driver.get(urlToTest);
        String contextName = createScopeContextForTarget(urlToTest);

        performTraditionalSpider(urlToTest, contextName);
        performAjaxSpider(urlToTest, contextName);
        performForcedBrowse(urlToTest);
        waitTillPassiveScanCompleted(2000, 10, 180);
        runActiveScanWithPolicy(urlToTest, policyName);
        waitTillPassiveScanCompleted(2000, 10, 120);
    }

    @Test
    public void testCombinedAuthenticatedSecurityScan() throws ClientApiException {
        if (username.isBlank() || password.isBlank()) {
            throw new SkipException("AUTH_USERNAME and AUTH_PASSWORD are required for authenticated scan test");
        }

        System.out.println("Running combined authenticated scan with policy: " + policyName);

        String contextName = ZapAuthUtil.createAuthContext(urlToTest, "combined-auth-scan-context");
        ZapAuthUtil.excludeUrlsFromContext(contextName, ".*(logout|signout).*?");
        ZapAuthUtil.setFormBasedAuthentication(
                contextName,
                loginUrl,
                loginRequestData,
                loggedInRegex,
                loggedOutRegex
        );
        String userId = ZapAuthUtil.createAndEnableUser(contextName, username, credentials, true);

        ZapAuthUtil.loginWithSelenium(
                driver,
                loginUrl,
                username,
                password,
                usernameCss,
                passwordCss,
                submitCss,
                postLoginCss
        );

        visitAuthenticatedWorkflowUrls();

        ZapAuthUtil.performTraditionalSpiderAsUser(urlToTest, contextName, userId);
        ZapAuthUtil.performAjaxSpiderAsUser(urlToTest, contextName, username);
        performForcedBrowseWithSelenium(driver, urlToTest);
        waitTillPassiveScanCompleted(2000, 10, 180);
        ZapAuthUtil.runActiveScanAsUserWithPolicy(urlToTest, contextName, userId, policyName);
        waitTillPassiveScanCompleted(2000, 10, 120);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        generateZapReport(urlToTest);
        if (driver != null) {
            driver.quit();
        }
    }

    private void visitAuthenticatedWorkflowUrls() {
        List<String> urls = Arrays.stream(workflowUrls.split(","))
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .collect(Collectors.toList());

        if (urls.isEmpty()) {
            System.out.println("No AUTH_WORKFLOW_URLS configured");
            return;
        }

        System.out.println("Visiting " + urls.size() + " authenticated workflow URLs");
        for (String url : urls) {
            String resolvedUrl = resolveWorkflowUrl(url);
            driver.get(resolvedUrl);
            System.out.println("Visited authenticated workflow URL: " + resolvedUrl);
        }
    }

    private String resolveWorkflowUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        URI baseUri = URI.create(urlToTest.endsWith("/") ? urlToTest : urlToTest + "/");
        String normalizedUrl = url.startsWith("/") ? url.substring(1) : url;
        return baseUri.resolve(normalizedUrl).toString();
    }
}
