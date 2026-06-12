import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.zaproxy.clientapi.core.ClientApiException;
import utils.ZapAuthUtil;

import java.util.List;

import static utils.ZapUtil.*;

public class AuthSpiderActiveScanTest {
    private WebDriver driver;

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private final String baseUrl = getEnv("APP_BASE_URL", "http://127.0.0.1:8080");
    private final String loginUrl = getEnv("AUTH_LOGIN_URL", baseUrl + "/login");
    private final String username = getEnv("AUTH_USERNAME", "");
    private final String password = getEnv("AUTH_PASSWORD", "");
    private final String usernameCss = getEnv("AUTH_USERNAME_CSS", "#email");
    private final String passwordCss = getEnv("AUTH_PASSWORD_CSS", "input[name='password']");
    private final String submitCss = getEnv("AUTH_SUBMIT_CSS", "button[type='submit']");
    private final String postLoginCss = getEnv("AUTH_POST_LOGIN_CSS", "");
    private final String authOnlyUrlContains = getEnv("AUTH_ONLY_URL_CONTAINS", "/admin");

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    @BeforeMethod
    public void setUp() {
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
    public void testAuthenticatedSpiderAjaxAndActiveScan() throws ClientApiException {
        if (username.isBlank() || password.isBlank()) {
            throw new SkipException("AUTH_USERNAME and AUTH_PASSWORD are required for authenticated scan test");
        }

        String contextName = ZapAuthUtil.createAuthContext(baseUrl, "auth-scan-context");
        ZapAuthUtil.excludeUrlsFromContext(contextName, ".*(logout|signout).*?");

        String loginRequestData = "username={%username%}&password={%password%}";
        ZapAuthUtil.setFormBasedAuthentication(
                contextName,
                loginUrl,
                loginRequestData,
                "(?i)(logout|sign out|my account)",
                "(?i)(login|sign in)"
        );

        String credentials = "username=" + username + "&password=" + password;
        ZapAuthUtil.createAndEnableUser(contextName, username, credentials, true);

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

        performTraditionalSpider(baseUrl, contextName);
        performAjaxSpider(baseUrl, contextName);
        waitTillPassiveScanCompleted(2000, 10, 180);
        performActiveScanInScope(baseUrl);

        List<String> urls = getUrlsFromScanTree();
        boolean hasAuthenticatedOnlyUrl = urls.stream().anyMatch(u -> u.contains(authOnlyUrlContains));
        Assert.assertTrue(
                hasAuthenticatedOnlyUrl,
                "Expected authenticated URL containing '" + authOnlyUrlContains + "' in ZAP site tree"
        );

        int totalAlerts = getAlertCountForBaseUrl(baseUrl);
        Assert.assertTrue(totalAlerts > 0, "Expected at least one alert for authenticated target");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        generateZapReport(baseUrl);
        if (driver != null) {
            driver.quit();
        }
    }
}
