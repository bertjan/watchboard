package nl.revolution.watchboard.utils;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class WebDriverUtils {

    private static final Logger LOG = LoggerFactory.getLogger(WebDriverUtils.class);

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

}
