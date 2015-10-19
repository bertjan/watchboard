package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.utils.WebDriverWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static nl.revolution.watchboard.utils.WebDriverUtils.doSleep;

public class PluginUpdateThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUpdateThread.class);
    private WebDriverWrapper wrappedDriver;

    private WatchboardPlugin watchboardPlugin;
    private boolean stop;
    private long currentSessionStartTimestamp;
    private String pluginName;

    public PluginUpdateThread(WatchboardPlugin plugin) {
        this.watchboardPlugin = plugin;
        this.pluginName = watchboardPlugin.getName();
    }


    public void run() {
        LOG.info("Starting data worker for plugin " + pluginName);
        currentSessionStartTimestamp = System.currentTimeMillis();

        wrappedDriver = new WebDriverWrapper();
        wrappedDriver.start();

        watchboardPlugin.setDriver(wrappedDriver);
        watchboardPlugin.performLogin();

        LOG.info("Starting main update loop for plugin " + pluginName);

        while (!stop) {
            try {
                long start = System.currentTimeMillis();
                LOG.info("Performing update for plugin " + pluginName);

                // Perform update.
                watchboardPlugin.performUpdate();

                long end = System.currentTimeMillis();
                LOG.info("Done performing update for plugin " + pluginName + ". Update took " + ((end - start) / 1000) + " seconds.");

                // Wait before fetching next update.
                int backendUpdateIntervalSeconds = watchboardPlugin.getUpdateInterval();
                LOG.debug("Sleeping {} seconds until next update for plugin " + pluginName + ".", backendUpdateIntervalSeconds);
                doSleep(1000 * backendUpdateIntervalSeconds);
            } catch (Exception e) {
                LOG.error("Caught exception in main update loop for plugin " + pluginName + ": ", e);
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

    public void doStop() {
        stop = true;
        watchboardPlugin.shutdown();
    }

    private void restartWebDriverAndReLogin() {
        wrappedDriver.restart();
        watchboardPlugin.performLogin();
        currentSessionStartTimestamp = System.currentTimeMillis();
    }

}
