package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchDashboardPlugin;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchPlugin;
import nl.revolution.watchboard.plugins.kibana.KibanaPlugin;
import nl.revolution.watchboard.plugins.performr.PerformrPlugin;
import nl.revolution.watchboard.plugins.sonar.SonarPlugin;
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
            if (cloudWatchPlugin != null
                    && Config.getInstance().getGraphCountForType(Graph.Type.CLOUDWATCH) > 0
                    && browserInstance.equals(cloudWatchPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new CloudWatchPlugin());
                LOG.info("CloudWatch plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin cloudWatchDashboardPlugin = Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH_DASHBOARD);
            if (cloudWatchDashboardPlugin != null
                    && Config.getInstance().getGraphCountForType(Graph.Type.CLOUDWATCH_DASHBOARD) > 0
                    && browserInstance.equals(cloudWatchDashboardPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new CloudWatchDashboardPlugin());
                LOG.info("CloudWatch dashboard plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin performrPlugin = Config.getInstance().getPlugin(Graph.Type.PERFORMR);
            if (performrPlugin != null
                    && Config.getInstance().getGraphCountForType(Graph.Type.PERFORMR) > 0
                    && browserInstance.equals(performrPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new PerformrPlugin());
                LOG.info("Performr plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin kibanaPlugin = Config.getInstance().getPlugin(Graph.Type.KIBANA);
            if (kibanaPlugin != null
                    && Config.getInstance().getGraphCountForType(Graph.Type.KIBANA) > 0
                    && browserInstance.equals(kibanaPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new KibanaPlugin(Graph.Type.KIBANA));
                LOG.info("Kibana plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin kibana5Plugin = Config.getInstance().getPlugin(Graph.Type.KIBANA5);
            if (kibana5Plugin != null
                    && Config.getInstance().getGraphCountForType(Graph.Type.KIBANA5) > 0
                    && browserInstance.equals(kibana5Plugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new KibanaPlugin(Graph.Type.KIBANA5));
                LOG.info("Kibana5 plugin configured for browser instance '" + browserInstance + "'.");
            }

            Plugin sonarPlugin = Config.getInstance().getPlugin(Graph.Type.SONAR);
            if (sonarPlugin != null
                    && Config.getInstance().getGraphCountForType(Graph.Type.SONAR) > 0
                    && browserInstance.equals(sonarPlugin.getBrowserInstance())) {
                pluginsForBrowserInstance.add(new SonarPlugin());
                LOG.info("Sonar plugin configured for browser instance '" + browserInstance + "'.");
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
