package nl.revolution.watchboard.utils;

import nl.revolution.watchboard.Config;
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
            BufferedImage image = ImageIO.read(screenshot);
            int elementWidth = element.getSize().getWidth();
            int elementHeight = element.getSize().getHeight();

            int subImageWidth = Math.min(elementWidth, image.getWidth() - element.getLocation().getX());
            int subImageHeight = Math.min(elementHeight, image.getHeight() - element.getLocation().getY());

            if (subImageWidth != elementWidth) {
                LOG.warn("Image will be cropped horizontally: expected {} px, but will be {} px", elementWidth, subImageWidth);
            }

            if (subImageHeight != elementHeight) {
                LOG.warn("Image will be cropped vertically: expected {} px, but will be {} px", elementHeight, subImageHeight);
            }

            BufferedImage eleScreenshot = image.getSubimage(
                    element.getLocation().getX(), element.getLocation().getY(),
                    subImageWidth, subImageHeight);
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
        new WebDriverWait(driver, timeoutInSeconds).until(ExpectedConditions.titleContains(expectedTitle));
        if (!driver.getTitle().contains(expectedTitle)) {
            LOG.error("Expected title '{}' is not contained in actual title '{}'.", expectedTitle, driver.getTitle());
        }
    }

    public static int numberOfElements(WebDriver driver, By by) {
        WebDriverUtils.disableTimeouts(driver);
        int size = driver.findElements(by).size();
        WebDriverUtils.enableTimeouts(driver);
        return size;
    }

    public static void fetchDummyPage(WebDriver driver) {
        driver.get("http://localhost:" + Config.getInstance().getInt(Config.HTTP_PORT) + Config.getInstance().getContextRoot());
    }

}
