package nl.revolution.watchboard;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class CloudWatchDataSource {

    private Thread worker;
    private boolean stop;

    public void start() {
        worker = new CloudWatchDataWorker();
        worker.start();
    }

    public void stop() {
        if (worker != null) {
            if (worker.isAlive()) {
                stop = true;
                try {
                    worker.join();
                } catch (InterruptedException ex) {
                    System.out.println("Error stopping CloudWatchDataWorker: " + ex);
                }
            }
            worker = null;
        }
    }

    private class CloudWatchDataWorker extends Thread {

        // TODO:
        // - proper logging

        private WebDriver driver;
        private static final String MAXIMIZE_IMAGE_CONTENT = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAOCAYAAADwikbvAAAAHElEQVR42mNwcXH5Ty5mGHjNpIBRzSNUM92TJwDOA1GY0jTyxAAAAABJRU5ErkJggg==";

        public void run() {
            System.out.println("Starting CloudWatch data worker");
            Config config = Config.getInstance();
            int backendUpdateIntervalSeconds = config.getInt(Config.BACKEND_UPDATE_INTERVAL_SECONDS);

            initWebDriver();
            loginToAwsConsole(config.getString(Config.AWS_USERNAME), config.getString(Config.AWS_PASSWORD));

            System.out.println("Starting main update loop.");
            long currentSessionStart = System.currentTimeMillis();

            while (!stop) {
                System.out.println("Updating data from AWS.");

                // Generate reports for all graphs for all dashboards.
                Config.getInstance().getDashboards().stream().forEach(
                        dashboard -> dashboard.getGraphs().stream().forEach(graph -> {
                                    if (!stop) getReportScreenshot(graph.getUrl(),
                                            graph.getBrowserWidth(),
                                            graph.getBrowserHeight(),
                                            graph.getImagePath());

                                }
                        ));
                if (stop) break;

                // Wait before fetching next update.
                doSleep(1000 * backendUpdateIntervalSeconds);

                // Re-start webdriver and re-login to AWS console every now and than to prevent session max duration issues.
                long currentSessionTimeInMinutes = ((System.currentTimeMillis() - currentSessionStart) / 1000 / 60);
                System.out.println("currentSessionTimeInMinutes: " + currentSessionTimeInMinutes);
                if (currentSessionTimeInMinutes > Config.getInstance().getInt(Config.MAX_SESSION_DURATION_MINUTES)) {
                    System.out.println("Max session duration exceeded, restarting browser.");
                    shutdownWebDriver();
                    initWebDriver();
                    loginToAwsConsole(config.getString(Config.AWS_USERNAME), config.getString(Config.AWS_PASSWORD));
                    currentSessionStart = System.currentTimeMillis();
                }
            }

            // Stop webdriver.
            shutdownWebDriver();
        }

        private void initWebDriver() {
            System.out.println("Initializing PhantomJS webDriver");
            // driver = new FirefoxDriver();
            driver = new PhantomJSDriver();
            driver.manage().timeouts().pageLoadTimeout(20, TimeUnit.SECONDS);
            driver.manage().timeouts().setScriptTimeout(20, TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
            doSleep(500);
        }

        private void shutdownWebDriver() {
            if (driver != null) {
                driver.quit();
                driver = null;
            }
            doSleep(500);
        }

        public void verifyTitle(String expectedTitle) {
            long timeout = 3;
            new WebDriverWait(driver, timeout).until(ExpectedConditions.titleIs(expectedTitle));
            assertThat(driver.getTitle(), is(equalTo(expectedTitle)));
        }

        private void getReportScreenshot(String reportUrl, int width, int height, String filename) {
            driver.manage().window().setSize(new Dimension(width, height));
            driver.get(reportUrl);
            doSleep(500);

            // Select bottom option in timezone select (local time).
            Select timezoneSelect = new Select(driver.findElement(By.id("gwt-debug-timezoneList")));
            timezoneSelect.selectByIndex(timezoneSelect.getOptions().size() - 1);

            // Wait until loading is finished.
            boolean loading = true;
            while (loading) {
                if (!driver.findElement(By.id("gwt-debug-graphLoadingIndicator")).isDisplayed()) {
                    loading = false;
                }
                doSleep(500);
            }

            List<WebElement> images = driver.findElement(By.id("gwt-debug-detailPanel")).findElements(By.className("gwt-Image"));

            for (WebElement image : images) {
                String imageSrc = image.getAttribute("src");
                if (MAXIMIZE_IMAGE_CONTENT.equals(imageSrc)) {
                    image.click();
                    doSleep(100);
                }
            }

            WebElement graph = driver.findElement(By.id("gwt-debug-graphContainer"));
            try {
                takeShot(graph, filename);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void takeShot(WebElement element, String fileName) throws IOException {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            // Crop the entire page screenshot to get only element screenshot.
            BufferedImage eleScreenshot = ImageIO.read(screenshot).getSubimage(
                    element.getLocation().getX(), element.getLocation().getY(),
                    element.getSize().getWidth(), element.getSize().getHeight());
            ImageIO.write(eleScreenshot, "png", screenshot);
            FileUtils.copyFile(screenshot, new File(fileName));
            System.out.println("Updated " + fileName + ".");
        }

        private void loginToAwsConsole(String username, String password) {
            System.out.println("Logging in to AWS console.");
            driver.get(Config.getInstance().getString(Config.AWS_SIGNIN_URL));
            doSleep(500);
            driver.get("https://console.aws.amazon.com/console/home");
            verifyTitle("Amazon Web Services Sign-In");
            driver.findElement(By.id("username")).sendKeys(username);
            driver.findElement(By.id("password")).sendKeys(password);
            driver.findElement(By.id("signin_button")).click();
        }

        private void doSleep(long duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
