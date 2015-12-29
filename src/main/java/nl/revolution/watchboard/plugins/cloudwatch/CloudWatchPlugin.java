package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.WatchboardPlugin;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;
import static nl.revolution.watchboard.utils.WebDriverUtils.takeScreenShot;

public class CloudWatchPlugin implements WatchboardPlugin {

    private static final String MAXIMIZE_IMAGE_CONTENT = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAOCAYAAADwikbvAAAAHElEQVR42mNwcXH5Ty5mGHjNpIBRzSNUM92TJwDOA1GY0jTyxAAAAABJRU5ErkJggg==";
    private static final int MAX_GRAPH_LOADING_TIME_IN_SECONDS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchPlugin.class);
    public static final double GRAPH_CANVAS_FILL_RATE_THRESHOLD = 0.08d;

    private boolean stop;

    private Plugin cloudWatchPlugin;
    private WebDriverWrapper wrappedDriver;

    public CloudWatchPlugin() {
        LOG.info("Starting CloudWatch plugin.");
        cloudWatchPlugin = Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH);
    }

    @Override
    public void performLogin() {
        LOG.info("Logging in to AWS console.");
        WebDriver driver = wrappedDriver.getDriver();
        try {
            driver.manage().window().setSize(new Dimension(800, 600));
            driver.get(cloudWatchPlugin.getLoginUrl());
            doSleep(500);
            driver.get("https://console.aws.amazon.com/console/home");
            verifyTitle("Amazon Web Services Sign-In");
            driver.findElement(By.id("username")).sendKeys(cloudWatchPlugin.getUsername());
            driver.findElement(By.id("password")).sendKeys(cloudWatchPlugin.getPassword());
            driver.findElement(By.id("signin_button")).click();

            // Wait for the login request to complete.
            for (int i=0; i<10; i++) {
                if (driver.getCurrentUrl().contains("signin.aws.amazon.com")) {
                    // Still on the login page.
                    LOG.debug("Waiting for login process to complete.");
                    doSleep(500);
                } else {
                    break;
                }
            }

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
        long start = System.currentTimeMillis();
        LOG.info("Performing update for CloudWatch graphs.");

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

        long end = System.currentTimeMillis();
        LOG.info("Finished updating CloudWatch graphs. Update took " + ((end-start)/1000) + " seconds.");
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
        long start = System.currentTimeMillis();
        try {
            WebDriver driver = wrappedDriver.getDriver();
            LOG.debug("Starting update of {}", filename);
            driver.manage().window().setSize(new Dimension(width, height));

            // Perform dummy get to localhost to clear browser. This provides a workaround for rendering of an
            // axis that is not used.
            String localURL = "http://localhost:" + Config.getInstance().getInt(Config.HTTP_PORT) + Config.getInstance().getContextRoot();
            driver.get(localURL);

            loadPageAsync(driver, reportUrl);

            // Select bottom option in timezone select (local time).
            Select timezoneSelect = new Select(driver.findElement(By.id("gwt-debug-timezoneList")));
            timezoneSelect.selectByIndex(timezoneSelect.getOptions().size() - 1);

            driver.findElement(By.id("gwt-debug-detailPanel")).findElements(By.className("gwt-Image")).stream().forEach(image -> {
                if (MAXIMIZE_IMAGE_CONTENT.equals(image.getAttribute("src"))) {
                    image.click();
                }
            });

            // Trigger re-draw to prevent drawing issues.
            triggerGraphRedraw(driver);

            // Wait until loading is finished.
            boolean graphLoaded = waitUntilGraphIsLoaded(filename);
            if (!graphLoaded) {
                return false;
            }

            double initialFillRate = getGraphCanvasFillRate(driver);

            if (initialFillRate < GRAPH_CANVAS_FILL_RATE_THRESHOLD) {
                LOG.debug(filename + ": fillRate " + initialFillRate + " is below threshold, entering retry loop.");

                // try/retry process
                for (int retry = 0; retry < 2; retry++) {

                    // wait a bit for the rendering to finish
                    doSleep(250);

                    double fillRate = getGraphCanvasFillRate(driver);
                    LOG.debug(filename + ": fillRate at start of retry iteration " + retry + ": " + fillRate);

                    if (fillRate > GRAPH_CANVAS_FILL_RATE_THRESHOLD) {
                        LOG.debug(filename + ": fillRate (" + fillRate + ") is above threshold at retry iteration " + retry + ", breaking - initial fillRate was " + initialFillRate + ".");
                        break;
                    }

                    LOG.debug(filename + ": fillRate is still below threshold, triggering redraw");

                    // Redraw, wait until loading is finished.
                    triggerGraphRedraw(driver);
                    graphLoaded = waitUntilGraphIsLoaded(filename);
                    if (!graphLoaded) {
                        return false;
                    }

                    if (fillRate > GRAPH_CANVAS_FILL_RATE_THRESHOLD) {
                        LOG.debug(filename + ": fillRate (" + fillRate + ") is above threshold after redraw at retry iteration " + retry + ", breaking - initial fillRate was " + initialFillRate + ".");
                        break;
                    }

                    if (retry == 2) {
                        LOG.debug(filename + ": fillRate at " + fillRate + ", max redraw retries exceeded, giving up for now.");
                    }

                }
            }


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
        long end = System.currentTimeMillis();
        LOG.info("Updating " + filename + " took " + (end - start) + " ms.");
        return true;
    }

    private void triggerGraphRedraw(WebDriver driver) {
        driver.findElement(By.id("gwt-debug-detailPanel")).findElements(By.tagName("button")).stream().forEach(button -> {
            if ("Update Graph".equals(button.getAttribute("title"))) {
                button.click();
            }
        });
    }

    private Double getGraphCanvasFillRate(WebDriver driver) {
        String result = (String) ((JavascriptExecutor)driver).executeScript(
                "var canvas = document.getElementsByClassName('flot-base')[0]\n" +
                "var cx = canvas.getContext('2d');" +
                "var pixels = cx.getImageData(0, 0, canvas.width, canvas.height);\n" +
                "var len = pixels.data.length;\n" +
                "var white = 0;\n" +
                "var filled = 0;\n" +
                "for (i = 0; i < len; i += 4) {\n" +
                "  if (pixels.data[i] === 0 &&\n" +
                "      pixels.data[i+1] === 0 &&\n" +
                "      pixels.data[i+2] === 0) {\n" +
                "    white++;\n" +
                "  } else {\n" +
                "    filled++;\n" +
                "  }\n" +
                "}\n" +
                "return white + ',' + filled;");
        int white = Integer.parseInt(result.split(",")[0]);
        int filled = Integer.parseInt(result.split(",")[1]);
        Double fillRate = ((double)filled / (filled + white));
        return fillRate;
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

    private void loadPageAsync(WebDriver driver, String url) {
        // Trick to speed up page loading.
        driver.manage().timeouts().pageLoadTimeout(0, TimeUnit.MILLISECONDS);
        try {
            driver.get(url);
        } catch (TimeoutException ignored) {
            // Expected, do nothing.
        }

        // Back to original timeout.
        driver.manage().timeouts().pageLoadTimeout(WebDriverWrapper.WEBDRIVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void setDriver(WebDriverWrapper driver) {
        this.wrappedDriver = driver;
    }

    @Override
    public String getName() {
        return "CloudWatch";
    }

    @Override
    public int getUpdateInterval() {
        return cloudWatchPlugin.getUpdateIntervalSeconds();
    }


}
