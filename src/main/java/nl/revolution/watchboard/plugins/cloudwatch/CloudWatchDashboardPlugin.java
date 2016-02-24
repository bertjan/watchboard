package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.data.Graph;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;
import static nl.revolution.watchboard.utils.WebDriverUtils.takeScreenShot;

public class CloudWatchDashboardPlugin extends AbstractCloudWatchPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchDashboardPlugin.class);

    protected void performSingleUpdate(Graph graph) {
        LOG.info("performing single update for graph " + graph.getId());
        boolean executedSuccessfully = getDashboardScreenshot(graph.getUrl(),
                graph.getBrowserWidth(),
                graph.getBrowserHeight(),
                graph.getImagePath());
        if (!executedSuccessfully) {
            // Something went wrong; start over.
            wrappedDriver.restart();
            performLogin();
        }
    }

    private boolean getDashboardScreenshot(String reportUrl, int width, int height, String filename) {
        long start = System.currentTimeMillis();
        try {
            WebDriver driver = wrappedDriver.getDriver();
            LOG.debug("Starting update of {}", filename);
            driver.manage().window().setSize(new Dimension(width, height));

            loadPageAsync(driver, reportUrl);

            // Wait until dashboard is loaded.
            boolean graphLoaded = waitUntilGraphIsLoaded(filename);
            if (!graphLoaded) {
                return false;
            }

            // Wait until individual graphs are loaded.
            doSleep(500);
            graphLoaded = waitUntilGraphIsLoaded(filename);
            if (!graphLoaded) {
                return false;
            }

            try {
                takeScreenShot(driver, driver.findElement(By.className("react-grid-layout")), filename);
            } catch (IOException e) {
                LOG.error("Error while taking screenshot:", e);
                return false;
            }
        } catch (WebDriverException e) {
            LOG.error("Caught WebDriverException: ", e);
            LOG.error("Error occurred while fetching report for {} ", filename);
            return false;
        }
        plugin.setTsLastUpdated(LocalDateTime.now());
        long end = System.currentTimeMillis();
        LOG.info("Updating " + filename + " took " + (end - start) + " ms.");
        return true;
    }

    @Override
    public String getName() {
        return "CloudWatchDashboard";
    }

    @Override
    public Graph.Type getGraphType() { return Graph.Type.CLOUDWATCH_DASHBOARD; }

    @Override
    public int getUpdateInterval() {
        return plugin.getUpdateIntervalSeconds();
    }

}
