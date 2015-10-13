package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.WatchboardPlugin;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;
import static nl.revolution.watchboard.utils.WebDriverUtils.takeScreenShot;

public class CloudWatchPlugin implements WatchboardPlugin {

    private static final String MAXIMIZE_IMAGE_CONTENT = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAOCAYAAADwikbvAAAAHElEQVR42mNwcXH5Ty5mGHjNpIBRzSNUM92TJwDOA1GY0jTyxAAAAABJRU5ErkJggg==";
    private static final int MAX_GRAPH_LOADING_TIME_IN_SECONDS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchPlugin.class);

    private boolean stop;

    private Plugin cloudWatchPlugin;
    private WebDriverWrapper wrappedDriver;

    public CloudWatchPlugin(WebDriverWrapper wrappedDriver) {
        LOG.info("Starting CloudWatch plugin.");
        cloudWatchPlugin = Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH);
        this.wrappedDriver = wrappedDriver;
    }

    @Override
    public void performLogin() {
        LOG.info("Logging in to AWS console.");
        WebDriver driver = wrappedDriver.getDriver();
        try {
            driver.get(cloudWatchPlugin.getLoginUrl());
            doSleep(500);
            driver.get("https://console.aws.amazon.com/console/home");
            verifyTitle("Amazon Web Services Sign-In");
            driver.findElement(By.id("username")).sendKeys(cloudWatchPlugin.getUsername());
            driver.findElement(By.id("password")).sendKeys(cloudWatchPlugin.getPassword());
            driver.findElement(By.id("signin_button")).click();
        } catch (Exception e) {
            LOG.error("Error logging in to AWS console: ", e);
            LOG.info("Sleeping 10 seconds and trying again.");
            doSleep(10000);
            wrappedDriver.restart();
            performLogin();
            return;
        }
    }

    @Override
    public void performUpdate() {
        LOG.info("Performing update.");

        // Check for config file update.
        Config.getInstance().checkForConfigUpdate();

        // Generate reports for all graphs for all dashboards.
        LOG.info("Updating data from AWS.");
        Config.getInstance().getDashboards().stream().forEach(
                dashboard -> dashboard.getGraphs().stream().filter(graph -> graph.getType().equals(Graph.Type.CLOUDWATCH)).forEach(graph -> {
                            if (!stop) {
                                boolean executedSuccessfully = getReportScreenshot(graph.getUrl(),
                                        graph.getBrowserWidth(),
                                        graph.getBrowserHeight(),
                                        graph.getImagePath());
                                if (!executedSuccessfully) {
                                    // Something went wrong; start over.
                                    wrappedDriver.restart();
                                    performLogin();
                                }
                            }
                        }
                ));
        LOG.info("Update finished.");
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down.");
        this.stop = true;
    }

    private void verifyTitle(String expectedTitle) {
        long timeout = 3;
        WebDriver driver = wrappedDriver.getDriver();
        new WebDriverWait(driver, timeout).until(ExpectedConditions.titleIs(expectedTitle));
        if (!expectedTitle.equals(driver.getTitle())) {
            LOG.error("Expected title '{}' does not match actual title '{}'.", expectedTitle, driver.getTitle());
        }
    }

    private boolean getReportScreenshot(String reportUrl, int width, int height, String filename) {
        try {
            WebDriver driver = wrappedDriver.getDriver();
            LOG.debug("Starting update of {}", filename);

            // Perform dummy get to localhost to clear browser. This provides a workaround for rendering of an
            // axis that is not used.
            String localURL = "http://localhost:" + Config.getInstance().getInt(Config.HTTP_PORT) + Config.getInstance().getContextRoot();
            driver.get(localURL);
            driver.manage().window().setSize(new Dimension(width, height));
            driver.get(reportUrl);
            doSleep(100);

            // Select bottom option in timezone select (local time).
            Select timezoneSelect = new Select(driver.findElement(By.id("gwt-debug-timezoneList")));
            timezoneSelect.selectByIndex(timezoneSelect.getOptions().size() - 1);

            driver.findElement(By.id("gwt-debug-detailPanel")).findElements(By.className("gwt-Image")).stream().forEach(image -> {
                if (MAXIMIZE_IMAGE_CONTENT.equals(image.getAttribute("src"))) {
                    image.click();
                }
            });

            doSleep(100);

            driver.findElement(By.id("gwt-debug-detailPanel")).findElements(By.tagName("button")).stream().forEach(button -> {
                if ("Update Graph".equals(button.getAttribute("title"))) {
                    button.click();
                }
            });

            doSleep(100);

            // Wait until loading is finished.
            boolean graphLoaded = waitUntilGraphIsLoaded(filename);
            if (!graphLoaded) {
                return false;
            }

            doSleep(500);

            try {
                takeScreenShot(driver, driver.findElement(By.id("gwt-debug-graphContainer")), filename);
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


    private boolean waitUntilGraphIsLoaded(String filename) {
        long loadingStart = System.currentTimeMillis();
        WebDriver driver = wrappedDriver.getDriver();
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
        return true;
    }

}
