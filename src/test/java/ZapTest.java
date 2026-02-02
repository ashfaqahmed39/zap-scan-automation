import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static utils.ZapUtil.*;

public class ZapTest {

    private WebDriver driver;

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

        // Use ZAP proxy from ZapUtil
        chromeOptions.setProxy(proxy);

        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver(chromeOptions);
    }

    @Test
    public void demoTest() {
        driver.get(urlToTest);
        // Wait until passive scan completed
        waitTillPassiveScanCompleted(2000, 10, 120);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        // Generate report after test
        generateZapReport(urlToTest);

        if (driver != null) {
            driver.quit();
        }
    }
}
