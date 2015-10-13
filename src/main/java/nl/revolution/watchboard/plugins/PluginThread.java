package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(PluginThread.class);

    private WatchboardPlugin cloudWatchPlugin;
    private boolean stop;

    public void run() {
        LOG.info("Starting data worker");

        // TODO: make this a list of plugins based on the plugin data from config
        cloudWatchPlugin = new CloudWatchPlugin();
        cloudWatchPlugin.initialize();

        LOG.info("Starting main update loop.");

        while (!stop) {
            cloudWatchPlugin.performUpdate();

            // Wait before fetching next update.
            int backendUpdateIntervalSeconds = Config.getInstance().getInt(Config.BACKEND_UPDATE_INTERVAL_SECONDS);
            LOG.debug("Sleeping {} seconds until next update.", backendUpdateIntervalSeconds);
            doSleep(1000 * backendUpdateIntervalSeconds);
        }
    }

    public void doStop() {
        stop = true;
        cloudWatchPlugin.shutdown();
    }

    private void doSleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            LOG.error("Yawn... sleep interrupted: ", e);
        }
    }

}
