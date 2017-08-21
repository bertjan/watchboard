package nl.revolution.watchboard.plugins.kibana;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.WatchboardPlugin;
import nl.revolution.watchboard.utils.WebDriverUtils;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public class KibanaPlugin implements WatchboardPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(KibanaPlugin.class);
    private final Graph.Type type;

    private boolean stop;

    private Plugin plugin;
    private WebDriverWrapper wrappedDriver;

    public KibanaPlugin(Graph.Type type) {
        LOG.info("Starting Kibana plugin.");
        this.type = type;
        plugin = Config.getInstance().getPlugin(this.type);
    }

    @Override
    public void performLogin() {
        LOG.info("Logging in to Kibana.");
        try {
            WebDriver driver = wrappedDriver.getDriver();
            driver.manage().window().setSize(new Dimension(2000, 1000));
            driver.get(plugin.getLoginUrl());
            WebDriverUtils.verifyTitle(driver, "Kibana", 10);
        } catch (Exception e) {
            LOG.error("Error while logging in to Kibana: ", e);
        }

        LOG.info("Logged in to Kibana.");
    }


    @Override
    public void performUpdate() {
        long start = System.currentTimeMillis();
        LOG.info("Performing update for Kibana graphs.");

        // Check for config file update.
        Config.getInstance().checkForConfigUpdate();

        // Generate reports for all graphs for all dashboards.
        LOG.info("Updating data from Kibana.");
        Config.getInstance().getGrapsForType(type).stream().forEach(graph -> {
                if (!stop) {
                    performSingleUpdate(graph);
                }
            }
        );

        long end = System.currentTimeMillis();
        LOG.info("Finished updating " + getName() + " graphs. Update took " + ((end-start)/1000) + " seconds.");
    }


    private void performSingleUpdate(Graph graph) {
        LOG.debug("Starting update of {}.", graph.getImagePath());
        WebDriver driver = wrappedDriver.getDriver();
        driver.manage().window().setSize(new Dimension(2000, 1000));
        WebDriverUtils.fetchDummyPage(driver);

        driver.get(graph.getUrl());

        for (int i=0; i<30; i++) {
            String currentUrl = driver.getCurrentUrl();
            if (currentUrl.equals(graph.getUrl())) {
                // URL loaded.
                break;
            }
            doSleep(500);
        }

        // Wait until dashboard panels are rendered.
        int size = 0;
        for (int i=0; i<60; i++) {
            size = WebDriverUtils.numberOfElements(driver, By.tagName("visualize"));
            if (size > 0) {
                break;
            }
            doSleep(500);
        }

        // If no items were found, skip screenshot.
        if (size == 0) {
            LOG.info("No Kibana visualizations found; skipping screenshot.");
            try {
                WebDriverUtils.takeScreenShot(driver, driver.findElement(By.tagName("html")), graph.getImagePath()+"-debug.png");
            } catch (IOException e) {
                LOG.error("Error while taking debug screenshot for " + graph.getId() + ": ", e);
            }
            return;
        }

        // Wait until a visualization chart is present.
        for (int i=0; i<20; i++) {
            size = WebDriverUtils.numberOfElements(driver, By.className("visualize-chart"));
            // LOG.info("visualize-chart size: " + size);
            if (size > 0) {
                break;
            }
            doSleep(500);
        }

        // Wait two more seconds to allow for rendering to complete ...
        doSleep(2000);

        getKibanaScreenshot(graph.getBrowserWidth(), graph.getBrowserHeight(), graph.getImagePath());

        plugin.setTsLastUpdated(LocalDateTime.now());
    }


    private void getKibanaScreenshot(int width, int height, String filename) {
        WebDriver driver = wrappedDriver.getDriver();
        driver.manage().window().setSize(new Dimension(width, height));

        try {
            WebDriverUtils.takeScreenShot(driver, driver.findElement(By.tagName("dashboard-grid")), filename);
        } catch (IOException e) {
            LOG.error("Error while taking screenshot: ", e);
        }
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

    @Override
    public String getName() {
        return "Kibana";
    }

    @Override
    public int getUpdateInterval() {
        return plugin.getUpdateIntervalSeconds();
    }

}
