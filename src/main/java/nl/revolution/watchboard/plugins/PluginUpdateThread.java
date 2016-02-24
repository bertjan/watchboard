package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public class PluginUpdateThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUpdateThread.class);
    private WebDriverWrapper wrappedDriver;

    private List<WatchboardPlugin> plugins;
    private boolean stop;
    private long currentSessionStartTimestamp;
    private String browserInstance;
    private String pluginNames;

    public PluginUpdateThread(String browserInstance, List<WatchboardPlugin> plugins) {
        this.browserInstance = browserInstance;
        this.plugins = plugins;
        this.pluginNames = StringUtils.join(plugins.stream().map(WatchboardPlugin::getName).collect(Collectors.toList()));
    }

    public void run() {
        LOG.info("Starting data worker for browser instance '" + browserInstance + "' with plugins " + pluginNames + ".");
        currentSessionStartTimestamp = System.currentTimeMillis();

        wrappedDriver = new WebDriverWrapper();
        wrappedDriver.start();

        plugins.forEach(plugin -> plugin.setDriver(wrappedDriver));
        plugins.forEach(WatchboardPlugin::performLogin);

        LOG.info("Starting main update loop for plugins " + pluginNames);

        while (!stop) {
            try {
                plugins.forEach(this::performSinglePluginUpdate);
            } catch (Exception e) {
                LOG.error("Caught exception in main update loop for browserInstance '" + browserInstance + "' and plugins " + pluginNames + ": ", e);
                restartWebDriverAndReLogin();
            }

            // Re-start webdriver and re-login every now and than to prevent session max duration issues.
            long currentSessionTimeInMinutes = ((System.currentTimeMillis() - currentSessionStartTimestamp) / 1000 / 60);
            LOG.info("currentSessionTimeInMinutes: " + currentSessionTimeInMinutes);
            if (currentSessionTimeInMinutes > Config.getInstance().getInt(Config.MAX_SESSION_DURATION_MINUTES)) {
                LOG.info("Max session duration exceeded, restarting browser.");

                // Restart; this also resets the session duration timer.
                restartWebDriverAndReLogin();
            }
        }
    }

    private void performSinglePluginUpdate(WatchboardPlugin plugin) {
        long start = System.currentTimeMillis();
        String pluginName = plugin.getName();
        LOG.info("Performing update for plugin " + pluginName);

        // Perform update.
        plugin.performUpdate();

        long end = System.currentTimeMillis();
        LOG.info("Done performing update for plugin " + pluginName + ". Update took " + ((end - start) / 1000) + " seconds.");

        // Wait before fetching next update.
        int backendUpdateIntervalSeconds = plugin.getUpdateInterval();
        LOG.debug("Sleeping {} seconds until next update for plugin " + pluginName + ".", backendUpdateIntervalSeconds);
        doSleep(1000 * backendUpdateIntervalSeconds);
    }

    public void doStop() {
        stop = true;
        plugins.forEach(WatchboardPlugin::shutdown);
    }

    private void restartWebDriverAndReLogin() {
        wrappedDriver.restart();
        plugins.forEach(WatchboardPlugin::performLogin);
        currentSessionStartTimestamp = System.currentTimeMillis();
    }

}
