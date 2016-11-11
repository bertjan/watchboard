package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.WatchboardPlugin;
import nl.revolution.watchboard.utils.WebDriverUtils;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public abstract class AbstractCloudWatchPlugin implements WatchboardPlugin {

    private static final int MAX_GRAPH_LOADING_TIME_IN_SECONDS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCloudWatchPlugin.class);

    protected static final String MAXIMIZE_IMAGE_CONTENT = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAOCAYAAADwikbvAAAAHElEQVR42mNwcXH5Ty5mGHjNpIBRzSNUM92TJwDOA1GY0jTyxAAAAABJRU5ErkJggg==";
    protected Plugin plugin;
    protected WebDriverWrapper wrappedDriver;

    protected boolean stop;

    public AbstractCloudWatchPlugin() {
        LOG.info("Starting CloudWatch plugin.");
        plugin = Config.getInstance().getPlugin(getGraphType());
    }

    @Override
    public void performLogin() {
        LOG.info("Logging in to AWS console.");
        WebDriver driver = wrappedDriver.getDriver();
        try {
            driver.manage().window().setSize(new Dimension(800, 600));
            driver.get(plugin.getLoginUrl());
            doSleep(500);
            driver.get("https://console.aws.amazon.com/console/home");
            WebDriverUtils.verifyTitle(driver, "Amazon Web Services Sign-In", 3);
            driver.findElement(By.id("username")).sendKeys(plugin.getUsername());
            driver.findElement(By.id("password")).sendKeys(plugin.getPassword());
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
        LOG.info("Performing update for " + getName() + " graphs.");

        // Check for config file update.
        Config.getInstance().checkForConfigUpdate();

        // Generate reports for all graphs for all dashboards.
        LOG.info("Updating data from AWS.");
        Config.getInstance().getGrapsForType(getGraphType()).stream().forEach(graph -> {
                if (!stop) {
                    performSingleUpdate(graph);
                }
            }
        );

        long end = System.currentTimeMillis();
        LOG.info("Finished updating " + getName() + " graphs. Update took " + ((end-start)/1000) + " seconds.");
    }

    protected void loadPageAsync(WebDriver driver, String url) {
        // Trick to speed up page loading.
        WebDriverUtils.disableTimeouts(driver);
        try {
            driver.get(url);
        } catch (TimeoutException ignored) {
            // Expected, do nothing.
        }

        // Back to original timeout.
        WebDriverUtils.enableTimeouts(driver);
    }

    protected boolean waitUntilGraphIsLoaded(String filename) {
        long loadingStart = System.currentTimeMillis();
        WebDriver driver = wrappedDriver.getDriver();
        while (true) {
            boolean isLoading = visibleLoadingIcons(driver) > 0;
            if (!isLoading) {
                doSleep(250);
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
            doSleep(100);
        }
        return true;
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down.");
        this.stop = true;
    }

    @Override
    public void setDriver(WebDriverWrapper driver) {
        this.wrappedDriver = driver;
    }


    abstract Graph.Type getGraphType();
    abstract void performSingleUpdate(Graph graph);

    protected int visibleLoadingIcons(WebDriver driver) {
        return WebDriverUtils.numberOfElements(driver, By.cssSelector(".cwdb-loader-container"));
    }

}
