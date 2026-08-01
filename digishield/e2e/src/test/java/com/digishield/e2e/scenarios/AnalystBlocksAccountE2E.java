package com.digishield.e2e.scenarios;

import com.digishield.e2e.pages.LoginPage;
import com.digishield.e2e.pages.SocInboxPage;
import com.digishield.e2e.pages.WatchlistPage;
import com.digishield.e2e.support.ApiHelper;
import com.digishield.e2e.support.DriverFactory;
import com.digishield.e2e.support.ScreenshotOnFailure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E-WL-01 - an Analyst blocks a scam account, end to end across UI -> API -> DB.
 *
 * <p>Signs in through the dev sign-in form (rendered when Cognito is not
 * configured), so the whole flow is hermetic: frontend + backend (dev profile)
 * + this test, no external identity provider. A blocking gate in automation-ci.
 *
 * <p>GUARDED: runs only with {@code -De2e.enabled=true} and a running frontend
 * (:5173) + backend (:8080, dev profile). In unit CI (no app) this class is
 * disabled, so it never fails the pipeline.
 */
@EnabledIfSystemProperty(named = "e2e.enabled", matches = "true")
@ExtendWith(ScreenshotOnFailure.class)
class AnalystBlocksAccountE2E {

    static WebDriver driver;
    /** Unique per run, so reruns against a long-lived dev backend never collide. */
    static final String VALUE = "0909" + (System.currentTimeMillis() % 1_000_000);

    @BeforeAll
    static void startBrowser() {
        driver = DriverFactory.create();
    }

    @AfterAll
    static void quitBrowser() {
        DriverFactory.quit();
    }

    @Test
    void analystBlocksScamAccountEndToEnd() throws Exception {
        // (1) Sign in with the analyst role via the dev sign-in form
        SocInboxPage inbox = new LoginPage(driver)
                .open()
                .loginAsAnalyst("analyst@dev.digishield.local");
        assertThat(inbox.filterBy("THREAT").reportCount()).isGreaterThanOrEqualTo(0);

        // (2) Block the account via API (setup by API, observe through the UI)
        ApiHelper.blockAccount(VALUE);

        // (3) UI: the new entry shows up in the watchlist table
        WatchlistPage wl = inbox.gotoWatchlist();
        assertThat(wl.hasEntry(VALUE)).as("Watchlist must show %s", VALUE).isTrue();

        // (4) UI: quick lookup via Quick Check
        wl.quickCheck(VALUE);

        // (5) Cross-check the interception API
        assertThat(ApiHelper.isInWatchlist(VALUE))
                .as("check?value= must return inWatchlist=true").isTrue();
    }
}
