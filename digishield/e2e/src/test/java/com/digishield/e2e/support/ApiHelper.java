package com.digishield.e2e.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * API cross-check via the JDK HttpClient: fast data setup plus backend-side
 * state assertions, in parallel with observing the UI through Selenium.
 *
 * <p>Uses the dev headers {@code X-Demo-Role} + {@code X-Tenant-Id} like the rest
 * of DigiShield under the dev profile.
 */
public final class ApiHelper {

    private static final String API = System.getProperty("e2e.apiUrl", "http://localhost:8080/api/v1");
    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private ApiHelper() {
    }

    private static HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + path))
                .header("Content-Type", "application/json")
                .header("X-Demo-Role", "ANALYST")
                .header("X-Tenant-Id", TENANT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** One business action, "block": write to both stores (blacklist + watchlist). */
    public static void blockAccount(String value) throws Exception {
        post("/blacklist", "{\"type\":\"phone\",\"value\":\"" + value
                + "\",\"source\":\"E2E-BTL\"}");
        post("/account-watchlist", "{\"type\":\"phone\",\"value\":\"" + value
                + "\",\"risk_level\":\"high\",\"source\":\"E2E-BTL\"}");
    }

    /** GET /account-watchlist/check?value=... -> true when inWatchlist. */
    public static boolean isInWatchlist(String value) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + "/account-watchlist/check?value=" + value))
                .header("X-Demo-Role", "ANALYST")
                .header("X-Tenant-Id", TENANT)
                .GET().build();
        String body = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return body.contains("\"inWatchlist\":true");
    }
}
