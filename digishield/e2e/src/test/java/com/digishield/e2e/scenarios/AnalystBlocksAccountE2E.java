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
 * <p><b>Reference template.</b> Selectors and flow follow the real frontend, but the
 * scenario still needs to be hardened against the live UI (timing, lazy-loaded routes)
 * before it can be a blocking gate. In automation-ci it runs non-blocking
 * ({@code continue-on-error}) and uploads a screenshot on failure.
 *
 * <p>GUARDED: runs only with {@code -De2e.enabled=true} and a running frontend
 * (:5173) + backend (:8080, dev profile). In unit CI (no app) this class is
 * disabled, so it never fails the pipeline.
 */
@EnabledIfSystemProperty(named = "e2e.enabled", matches = "true")
@ExtendWith(ScreenshotOnFailure.class)
class AnalystBlocksAccountE2E {

    static WebDriver driver;
    static final String VALUE = "0909123456";

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
        // (1) Log in as the Analyst persona (the 600ms wait is encapsulated in LoginPage)
        SocInboxPage inbox = new LoginPage(driver)
                .open()
                .loginAsAnalyst("analyst@coquan.gov.vn", "demo1234");
        assertThat(inbox.filterBy("THREAT").reportCount()).isGreaterThanOrEqualTo(0);

        // (2) Block the account via API (the UI "+ Add manually" button is a stub)
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
