package com.digishield.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object for the Watchlist page {@code /soc/watchlist}.
 *
 * <p>Reached via SPA navigation (sidebar link) — never {@code driver.get()},
 * because a full page load drops the in-memory dev session and bounces back
 * to /login.
 */
public class WatchlistPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By quickCheckInput = By.cssSelector("form input[type='text']");
    private final By quickCheckSubmit = By.cssSelector("form button[type='submit']");

    public WatchlistPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    /** Wait until the page (its Quick Check form) is rendered. */
    public WatchlistPage awaitLoaded() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(quickCheckInput));
        return this;
    }

    public WatchlistPage quickCheck(String value) {
        WebElement in = wait.until(ExpectedConditions.visibilityOfElementLocated(quickCheckInput));
        in.clear();
        in.sendKeys(value);
        wait.until(ExpectedConditions.elementToBeClickable(quickCheckSubmit)).click();
        return this;
    }

    /** Wait for the async-loaded table to show a row containing the value. */
    public boolean hasEntry(String value) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//div[normalize-space()='" + value + "']"))).isDisplayed();
    }
}
