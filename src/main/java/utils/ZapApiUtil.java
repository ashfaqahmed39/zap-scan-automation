package utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.zaproxy.clientapi.core.ApiResponse;
import org.zaproxy.clientapi.core.ApiResponseElement;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;

import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZapApiUtil {

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

    public static void importOpenApi(String definitionUrlOrFile) throws ClientApiException {
        validateDefinitionLocation(definitionUrlOrFile);
        boolean localFile = isLocalFile(definitionUrlOrFile);
        ApiResponse response;

        try {
            if (localFile) {
                response = clientApi.openapi.importFile(definitionUrlOrFile, null, null);
            } else {
                response = clientApi.openapi.importUrl(definitionUrlOrFile, null, null);
            }
        } catch (ClientApiException e) {
            throw new ClientApiException(
                    "Failed to import OpenAPI definition from '" + definitionUrlOrFile
                            + "'. Verify it is reachable by ZAP and is valid OpenAPI JSON/YAML.",
                    e
            );
        }

        System.out.println("OpenAPI import response: " + response.toString());
    }

    public static void importGraphQl(String endpointOrSchema) throws ClientApiException {
        validateDefinitionLocation(endpointOrSchema);
        boolean localFile = isLocalFile(endpointOrSchema);
        ApiResponse response;

        if (localFile) {
            response = clientApi.graphql.importFile(endpointOrSchema, null);
        } else {
            response = clientApi.graphql.importUrl(endpointOrSchema, null);
        }

        System.out.println("GraphQL import response: " + response.toString());
    }

    public static void triggerApiSpiderOrScan(String targetBaseUrl) throws ClientApiException {
        triggerApiSpiderOrScan(targetBaseUrl, true, true);
    }

    public static void triggerApiSpiderOrScan(String targetBaseUrl, boolean runSpider, boolean runActiveScan)
            throws ClientApiException {
        if (runSpider) {
            String spiderScanId = ((ApiResponseElement) clientApi.spider
                    .scan(targetBaseUrl, null, "true", null, "false")).getValue();
            waitTillSpiderIsCompleted(spiderScanId);
        }

        if (runActiveScan) {
            String scanId = ((ApiResponseElement) clientApi.ascan
                    .scan(targetBaseUrl, "true", "false", null, null, null)).getValue();
            waitTillActiveScanIsCompleted(scanId);
        }
    }

    private static void waitTillSpiderIsCompleted(String spiderScanId) throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.spider.status(spiderScanId)).getValue();

        while (!"100".equals(status)) {
            status = ((ApiResponseElement) clientApi.spider.status(spiderScanId)).getValue();
            System.out.println("API spider is in progress: " + status + "%");
            sleep(1000);
        }

        System.out.println("API spider has completed");
    }

    private static void waitTillActiveScanIsCompleted(String scanId) throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.ascan.status(scanId)).getValue();

        while (!"100".equals(status)) {
            status = ((ApiResponseElement) clientApi.ascan.status(scanId)).getValue();
            System.out.println("API active scan is in progress: " + status + "%");
            sleep(1000);
        }

        System.out.println("API active scan has completed");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for scan completion", e);
        }
    }

    private static boolean isLocalFile(String location) {
        try {
            return Files.exists(Path.of(location));
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    private static void validateDefinitionLocation(String location) throws ClientApiException {
        if (location == null || location.isBlank()) {
            throw new ClientApiException("API definition location is empty.");
        }

        boolean url = location.startsWith("http://") || location.startsWith("https://");
        if (url) return;

        try {
            if (!Files.exists(Path.of(location))) {
                throw new ClientApiException("API definition file does not exist: " + location);
            }
        } catch (InvalidPathException e) {
            throw new ClientApiException("API definition location is not a valid URL or file path: " + location, e);
        }
    }
}
