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

    private boolean stop;

    private Plugin plugin;
    private WebDriverWrapper wrappedDriver;

    public KibanaPlugin() {
        LOG.info("Starting Kibana plugin.");
        plugin = Config.getInstance().getPlugin(Graph.Type.KIBANA);
    }

    @Override
    public void performLogin() {
        LOG.info("Logging in to Kibana.");
        try {
            WebDriver driver = wrappedDriver.getDriver();
            driver.manage().window().setSize(new Dimension(2000, 1000));
            driver.get(plugin.getLoginUrl());
            WebDriverUtils.verifyTitle(driver, "Kibana 4", 5);
        } catch (Exception e) {
            LOG.error("Error while logging in to Performr: ", e);
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
        Config.getInstance().getGrapsForType(Graph.Type.KIBANA).stream().forEach(graph -> {
                if (!stop) {
                    performSingleUpdate(graph);
                }
            }
        );

        long end = System.currentTimeMillis();
        LOG.info("Finished updating " + getName() + " graphs. Update took " + ((end-start)/1000) + " seconds.");
    }


    private void performSingleUpdate(Graph graph) {
        try {
            LOG.debug("Starting update of {}.", graph.getImagePath());
            WebDriver driver = wrappedDriver.getDriver();
            driver.manage().window().setSize(new Dimension(2000, 1000));
            driver.get("http://localhost:" + Config.getInstance().getInt(Config.HTTP_PORT));

            driver.get(graph.getUrl());

            // Wait until dashboard panels are rendered.
            int size = 0;
            for (int i=0; i<20; i++) {
                size = WebDriverUtils.numberOfElements(driver, By.tagName("visualize"));
                if (size > 0) {
                    break;
                }
                doSleep(500);
            }

            // If no items were found, skip screenshot.
            if (size == 0) {
                LOG.info("No Kibana visualizations found; skipping screenshot.");
                return;
            }

            // Wait until a visualization chart is present.
            size = 0;
            for (int i=0; i<20; i++) {
                size = WebDriverUtils.numberOfElements(driver, By.className("visualize-chart"));
                // LOG.info("visualize-chart size: " + size);
                if (size > 0) {
                    break;
                }
                doSleep(500);
            }


            if (WebDriverUtils.numberOfElements(driver, By.className("vis-wrapper")) > 0) {
                LOG.info("vis-wrapper exists");
                // TODO: is dit nodig?
                // Find svg's in the first visualization.
//                int svgs = driver.findElement(By.tagName("visualize")).findElements(By.tagName("svg")).size();
//                if (svgs == 0) {
//                    LOG.info("No Kibana visualizations found; skipping screenshot.");
//                    return;
//                }

            }


            // Wait one more second to allow for rendering to complete ...
            doSleep(1000);

            getKibanaScreenshot(graph.getBrowserWidth(), graph.getBrowserHeight(), graph.getImagePath());
        } catch (Exception e) {
            LOG.error("Exception while performing Kibana update: ", e);
        }

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
        return "Performr";
    }

    @Override
    public int getUpdateInterval() {
        return plugin.getUpdateIntervalSeconds();
    }

}
