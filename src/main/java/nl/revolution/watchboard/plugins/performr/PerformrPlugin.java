package nl.revolution.watchboard.plugins.performr;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.WatchboardPlugin;
import nl.revolution.watchboard.utils.WebDriverUtils;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public class PerformrPlugin implements WatchboardPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(PerformrPlugin.class);

    private boolean stop;

    private Plugin performrPlugin;
    private WebDriverWrapper wrappedDriver;

    public PerformrPlugin(WebDriverWrapper wrappedDriver) {
        LOG.info("Starting Performr plugin.");
        performrPlugin = Config.getInstance().getPlugin(Graph.Type.PERFORMR);
        this.wrappedDriver = wrappedDriver;
    }


    @Override
    public void performLogin() {
        LOG.info("Logging in to Performr.");
        WebDriver driver = wrappedDriver.getDriver();

        driver.manage().window().setSize(new Dimension(2000, 1000));

        driver.get(performrPlugin.getLoginUrl());

        driver.findElement(By.id("username")).sendKeys(performrPlugin.getUsername());
        doSleep(1000);
        driver.findElement(By.id("password")).sendKeys(performrPlugin.getPassword());
        doSleep(1000);

        driver.findElements(By.tagName("input")).stream().forEach(input -> {
            if ("Inloggen".equals(input.getAttribute("value"))) {
                input.click();
            }
        });

    }


    @Override
    public void performUpdate() {
        long start = System.currentTimeMillis();
        LOG.info("Performing update for Performr graphs.");

        // Check for config file update.
        Config.getInstance().checkForConfigUpdate();

        // Generate reports for all graphs for all dashboards.
        LOG.info("Updating data from Performr.");
        Config.getInstance().getDashboards().stream().forEach(
                dashboard -> dashboard.getGraphs().stream().filter(graph -> graph.getType().equals(Graph.Type.PERFORMR)).forEach(graph -> {
                            if (!stop) {
                                performSingleUpdate(graph, false);
                            }
                        }
                ));

        long end = System.currentTimeMillis();
        LOG.info("Finished updating Performr graphs. Update took " + ((end-start)/1000) + " seconds.");
    }


    private void performSingleUpdate(Graph graph, boolean isRetry) {
        LOG.debug("Starting update of {}.", graph.getImagePath());
        WebDriver driver = wrappedDriver.getDriver();
        driver.get(performrPlugin.getLoginUrl());

        long loadingStart = System.currentTimeMillis();
        boolean found = false;
        while (!found) {
            List<WebElement> spans = driver.findElements(By.tagName("span"));
            for (WebElement span : spans) {
                if ("Selecteer component".equals(span.getText())) {
                    span.click();
                    found = true;
                }
            }
            LOG.debug("Did not find Performr component selection (yet), waiting.");
            doSleep(1000);
            long waitingForMS = System.currentTimeMillis() - loadingStart;
            if ((waitingForMS / 1000) > 10) {
                // Waited for over 30 seconds; break.
                LOG.error("Timed out waiting for Performr component selection to appear.");

                // Re-login to fix issue.
                performLogin();
                if (!isRetry) {
                    // if this isn't a retry already, try once again.
                    performSingleUpdate(graph, true);
                }
                return;
            }

        }

        // Disable all components.
        getComponentCheckbox("Alle").click();
        doSleep(500);

        // Select project components.
        graph.getComponents().stream().forEach(component -> {
            getComponentCheckbox(component).click();
            doSleep(500);
        });

        getPerformrScreenshot(graph.getBrowserWidth(), graph.getBrowserHeight(), graph.getImagePath());
    }


    private void getPerformrScreenshot(int width, int height, String filename) {
        WebDriver driver = wrappedDriver.getDriver();

        // Resize fix.
        Dimension size = driver.manage().window().getSize();
        driver.manage().window().setSize(new Dimension(200 , size.getHeight()));
        driver.manage().window().setSize(size);

        driver.manage().window().setSize(new Dimension(width, height));
        doSleep(500);
        try {
            WebDriverUtils.takeScreenShot(driver, driver.findElement(By.id("heatmap-holder")), filename);
        } catch (IOException e) {
            LOG.error("Error while taking screenshot: ", e);
        }

    }


    private WebElement getComponentCheckbox(String labelText) {
        WebDriver driver = wrappedDriver.getDriver();
        List<WebElement> elements = driver.findElements(By.tagName("label"));
        for (WebElement label : elements) {
            if (labelText.equals(label.getText())) {
                String checkboxId = label.getAttribute("for");
                return driver.findElement(By.id(checkboxId));
            }
        };
        return null;
    }


    @Override
    public void shutdown() {
        LOG.info("Shutting down.");
        this.stop = true;
    }


}
