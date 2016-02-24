package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.data.Graph;
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

        if (Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH) != null) {
            LOG.info("CloudWatch plugin configured, starting update thread.");
            workers.add(new PluginUpdateThread(new CloudWatchPlugin()));
        }

        if (Config.getInstance().getPlugin(Graph.Type.CLOUDWATCH_DASHBOARD) != null) {
            LOG.info("CloudWatchDashboard plugin configured, starting update thread.");
            workers.add(new PluginUpdateThread(new CloudWatchDashboardPlugin()));
        }

        if (Config.getInstance().getPlugin(Graph.Type.PERFORMR) != null) {
            LOG.info("Performr plugin configured, starting update thread.");
            workers.add(new PluginUpdateThread(new PerformrPlugin()));
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
