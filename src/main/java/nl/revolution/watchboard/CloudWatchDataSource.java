package nl.revolution.watchboard;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CloudWatchDataSource {

    // TODO
    // - rearrange packages
    // - add tests

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchDataSource.class);
    private static final int SOCKET_TIMEOUT_MS = 60 * 1000;

    private long currentSessionStartTimestamp;

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
                } catch (InterruptedException e) {
                    LOG.error("Error stopping CloudWatchDataWorker: ", e);
                }
            }
            worker = null;
        }
    }

    private class CloudWatchDataWorker extends Thread {

        private WebDriver driver;
        private static final String MAXIMIZE_IMAGE_CONTENT = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAOCAYAAADwikbvAAAAHElEQVR42mNwcXH5Ty5mGHjNpIBRzSNUM92TJwDOA1GY0jTyxAAAAABJRU5ErkJggg==";
        private static final int MAX_GRAPH_LOADING_TIME_IN_SECONDS = 30;

        public void run() {
            LOG.info("Starting CloudWatch data worker");

            initWebDriver();
            loginToAwsConsole(Config.getInstance().getString(Config.AWS_USERNAME), Config.getInstance().getString(Config.AWS_PASSWORD));

            LOG.info("Starting main update loop.");
            currentSessionStartTimestamp = System.currentTimeMillis();

            while (!stop) {
                // Check for config file update.
                Config.getInstance().checkForConfigUpdate();

                // Generate reports for all graphs for all dashboards.
                LOG.info("Updating data from AWS.");
                Config.getInstance().getDashboards().stream().forEach(
                        dashboard -> dashboard.getGraphs().stream().forEach(graph -> {
                                    if (!stop) {
                                        boolean executedSuccessfully = getReportScreenshot(graph.getUrl(),
                                                graph.getBrowserWidth(),
                                                graph.getBrowserHeight(),
                                                graph.getImagePath());
                                        if (!executedSuccessfully) {
                                            // Something went wrong; start over.
                                            restartWebDriverAndLoginToAWSConsole();
                                        }
                                    }
                                }
                        ));
                if (stop) break;

                // Wait before fetching next update.
                int backendUpdateIntervalSeconds = Config.getInstance().getInt(Config.BACKEND_UPDATE_INTERVAL_SECONDS);
                LOG.debug("Sleeping {} seconds until next update.", backendUpdateIntervalSeconds);
                doSleep(1000 * backendUpdateIntervalSeconds);

                // Re-start webdriver and re-login to AWS console every now and than to prevent session max duration issues.
                long currentSessionTimeInMinutes = ((System.currentTimeMillis() - currentSessionStartTimestamp) / 1000 / 60);
                LOG.info("currentSessionTimeInMinutes: " + currentSessionTimeInMinutes);
                if (currentSessionTimeInMinutes > Config.getInstance().getInt(Config.MAX_SESSION_DURATION_MINUTES)) {
                    LOG.info("Max session duration exceeded, restarting browser.");

                    // Restart; this also resets the session duration timer.
                    restartWebDriverAndLoginToAWSConsole();
                }
            }

            // Stop webdriver.
            shutdownWebDriver();
        }

        private void initWebDriver() {
            LOG.info("Initializing PhantomJS webDriver.");
            try {
                // driver = new FirefoxDriver();
                WebDriverHttpParamsSetter.setSoTimeout(SOCKET_TIMEOUT_MS);
                driver = new PhantomJSDriver();
                driver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
                driver.manage().timeouts().setScriptTimeout(30, TimeUnit.SECONDS);
                driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.error("Error (re)initializing webDriver: ", e);
                LOG.info("Sleeping 10 seconds and trying again.");
                doSleep(10000);
                initWebDriver();
                return;
            }
            doSleep(500);
        }

        private void shutdownWebDriver() {
            if (driver != null) {
                driver.quit();
                driver = null;
            }
            doSleep(500);
        }

        private void restartWebDriverAndLoginToAWSConsole() {
            shutdownWebDriver();
            initWebDriver();
            loginToAwsConsole(Config.getInstance().getString(Config.AWS_USERNAME), Config.getInstance().getString(Config.AWS_PASSWORD));
            currentSessionStartTimestamp = System.currentTimeMillis();
        }

        public void verifyTitle(String expectedTitle) {
            long timeout = 3;
            new WebDriverWait(driver, timeout).until(ExpectedConditions.titleIs(expectedTitle));
            if (!expectedTitle.equals(driver.getTitle())) {
                LOG.error("Expected title '{}' does not match actual title '{}'.", expectedTitle, driver.getTitle());
            }
        }

        private boolean getReportScreenshot(String reportUrl, int width, int height, String filename) {
            try {
                LOG.debug("Starting update of {}", filename);

                driver.manage().window().setSize(new Dimension(width, height));
                driver.get(reportUrl);
                doSleep(500);

                // Select bottom option in timezone select (local time).
                Select timezoneSelect = new Select(driver.findElement(By.id("gwt-debug-timezoneList")));
                timezoneSelect.selectByIndex(timezoneSelect.getOptions().size() - 1);

                // Wait until loading is finished.
                long loadingStart = System.currentTimeMillis();
                while (true) {
                    if (!driver.findElement(By.id("gwt-debug-graphLoadingIndicator")).isDisplayed()) {
                        long loadTimeMS = System.currentTimeMillis() - loadingStart;
                        LOG.debug("Graph {} loaded in {} ms.", filename, loadTimeMS);
                        break;
                    }

                    long waitingForMS = System.currentTimeMillis() - loadingStart;
                    LOG.debug("Waiting until {} is loaded (waited for {} ms).", filename, waitingForMS);
                    if ((waitingForMS / 1000) > MAX_GRAPH_LOADING_TIME_IN_SECONDS) {
                        LOG.error("Max waiting time of {} seconds for loading graph expired, giving up.", MAX_GRAPH_LOADING_TIME_IN_SECONDS);
                        return false;
                    }
                    doSleep(250);
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
                    LOG.error("Error while taking screenshot:", e);
                    return false;
                }
            } catch (WebDriverException e) {
                LOG.error("Caught WebDriverException: ", e);
                LOG.error("Error occurred while fetching report for {} ", filename);
                return false;
            }
            return true;
        }

        private void takeShot(WebElement element, String fileName) throws IOException {
            // LOG.debug("Taking screenshot for {}.", fileName);
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            // Crop the entire page screenshot to get only element screenshot.
            BufferedImage eleScreenshot = ImageIO.read(screenshot).getSubimage(
                    element.getLocation().getX(), element.getLocation().getY(),
                    element.getSize().getWidth(), element.getSize().getHeight());
            ImageIO.write(eleScreenshot, "png", screenshot);
            FileUtils.copyFile(screenshot, new File(fileName));
            LOG.info("Updated {}.", fileName);
        }

        private void loginToAwsConsole(String username, String password) {
            LOG.info("Logging in to AWS console.");
            try {
                driver.get(Config.getInstance().getString(Config.AWS_SIGNIN_URL));
                doSleep(500);
                driver.get("https://console.aws.amazon.com/console/home");
                verifyTitle("Amazon Web Services Sign-In");
                driver.findElement(By.id("username")).sendKeys(username);
                driver.findElement(By.id("password")).sendKeys(password);
                driver.findElement(By.id("signin_button")).click();
            } catch (Exception e) {
                LOG.error("Error logging in to AWS console: ", e);
                LOG.info("Sleeping 10 seconds and trying again.");
                doSleep(10000);
                loginToAwsConsole(username, password);
                return;
            }
        }

        private void doSleep(long duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                LOG.error("Yawn... sleep interrupted: ", e);
            }
        }
    }

}
