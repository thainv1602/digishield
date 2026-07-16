package com.digishield.e2e.pages;

import com.digishield.e2e.support.DriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object for the DigiShield {@code /login} page. The single place that
 * knows the login locators; tests only call business methods.
 */
public class LoginPage {

    private static final String URL = DriverFactory.baseUrl() + "/login";

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By email = By.id("login-email");
    private final By password = By.id("login-pw");
    private final By submit = By.cssSelector("button[type='submit']");

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public LoginPage open() {
        driver.get(URL);
        wait.until(ExpectedConditions.visibilityOfElementLocated(email));
        return this;
    }

    private void loginAs(String persona, String user, String pw) {
        driver.findElement(By.xpath("//button[normalize-space()='" + persona + "']")).click();
        WebElement e = driver.findElement(email);
        e.clear();
        e.sendKeys(user);
        WebElement p = driver.findElement(password);
        p.clear();
        p.sendKeys(pw);
        wait.until(ExpectedConditions.elementToBeClickable(submit)).click();
    }

    /** Log in as the Analyst persona, then wait for navigation to /soc/inbox (past the 600ms setTimeout). */
    public SocInboxPage loginAsAnalyst(String user, String pw) {
        loginAs("Analyst", user, pw);
        wait.until(ExpectedConditions.urlContains("/soc/inbox"));
        return new SocInboxPage(driver);
    }
}
