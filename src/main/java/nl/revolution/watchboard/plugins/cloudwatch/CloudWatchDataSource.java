package nl.revolution.watchboard.plugins.cloudwatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudWatchDataSource {

    // TODO
    // - rearrange packages
    // - add tests

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchDataSource.class);

    private Thread worker;

    public void start() {
        worker = new CloudWatchDataWorker();
        worker.start();
    }

    public void stop() {
        if (worker != null) {
            if (worker.isAlive()) {
                ((CloudWatchDataWorker)worker).doStop();
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    LOG.error("Error stopping CloudWatchDataWorker: ", e);
                }
            }
            worker = null;
        }
    }

}
