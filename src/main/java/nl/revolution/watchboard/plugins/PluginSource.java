package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchDashboardPlugin;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchPlugin;
import nl.revolution.watchboard.plugins.performr.PerformrPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PluginSource {

    private static final Logger LOG = LoggerFactory.getLogger(PluginSource.class);

    private List<Thread> workers;

    public void start() {
        workers = new ArrayList<>();

        List<String> browserInstances = Config.getInstance().getBrowserInstances();

        for (String browserInstance : browserInstances) {
            List<WatchboardPlugin> pluginsForBrowserInstance = new ArrayList<>();

            Plugin cloudWatchPlugin = Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH);
            if (cloudWatchPlugin != null && browserInstance.equals(cloudWatchPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new CloudWatchPlugin());
                LOG.info("CloudWatch plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin cloudWatchDashboardPlugin = Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH_DASHBOARD);
            if (cloudWatchDashboardPlugin != null && browserInstance.equals(cloudWatchDashboardPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new CloudWatchDashboardPlugin());
                LOG.info("CloudWatch dashboard plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin performrPlugin = Config.getInstance().getPlugin(Graph.Type.PERFORMR);
            if (performrPlugin != null && browserInstance.equals(performrPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new PerformrPlugin());
                LOG.info("Performr plugin configured for browser instance '" + browserInstance + "'.");
            }

            if (!pluginsForBrowserInstance.isEmpty()) {
                LOG.info("Starting browser instance '" + browserInstance + "' for plugins " + pluginsForBrowserInstance + ".");
                workers.add(new PluginUpdateThread(browserInstance, pluginsForBrowserInstance));
            } else {
                LOG.warn("No plugins configured for browser instance '" + browserInstance + "', skipping initialization.");
            }
        }

        workers.stream().forEach(Thread::start);
    }

    public void stop() {
        workers.stream().forEach(this::stopWorker);
    }

    private void stopWorker(Thread worker) {
        if (worker != null) {
            if (worker.isAlive()) {
                ((PluginUpdateThread)worker).doStop();
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    LOG.error("Error stopping PluginSource: ", e);
                }
            }
        }
    }

}
