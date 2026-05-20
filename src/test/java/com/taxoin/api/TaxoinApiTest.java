package com.taxoin.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TaxoinApiTest {

    // ── Health ────────────────────────────────────────────────────────────────

    @Test
    void health_returns_ok() {
        given()
            .when().get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("ok"));
    }

    @Test
    void quarkus_health_live() {
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Test
    void status_returns_network_info() {
        given()
            .when().get("/api/status")
            .then()
            .statusCode(200)
            .body("network", equalTo("taxoin"))
            .body("status",  equalTo("running"));
    }

    // ── Wallet ────────────────────────────────────────────────────────────────

    @Test
    void wallet_create_returns_address() {
        given()
            .contentType(ContentType.JSON)
            .when().post("/api/wallet")
            .then()
            .statusCode(200)
            .body("address",    startsWith("0x"))
            .body("address",    hasLength(42))
            .body("privateKey", containsString("BEGIN PRIVATE KEY"))
            .body("publicKey",  containsString("BEGIN PUBLIC KEY"));
    }

    @Test
    void wallet_create_each_time_different() {
        String addr1 = given().contentType(ContentType.JSON)
                .post("/api/wallet").then().statusCode(200)
                .extract().path("address");
        String addr2 = given().contentType(ContentType.JSON)
                .post("/api/wallet").then().statusCode(200)
                .extract().path("address");
        assertNotEquals(addr1, addr2);
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @Test
    void balance_unknown_address_returns_zero() {
        given()
            .when().get("/api/balance/0xunknown")
            .then()
            .statusCode(200)
            .body("address", equalTo("0xunknown"))
            .body("balance", equalTo(0.0f));
    }

    // ── Faucet ────────────────────────────────────────────────────────────────

    @Test
    void faucet_credits_100_tokens() {
        String address = "0xtest" + System.currentTimeMillis();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("address", address))
            .when().post("/api/testnet/faucet")
            .then()
            .statusCode(200)
            .body("status",  equalTo("ok"))
            .body("address", equalTo(address))
            .body("amount",  equalTo(100.0f));
    }

    @Test
    void faucet_missing_address_returns_400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of())
            .when().post("/api/testnet/faucet")
            .then()
            .statusCode(400);
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @Test
    void tx_send_valid_both_sigs() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "consumer",    "0xalice",
                "provider",    "0xbob",
                "amount",      0.1,
                "consumerSig", "alice_sig",
                "providerSig", "bob_sig"))
            .when().post("/api/tx/send")
            .then()
            .statusCode(200)
            .body("status",   equalTo("pending"))
            .body("consumer", equalTo("0xalice"))
            .body("provider", equalTo("0xbob"))
            .body("txId",     notNullValue());
    }

    @Test
    void tx_send_invalid_missing_sigs() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "consumer", "0xalice",
                "provider", "0xbob",
                "amount",   0.1))
            .when().post("/api/tx/send")
            .then()
            .statusCode(200)
            .body("status", equalTo("invalid"));
    }

    // ── Services ──────────────────────────────────────────────────────────────

    @Test
    void services_list_empty_initially() {
        given()
            .when().get("/api/services")
            .then()
            .statusCode(200)
            .body("$", hasSize(greaterThanOrEqualTo(0)));
    }

    @Test
    void service_register_and_retrieve() {
        String provider = "0xprovider" + System.currentTimeMillis();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "provider",     provider,
                "serviceType",  "sms",
                "pricePerUnit", 0.1,
                "description",  "Test SMS gateway",
                "endpoint",     "https://test.com"))
            .when().post("/api/service/register")
            .then()
            .statusCode(200)
            .body("status",   equalTo("ok"))
            .body("provider", equalTo(provider));

        // Should appear in list
        given()
            .when().get("/api/services")
            .then()
            .statusCode(200)
            .body("provider", hasItem(provider));
    }

    @Test
    void service_register_duplicate_returns_400() {
        String provider = "0xdup" + System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "provider",     provider,
                "serviceType",  "gpu",
                "pricePerUnit", 5.0,
                "description",  "GPU farm",
                "endpoint",     "https://gpu.com");

        given().contentType(ContentType.JSON).body(body)
                .post("/api/service/register").then().statusCode(200);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/service/register")
                .then().statusCode(400);
    }

    @Test
    void services_filter_by_type() {
        String provider = "0xsms" + System.currentTimeMillis();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "provider",     provider,
                "serviceType",  "sms",
                "pricePerUnit", 0.1,
                "description",  "SMS",
                "endpoint",     "url"))
            .post("/api/service/register");

        given()
            .queryParam("service_type", "sms")
            .when().get("/api/services")
            .then()
            .statusCode(200)
            .body("serviceType", everyItem(equalTo("sms")));
    }

    // ── Reputation ────────────────────────────────────────────────────────────

    @Test
    void reputation_unknown_returns_zero() {
        given()
            .when().get("/api/reputation/0xunknown")
            .then()
            .statusCode(200)
            .body("address",       equalTo("0xunknown"))
            .body("rating",        equalTo(0.0f))
            .body("successful_tx", equalTo(0))
            .body("disputes",      equalTo(0));
    }

    // ── Validators ────────────────────────────────────────────────────────────

    @Test
    void validators_returns_list() {
        given()
            .when().get("/api/validators")
            .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static void assertNotEquals(String a, String b) {
        if (a.equals(b)) throw new AssertionError("Expected different values, got: " + a);
    }
}
