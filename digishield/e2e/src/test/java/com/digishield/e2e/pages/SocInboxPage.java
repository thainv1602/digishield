package com.digishield.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object for the SOC Inbox page {@code /soc/inbox}.
 */
public class SocInboxPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By rows = By.cssSelector("div[role='button']");

    public SocInboxPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    private By tab(String label) {
        return By.xpath("//button[@role='tab'][contains(., '" + label + "')]");
    }

    public SocInboxPage filterBy(String label) {
        wait.until(ExpectedConditions.elementToBeClickable(tab(label))).click();
        return this;
    }

    public int reportCount() {
        return driver.findElements(rows).size();
    }

    public WatchlistPage gotoWatchlist() {
        return new WatchlistPage(driver).open();
    }
}
