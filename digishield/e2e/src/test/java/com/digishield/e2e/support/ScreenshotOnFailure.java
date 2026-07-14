package com.digishield.e2e.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JUnit 5 {@link TestWatcher} that captures a screenshot when an E2E test fails
 * - the primary evidence for diagnosing a headless CI run (blank page? error
 * toast? still on /login?). Images are saved to
 * {@code build/screenshots/<display name>.png}.
 */
public class ScreenshotOnFailure implements TestWatcher {

    @Override
    public void testFailed(ExtensionContext ctx, Throwable cause) {
        WebDriver driver = DriverFactory.current();
        if (driver == null) {
            return;
        }
        try {
            File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dir = Path.of("build", "screenshots");
            Files.createDirectories(dir);
            Files.copy(shot.toPath(), dir.resolve(ctx.getDisplayName() + ".png"));
        } catch (IOException e) {
            System.err.println("No screenshot: " + e.getMessage());
        }
    }
}
