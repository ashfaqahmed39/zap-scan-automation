package utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.zaproxy.clientapi.core.ApiResponse;
import org.zaproxy.clientapi.core.ApiResponseElement;
import org.zaproxy.clientapi.core.ApiResponseList;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;

import java.util.List;
import java.util.stream.Collectors;

public class ZapPolicyUtil {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static final String zapAddress = getEnv("ZAP_ADDRESS", "127.0.0.1");
    private static final int zapPort = Integer.parseInt(getEnv("ZAP_PORT", "8080"));
    private static final String apiKey = getEnv("ZAP_API_KEY", "");
    private static final ClientApi clientApi = new ClientApi(zapAddress, zapPort, apiKey);

    private static final List<Integer> FAST_RULE_IDS = List.of(
            40012, // Cross Site Scripting (Reflected)
            40018, // SQL Injection
            40003, // CRLF Injection
            90019, // Server Side Code Injection
            90020  // Remote OS Command Injection
    );

    private static String getEnv(String key, String defaultValue) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) return fromOs;

        String fromEnvFile = dotenv.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) return fromEnvFile;

        return defaultValue;
    }

    public static void createPolicyIfMissing(String policyName) throws ClientApiException {
        if (getScanPolicyNames().contains(policyName)) {
            System.out.println("ZAP scan policy already exists: " + policyName);
            return;
        }

        clientApi.ascan.addScanPolicy(policyName, null, null);
        System.out.println("Created ZAP scan policy: " + policyName);
    }

    public static void enableRules(String policyName, List<Integer> ruleIds) throws ClientApiException {
        if (ruleIds == null || ruleIds.isEmpty()) {
            throw new IllegalArgumentException("ruleIds must not be empty");
        }

        String scannerIds = ruleIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        clientApi.ascan.enableScanners(scannerIds, policyName);
        System.out.println("Enabled ZAP active scan rules for " + policyName + ": " + scannerIds);
    }

    public static void setRuleStrengthThreshold(
            String policyName,
            List<Integer> ruleIds,
            String attackStrength,
            String alertThreshold
    ) throws ClientApiException {
        if (ruleIds == null || ruleIds.isEmpty()) {
            throw new IllegalArgumentException("ruleIds must not be empty");
        }

        for (Integer ruleId : ruleIds) {
            String scannerId = String.valueOf(ruleId);
            if (attackStrength != null && !attackStrength.isBlank()) {
                clientApi.ascan.setScannerAttackStrength(scannerId, attackStrength, policyName);
            }
            if (alertThreshold != null && !alertThreshold.isBlank()) {
                clientApi.ascan.setScannerAlertThreshold(scannerId, alertThreshold, policyName);
            }
        }
    }

    public static void runActiveScanWithPolicy(String baseUrl, String policyName) throws ClientApiException {
        String recurse = "true";
        String inScopeOnly = "false";
        String method = null;
        String postData = null;

        ApiResponse response = clientApi.ascan.scan(baseUrl, recurse, inScopeOnly, policyName, method, postData);
        String scanId = ((ApiResponseElement) response).getValue();
        waitTillActiveScanIsCompleted(scanId);
    }

    public static String resolvePolicyName(String suitePolicyName) {
        if (suitePolicyName != null && !suitePolicyName.isBlank() && !suitePolicyName.startsWith("${")) {
            return suitePolicyName;
        }
//ZAP_SCAN_POLICY=policy-fast or policy-full
        //default policy-fast
        String explicitPolicy = getEnv("ZAP_SCAN_POLICY", "");
        if (!explicitPolicy.isBlank()) {
            return explicitPolicy;
        }

        String suite = getEnv("SCAN_SUITE", getEnv("TEST_SUITE", "pr"));
        if ("nightly".equalsIgnoreCase(suite) || "full".equalsIgnoreCase(suite)) {
            return "policy-full";
        }

        return "policy-fast";
    }

    public static void configurePolicyProfile(String policyName) throws ClientApiException {
        createPolicyIfMissing(policyName);

        if ("policy-full".equalsIgnoreCase(policyName)) {
            clientApi.ascan.enableAllScanners(policyName);
            clientApi.ascan.setOptionThreadPerHost(5);
            clientApi.ascan.setOptionMaxRuleDurationInMins(0);
            clientApi.ascan.setOptionMaxScanDurationInMins(0);
            System.out.println("Configured policy-full for nightly active scanning");
            return;
        }

        clientApi.ascan.disableAllScanners(policyName);
        enableRules(policyName, FAST_RULE_IDS);
        setRuleStrengthThreshold(policyName, FAST_RULE_IDS, "LOW", "MEDIUM");
        clientApi.ascan.setOptionThreadPerHost(2);
        clientApi.ascan.setOptionMaxRuleDurationInMins(2);
        clientApi.ascan.setOptionMaxScanDurationInMins(10);
        System.out.println("Configured policy-fast for PR active scanning");
    }

    private static List<String> getScanPolicyNames() throws ClientApiException {
        ApiResponse response = clientApi.ascan.scanPolicyNames();
        return ((ApiResponseList) response).getItems().stream()
                .map(item -> ((ApiResponseElement) item).getValue())
                .collect(Collectors.toList());
    }

    private static void waitTillActiveScanIsCompleted(String scanId) throws ClientApiException {
        String status = ((ApiResponseElement) clientApi.ascan.status(scanId)).getValue();

        while (!"100".equals(status)) {
            status = ((ApiResponseElement) clientApi.ascan.status(scanId)).getValue();
            System.out.println("Policy active scan is in progress: " + status + "%");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for active scan completion", e);
            }
        }

        System.out.println("Policy active scan has completed");
    }
}
