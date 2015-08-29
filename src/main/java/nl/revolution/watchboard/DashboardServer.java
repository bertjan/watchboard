package nl.revolution.watchboard;

import org.eclipse.jetty.server.Server;

public class DashboardServer {

    public static void main(String... args) throws Exception {
        CloudWatchDataWorker dataWorker = new CloudWatchDataWorker();
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
    }
}
