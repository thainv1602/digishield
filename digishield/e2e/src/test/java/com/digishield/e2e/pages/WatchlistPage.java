package com.digishield.e2e.pages;

import com.digishield.e2e.support.DriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object for the Watchlist page {@code /soc/watchlist}.
 */
public class WatchlistPage {

    private static final String URL = DriverFactory.baseUrl() + "/soc/watchlist";

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By quickCheckInput = By.cssSelector("form input[type='text']");
    private final By quickCheckSubmit = By.cssSelector("form button[type='submit']");

    public WatchlistPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public WatchlistPage open() {
        driver.get(URL);
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
