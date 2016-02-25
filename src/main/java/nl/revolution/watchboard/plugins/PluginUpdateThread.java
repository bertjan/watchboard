package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

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
            plugins.forEach(plugin -> {
                if (!performSinglePluginUpdate(plugin)) {
                    LOG.error("Update run for plugin '" + plugin.getName() + "' failed, restarting browserInstance '" + browserInstance + "' for plugins " + pluginNames + ": ");
                    restartWebDriverAndReLogin();
                }
            });

            // Re-start webdriver and re-login every now and than to prevent session max duration issues.
            long currentSessionTimeInMinutes = ((System.currentTimeMillis() - currentSessionStartTimestamp) / 1000 / 60);
            LOG.info("currentSessionTimeInMinutes: " + currentSessionTimeInMinutes);
            if (currentSessionTimeInMinutes > Config.getInstance().getInt(Config.MAX_SESSION_DURATION_MINUTES)) {
                LOG.info("Max session duration exceeded, restarting browser instance '" + browserInstance + "'.");

                // Restart; this also resets the session duration timer.
                restartWebDriverAndReLogin();
            }
        }
    }

    private boolean performSinglePluginUpdate(WatchboardPlugin plugin) {
        long start = System.currentTimeMillis();
        String pluginName = plugin.getName();
        LOG.info("Performing update for plugin " + pluginName);

        // Perform update.
        try {
            plugin.performUpdate();
        } catch (Exception e) {
            LOG.error("Error while performing plugin update for plugin '" + plugin.getName() + "':", e);
            return false;
        }

        long end = System.currentTimeMillis();
        LOG.info("Done performing update for plugin " + pluginName + ". Update took " + ((end - start) / 1000) + " seconds.");

        // Wait before fetching next update.
//        int backendUpdateIntervalSeconds = plugin.getUpdateInterval();
//        LOG.debug("Sleeping {} seconds until next update for plugin " + pluginName + ".", backendUpdateIntervalSeconds);
//        doSleep(1000 * backendUpdateIntervalSeconds);
        return true;
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
