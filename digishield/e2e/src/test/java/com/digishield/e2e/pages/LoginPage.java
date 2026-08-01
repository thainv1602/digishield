package com.digishield.e2e.pages;

import com.digishield.e2e.support.DriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object for the DigiShield {@code /login} page. The single place that
 * knows the login locators; tests only call business methods.
 *
 * <p>Targets the <b>dev sign-in form</b> the frontend renders when Cognito is
 * not configured (no {@code VITE_COGNITO_*} env — the local/CI setup). The
 * form exposes the stable ids {@code login-role}, {@code login-email} and
 * {@code login-submit}; role values match the backend RBAC roles
 * ({@code analyst}, {@code learner}, ...).
 */
public class LoginPage {

    private static final String URL = DriverFactory.baseUrl() + "/login";

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By role = By.id("login-role");
    private final By email = By.id("login-email");
    private final By submit = By.id("login-submit");

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public LoginPage open() {
        driver.get(URL);
        wait.until(ExpectedConditions.visibilityOfElementLocated(role));
        return this;
    }

    private void loginAs(String roleValue, String userEmail) {
        new Select(driver.findElement(role)).selectByValue(roleValue);
        WebElement e = driver.findElement(email);
        e.clear();
        e.sendKeys(userEmail);
        wait.until(ExpectedConditions.elementToBeClickable(submit)).click();
    }

    /** Sign in with the analyst role, then wait for navigation to /soc/inbox. */
    public SocInboxPage loginAsAnalyst(String userEmail) {
        loginAs("analyst", userEmail);
        wait.until(ExpectedConditions.urlContains("/soc/inbox"));
        return new SocInboxPage(driver);
    }
}
