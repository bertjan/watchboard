package nl.revolution.watchboard.utils;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WebDriverUtils {

    private static final Logger LOG = LoggerFactory.getLogger(WebDriverUtils.class);
    public static final int WEBDRIVER_TIMEOUT_SECONDS = 60;

    public static void disableTimeouts(WebDriver driver) {
        driver.manage().timeouts().pageLoadTimeout(0, TimeUnit.SECONDS);
        driver.manage().timeouts().setScriptTimeout(0, TimeUnit.SECONDS);
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
    }

    public static void enableTimeouts(WebDriver driver) {
        driver.manage().timeouts().pageLoadTimeout(WEBDRIVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        driver.manage().timeouts().setScriptTimeout(WEBDRIVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        driver.manage().timeouts().implicitlyWait(WEBDRIVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static void takeScreenShot(WebDriver driver, WebElement element, String fileName) throws IOException {
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        // Crop the entire page screenshot to get only element screenshot.
        try {
            BufferedImage eleScreenshot = ImageIO.read(screenshot).getSubimage(
                    element.getLocation().getX(), element.getLocation().getY(),
                    element.getSize().getWidth(), element.getSize().getHeight());
            ImageIO.write(eleScreenshot, "png", screenshot);
            FileUtils.copyFile(screenshot, new File(fileName));
            try {
                screenshot.delete();
            } catch (Exception e) {
                LOG.error("Error while deleting screenshot: ", e);
            }
            LOG.info("Updated {}.", fileName);
        } catch (Exception e) {
            LOG.error("Error while taking screenshot:", e);
        }
    }


    public static void doSleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            LOG.error("Yawn... sleep interrupted: ", e);
        }
    }

    public static void verifyTitle(WebDriver driver, String expectedTitle, long timeoutInSeconds) {
        new WebDriverWait(driver, timeoutInSeconds).until(ExpectedConditions.titleIs(expectedTitle));
        if (!expectedTitle.equals(driver.getTitle())) {
            LOG.error("Expected title '{}' does not match actual title '{}'.", expectedTitle, driver.getTitle());
        }
    }

    public static int numberOfElements(WebDriver driver, By by) {
        WebDriverUtils.disableTimeouts(driver);
        int size = driver.findElements(by).size();
        WebDriverUtils.enableTimeouts(driver);
        return size;
    }

}
