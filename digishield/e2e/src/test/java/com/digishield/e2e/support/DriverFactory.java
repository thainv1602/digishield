package com.digishield.e2e.support;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Creates and holds the WebDriver for a scenario.
 *
 * <p>Headless is on by default ({@code -Dselenium.headless=true}); pass
 * {@code false} to watch the browser while debugging locally. Selenium Manager
 * resolves the matching chromedriver automatically.
 */
public final class DriverFactory {

    private static final ThreadLocal<WebDriver> CURRENT = new ThreadLocal<>();

    private DriverFactory() {
    }

    /** Base URL of the frontend under test (default {@code http://localhost:5173}). */
    public static String baseUrl() {
        return System.getProperty("e2e.baseUrl", "http://localhost:5173");
    }

    /** Create a Chrome driver honouring the {@code selenium.headless} flag. */
    public static WebDriver create() {
        ChromeOptions opts = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("selenium.headless", "true"))) {
            opts.addArguments("--headless=new");
        }
        opts.addArguments(
                "--window-size=1920,1080", // fixed viewport for responsive layout
                "--disable-gpu",
                "--no-sandbox");           // required in CI containers
        WebDriver driver = new ChromeDriver(opts);
        CURRENT.set(driver);
        return driver;
    }

    /** The driver bound to the current thread, or {@code null} (used by the screenshot watcher). */
    public static WebDriver current() {
        return CURRENT.get();
    }

    public static void quit() {
        WebDriver driver = CURRENT.get();
        if (driver != null) {
            driver.quit();
            CURRENT.remove();
        }
    }
}
