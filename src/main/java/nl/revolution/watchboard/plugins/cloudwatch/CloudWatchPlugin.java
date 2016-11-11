package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.data.Graph;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

import static nl.revolution.watchboard.utils.WebDriverUtils.takeScreenShot;

public class CloudWatchPlugin extends AbstractCloudWatchPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchPlugin.class);

    protected void performSingleUpdate(Graph graph) {
        boolean executedSuccessfully = getReportScreenshot(graph.getUrl(),
                graph.getBrowserWidth(),
                graph.getBrowserHeight(),
                graph.getImagePath());
        if (!executedSuccessfully) {
            // Something went wrong; start over.
            throw new RuntimeException("CloudWatchPlugin performSingleUpdate failed.");
        }
    }

    private boolean getReportScreenshot(String reportUrl, int width, int height, String filename) {
        if (reportUrl == null) {
            LOG.error("reportUrl is null for filename " + filename);
            return true;
        }

        long start = System.currentTimeMillis();
        try {
            WebDriver driver = wrappedDriver.getDriver();
            LOG.debug("Starting update of {}", filename);
            driver.manage().window().setSize(new Dimension(width, height));

            // Perform dummy get to localhost to clear browser. This provides a workaround for rendering of an
            // axis that is not used.
            // String localURL = "http://localhost:" + Config.getInstance().getInt(Config.HTTP_PORT) + Config.getInstance().getContextRoot();
            // driver.get(localURL);

            loadPageAsync(driver, reportUrl);

            // Set time zone.
            WebElement timeRangeDropdown =  driver.findElement(By.cssSelector(".cwui-datepicker-dropdown-toggle"));
            timeRangeDropdown.findElement(By.cssSelector("a[role=\"button\"]")).click();

            WebElement datePickerDropDown = driver.findElement(By.cssSelector(".cwui-datepicker-dropdown"));

            // local timezone zetten
            WebElement timezoneSelector = datePickerDropDown.findElement(By.cssSelector(".cwui-datepicker-timezone-selector-select"));
            new Select(timezoneSelector).selectByVisibleText("Local timezone");

            // close timepicker
            timeRangeDropdown.findElement(By.cssSelector("a[role=\"button\"]")).click();

            // Wait until loading is finished.
            boolean graphLoaded = waitUntilGraphIsLoaded(filename);
            if (!graphLoaded) {
                return false;
            }

            try {
                takeScreenShot(driver, driver.findElement(By.cssSelector(".cwdb-standalone-graph-container-graph")), filename);
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
        return "CloudWatch";
    }

    @Override
    public Graph.Type getGraphType() { return Graph.Type.CLOUDWATCH; }

    @Override
    public int getUpdateInterval() {
        return plugin.getUpdateIntervalSeconds();
    }

}
