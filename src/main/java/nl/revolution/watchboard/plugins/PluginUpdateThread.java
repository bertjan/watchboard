package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchPlugin;
import nl.revolution.watchboard.plugins.performr.PerformrPlugin;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public class PluginUpdateThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUpdateThread.class);
    private WebDriverWrapper wrappedDriver;

    private WatchboardPlugin cloudWatchPlugin;
    private WatchboardPlugin performrPlugin;
    private boolean stop;
    private long currentSessionStartTimestamp;


    public void run() {
        LOG.info("Starting data worker");
        currentSessionStartTimestamp = System.currentTimeMillis();

        wrappedDriver = new WebDriverWrapper();
        wrappedDriver.start();

        // TODO: make this a list of plugins based on the plugin data from config
        cloudWatchPlugin = new CloudWatchPlugin(wrappedDriver);
        cloudWatchPlugin.performLogin();

        performrPlugin = new PerformrPlugin(wrappedDriver);
        performrPlugin.performLogin();

        LOG.info("Starting main update loop.");

        while (!stop) {
            // Perform update for all plugins.
            performrPlugin.performUpdate();
            cloudWatchPlugin.performUpdate();

            // Wait before fetching next update.
            int backendUpdateIntervalSeconds = Config.getInstance().getInt(Config.BACKEND_UPDATE_INTERVAL_SECONDS);
            LOG.debug("Sleeping {} seconds until next update.", backendUpdateIntervalSeconds);
            doSleep(1000 * backendUpdateIntervalSeconds);

            // Re-start webdriver and re-login every now and than to prevent session max duration issues.
            long currentSessionTimeInMinutes = ((System.currentTimeMillis() - currentSessionStartTimestamp) / 1000 / 60);
            LOG.info("currentSessionTimeInMinutes: " + currentSessionTimeInMinutes);
            if (currentSessionTimeInMinutes > Config.getInstance().getInt(Config.MAX_SESSION_DURATION_MINUTES)) {
                LOG.info("Max session duration exceeded, restarting browser.");

                // Restart; this also resets the session duration timer.
                restartWebDriverAndReLoginForAllPlugins();
            }
        }
    }

    public void doStop() {
        stop = true;
        cloudWatchPlugin.shutdown();
        performrPlugin.shutdown();
    }

    private void restartWebDriverAndReLoginForAllPlugins() {
        wrappedDriver.restart();
        cloudWatchPlugin.performLogin();
        performrPlugin.performLogin();
        currentSessionStartTimestamp = System.currentTimeMillis();
    }

}
