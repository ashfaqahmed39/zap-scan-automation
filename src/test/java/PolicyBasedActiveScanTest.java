import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.zaproxy.clientapi.core.ClientApiException;

import static utils.ZapPolicyUtil.configurePolicyProfile;
import static utils.ZapPolicyUtil.resolvePolicyName;
import static utils.ZapPolicyUtil.runActiveScanWithPolicy;
import static utils.ZapUtil.generateZapReport;
import static utils.ZapUtil.proxy;
import static utils.ZapUtil.waitTillPassiveScanCompleted;

public class PolicyBasedActiveScanTest {
    private WebDriver driver;
    private String policyName;

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private final String urlToTest = getEnv("APP_BASE_URL", "http://127.0.0.1:8080");

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
    public void testPolicyBasedActiveScan() throws ClientApiException {
        System.out.println("Running policy-based active scan with policy: " + policyName);
        driver.get(urlToTest);
        waitTillPassiveScanCompleted(2000, 10, 120);
        runActiveScanWithPolicy(urlToTest, policyName);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        generateZapReport(urlToTest);
        if (driver != null) {
            driver.quit();
        }
    }
}
