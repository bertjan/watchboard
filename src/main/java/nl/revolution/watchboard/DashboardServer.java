package nl.revolution.watchboard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardServer {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardServer.class);
    private static String appVersion;

    public static void main(String... args) throws Exception {
        configureLogging();

        // Set backend app version: unique id for each time the app starts
        // (to enable auto refesh in the frontend on app restart/upgrade).
        appVersion = "WatchBoard-" + System.currentTimeMillis();
        CloudWatchDataSource dataWorker = new CloudWatchDataSource();
        Server webServer = new WebServer().createServer();

        // Setup shutdown hooks.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    dataWorker.stop();
                    webServer.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        webServer.start();
        dataWorker.start();

        LOG.info("DashboardServer running. Press enter to quit.");
        System.in.read();

        webServer.stop();
        dataWorker.stop();
    }

    public static String getAppVersion() {
        return appVersion;
    }

    private static void configureLogging() {
        LoggerContext logConfig = (LoggerContext) LoggerFactory.getILoggerFactory();
        logConfig.getLogger("ROOT").setLevel(Level.INFO);
        logConfig.getLogger("org.eclipse.jetty").setLevel(Level.INFO);
        logConfig.getLogger("nl.revolution.watchboard").setLevel(Level.DEBUG);
    }

}
