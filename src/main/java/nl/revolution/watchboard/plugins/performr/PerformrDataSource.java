package nl.revolution.watchboard.plugins.performr;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

public class PerformrDataSource {

    public static void main(String... args) {
        WebDriver driver = new FirefoxDriver();
        driver.get("https://app.performr.com");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        driver.quit();
    }
}
