package nl.revolution.watchboard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import nl.revolution.watchboard.plugins.cloudwatch.CloudWatchDataSource;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DashboardServer {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardServer.class);
    private static String appVersion;

    public static void main(String... args) throws Exception {
        configureLogging();

        // Set backend app version: unique id for each build.
        // (to enable auto refesh in the frontend on app restart/upgrade).
        appVersion = readBuildTimestampFromVersionProperties();
        if (appVersion == null || appVersion.endsWith("PLACEHOLDER")) {
            // Workaround for local deployments since the placeholdr is replaced in the maven build process.
            appVersion = "WatchBoard-" + System.currentTimeMillis();
        }
        LOG.debug("Starting {}.", appVersion);

        CloudWatchDataSource dataWorker = new CloudWatchDataSource();
        Server webServer = new WebServer().createServer();

        // Setup shutdown hooks.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    dataWorker.stop();
                    webServer.stop();
                } catch (Exception e) {
                    LOG.error("Stopping workers failed: ", e);
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
        logConfig.getLogger("org.eclipse.jetty.server.handler.ContextHandler").setLevel(Level.ERROR);
        logConfig.getLogger("nl.revolution.watchboard").setLevel(Level.DEBUG);
    }

    private static String readBuildTimestampFromVersionProperties() throws IOException {
        InputStream propertyStream = DashboardServer.class.getClassLoader().getResourceAsStream("version.properties");
        if (propertyStream == null) {
            return null;
        }
        Properties properties = new Properties();
        properties.load(propertyStream);
        return properties.getProperty("BUILD_TIMESTAMP");
    }

}
