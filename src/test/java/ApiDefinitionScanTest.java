import io.github.cdimascio.dotenv.Dotenv;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.zaproxy.clientapi.core.ClientApiException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static utils.ZapApiUtil.importOpenApi;
import static utils.ZapApiUtil.triggerApiSpiderOrScan;
import static utils.ZapUtil.*;

public class ApiDefinitionScanTest {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private String baseUrl;

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    @Test
    public void importOpenApiAndScanImportedEndpoints() throws ClientApiException {
        baseUrl = getEnv("APP_BASE_URL", "http://127.0.0.1:8080");
        String openApiDefinition = getEnv("OPENAPI_DEFINITION", baseUrl + "/v3/api-docs");
        String expectedImportedEndpointFragment = getEnv("EXPECTED_IMPORTED_ENDPOINT_FRAGMENT", "/api/");

        String contextName = createScopeContextForTarget(baseUrl);
        performTraditionalSpider(baseUrl, contextName);
        Set<String> spiderDiscoveredUrls = getUrlsFromScanTree().stream().collect(Collectors.toSet());

        importOpenApi(openApiDefinition);

        List<String> urlsAfterImport = getUrlsFromScanTree();
        List<String> importedOnlyUrls = urlsAfterImport.stream()
                .filter(url -> !spiderDiscoveredUrls.contains(url))
                .collect(Collectors.toList());

        Assert.assertFalse(importedOnlyUrls.isEmpty(),
                "Expected at least one API endpoint imported from definition that spider did not discover.");

        boolean hasExpectedEndpoint = importedOnlyUrls.stream()
                .anyMatch(url -> url.contains(expectedImportedEndpointFragment));
        Assert.assertTrue(hasExpectedEndpoint,
                "Expected imported-only endpoints to contain fragment: " + expectedImportedEndpointFragment);

        triggerApiSpiderOrScan(baseUrl, false, true);
        waitTillPassiveScanCompleted(2000, 10, 120);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            generateZapReport(baseUrl);
        }
    }
}
