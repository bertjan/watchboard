package nl.revolution.watchboard.plugins.cloudwatch;

import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.utils.WebDriverUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
            boolean timezoneSettingSucceeded = false;
            WebElement timeRangeDropdown =  driver.findElement(By.className("cwdb-time-range-dropdown"));
            timeRangeDropdown.findElement(By.tagName("button")).click();

            List<WebElement> spans = timeRangeDropdown.findElements(By.tagName("span")).stream().filter(element -> StringUtils.isNotBlank(element.getText())).collect(Collectors.toList());

            if (!spans.isEmpty()) {
                WebElement timeZoneElement = spans.get(spans.size()-1);
                timeZoneElement.click();

                Optional<WebElement> localTimeZoneElement = timeRangeDropdown.findElements(By.tagName("span")).stream().filter(elem -> "Local".equals(elem.getText())).findFirst();
                if (localTimeZoneElement.isPresent()) {
                    localTimeZoneElement.get().click();
                    timezoneSettingSucceeded = true;
                }
            }

            if (!timezoneSettingSucceeded) {
                LOG.error("Setting time zone failed for graph " + filename);
            }

            // Wait until dashboard is loaded.
            boolean graphLoaded = waitUntilGraphIsLoaded(filename);
            if (!graphLoaded) {
                return false;
            }

            Optional<WebElement> timeRangeInputField = driver.findElement(By.className("cwdb-time-range-controls")).findElements(By.tagName("input")).stream().filter(input -> "number".equals(input.getAttribute("type"))).findFirst();
            if (timeRangeInputField.isPresent()) {
                timeRangeInputField.get().clear();
                timeRangeInputField.get().sendKeys(String.valueOf(timeRange));
                timeRangeInputField.get().sendKeys(Keys.RETURN);
            }

            // Click refresh button.
            Optional<WebElement> refreshButton = driver.findElements(By.tagName("button")).stream().filter(button -> "refresh".equals(button.getAttribute("data-role"))).findFirst();
            if (refreshButton.isPresent()) {
                refreshButton.get().click();
            }

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
