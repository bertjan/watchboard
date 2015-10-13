package nl.revolution.watchboard.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginSource {

    // TODO
    // - rearrange packages
    // - add tests

    private static final Logger LOG = LoggerFactory.getLogger(PluginSource.class);

    private Thread worker;

    public void start() {
        worker = new PluginThread();
        worker.start();
    }

    public void stop() {
        if (worker != null) {
            if (worker.isAlive()) {
                ((PluginThread)worker).doStop();
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    LOG.error("Error stopping PluginSource: ", e);
                }
            }
            worker = null;
        }
    }

}
