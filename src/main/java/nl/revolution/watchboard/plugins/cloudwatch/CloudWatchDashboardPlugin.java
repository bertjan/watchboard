package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.utils.WebDriverUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;
import static nl.revolution.watchboard.utils.WebDriverUtils.takeScreenShot;

public class CloudWatchDashboardPlugin extends AbstractCloudWatchPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchDashboardPlugin.class);

    protected void performSingleUpdate(Graph graph) {
        boolean executedSuccessfully = getDashboardScreenshot(graph.getUrl(),
                graph.getBrowserWidth(),
                graph.getBrowserHeight(),
                graph.getTimeRange(),
                graph.getImagePath());
        if (!executedSuccessfully) {
            // Something went wrong; start over.
            throw new RuntimeException("CloudWatchDashboardPlugin performSingleUpdate failed.");
        }
    }

    private boolean getDashboardScreenshot(String reportUrl, int width, int height, int timeRange, String filename) {
        long start = System.currentTimeMillis();
        try {
            WebDriver driver = wrappedDriver.getDriver();
            LOG.debug("Starting update of {}", filename);
            driver.manage().window().setSize(new Dimension(width, height));
            WebDriverUtils.fetchDummyPage(driver);
            loadPageAsync(driver, reportUrl);

            // Set time zone.
            WebElement timeRangeDropdown =  driver.findElement(By.cssSelector(".cwui-datepicker-dropdown-toggle"));
            timeRangeDropdown.findElement(By.cssSelector("a[role=\"button\"]")).click();

            WebElement datePickerDropDown = driver.findElement(By.cssSelector(".cwui-datepicker-dropdown"));

            // local timezone zetten
            WebElement timezoneSelector = datePickerDropDown.findElement(By.cssSelector(".cwui-datepicker-timezone-selector-select"));
            new Select(timezoneSelector).selectByVisibleText("Local timezone");

            // timerange selecteren
            datePickerDropDown.findElements(By.cssSelector(".cwui-datepicker-duration-row"))
                    .get(1)
                    .findElements(By.tagName("button")).stream()
                    .filter(elem -> elem.getText().equals(String.valueOf(timeRange)))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find button for timerage: " + timeRange))
                    .click();

            // Next step: wait for the individual graphs to be loaded.
            // First, wait up to two seconds for 'loading' indicators to appear.
            doSleep(250);
            for (int i=0; i<20; i++) {
                int loading = visibleLoadingIcons(driver);
                if (loading > 0) {
                    // Loading icon found; stop waiting.
                    break;
                }

                // Loading icon not (yet) found, wait a bit and retry.
                doSleep(100);
            }

            // Wait until all individual graphs are loaded.
            boolean graphLoaded = waitUntilGraphIsLoaded(filename);
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
