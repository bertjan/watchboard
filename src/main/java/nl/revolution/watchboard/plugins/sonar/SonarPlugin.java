package nl.revolution.watchboard.plugins.sonar;

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
import java.util.List;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public class SonarPlugin implements WatchboardPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(SonarPlugin.class);

    private boolean stop;

    private Plugin plugin;
    private WebDriverWrapper wrappedDriver;

    public SonarPlugin() {
        LOG.info("Starting Sonar plugin.");
        plugin = Config.getInstance().getPlugin(Graph.Type.SONAR);
    }

    @Override
    public void performLogin() {
        LOG.info("Logging in to Sonar.");
        try {
            WebDriver driver = wrappedDriver.getDriver();
            driver.manage().window().setSize(new Dimension(2000, 1000));
            WebDriverUtils.fetchDummyPage(driver);
            driver.get(plugin.getLoginUrl());
            WebDriverUtils.verifyTitle(driver, "SonarQube", 10);

            driver.findElement(By.id("login")).sendKeys(plugin.getUsername());
            driver.findElement(By.id("password")).sendKeys(plugin.getPassword());
            driver.findElement(By.id("password")).submit();
        } catch (Exception e) {
            LOG.error("Error while logging in to Sonar: ", e);
        }

        LOG.info("Logged in to Sonar.");
    }


    @Override
    public void performUpdate() {
        long start = System.currentTimeMillis();
        LOG.info("Performing update for Sonar graphs.");

        // Check for config file update.
        Config.getInstance().checkForConfigUpdate();

        // Generate reports for all graphs for all dashboards.
        LOG.info("Updating data from Sonar.");
        Config.getInstance().getGrapsForType(Graph.Type.SONAR).stream().forEach(graph -> {
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

        // Wait for the screen to load.
        doSleep(500);

        JavascriptExecutor executor = (JavascriptExecutor)driver;

        // Give tiles list some padding at the top.
        WebElement tilesList = driver.findElement(By.className("overview-domains-list"));
        executor.executeScript("arguments[0].style.padding='5px 0 0 0';", tilesList);

        // Hide all tiles except the top 2.
        List<WebElement> sonarTiles = tilesList.findElements(By.className("overview-card"));
        for (int i=0; i<sonarTiles.size(); i++) {
            if (i>1) {
                WebElement tile = sonarTiles.get(i);
                executor.executeScript("arguments[0].style.display='none';", tile);
            }
        }

        // Wait for the screen to load.
        doSleep(500);

        getSonarScreenshot(graph.getBrowserWidth(), graph.getBrowserHeight(), graph.getImagePath());

        plugin.setTsLastUpdated(LocalDateTime.now());
    }


    private void getSonarScreenshot(int width, int height, String filename) {
        WebDriver driver = wrappedDriver.getDriver();
        driver.manage().window().setSize(new Dimension(width, height));
        try {
            WebDriverUtils.takeScreenShot(driver, driver.findElement(By.className("overview-domains-list")), filename);
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
        return "Sonar";
    }

    @Override
    public int getUpdateInterval() {
        return plugin.getUpdateIntervalSeconds();
    }

}
