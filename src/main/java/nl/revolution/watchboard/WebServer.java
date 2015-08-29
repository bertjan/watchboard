package nl.revolution.watchboard;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlets.gzip.GzipHandler;

public class WebServer {

    public Server createServer() {
        int httpPort = Config.getInstance().getInt("httpPort");
        String contextRoot = Config.getInstance().getString("web.contextroot");
        String webDir = this.getClass().getClassLoader().getResource("web").toExternalForm();
        HandlerList webHandlers = new HandlerList();

        // Resource handler for initial request (index.html).
        ResourceHandler indexResource = new ResourceHandler();
        indexResource.setDirectoriesListed(false);
        indexResource.setResourceBase(webDir);
        indexResource.setWelcomeFiles(new String[]{"index.html"});
        ContextHandler indexContextHandler = new ContextHandler(contextRoot);
        indexContextHandler.setHandler(indexResource);
        webHandlers.addHandler(indexContextHandler);

        // Dashboard-specific context/resource handler for second request (dashboard.html).
        Config.getInstance().getDashboardIds().stream().forEach(dashboardId -> {
            ResourceHandler dashboardesource = new ResourceHandler();
            dashboardesource.setDirectoriesListed(false);
            dashboardesource.setResourceBase(webDir);
            dashboardesource.setWelcomeFiles(new String[]{"dashboard.html"});
            ContextHandler dashboardContextHandler;
            if (contextRoot.endsWith("/")) {
                dashboardContextHandler = new ContextHandler(contextRoot + dashboardId);
            } else {
                dashboardContextHandler = new ContextHandler(contextRoot + "/" + dashboardId);
            }
            dashboardContextHandler.setHandler(dashboardesource);
            webHandlers.addHandler(dashboardContextHandler);
        });

        webHandlers.addHandler(new APIHandler());
        webHandlers.addHandler(new NotFoundHandler());
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(webHandlers);

        Server webServer = new Server(httpPort);
        webServer.setHandler(gzipHandler);

        System.out.println("Webserver created, listening on port " + httpPort);
        return webServer;
    }

}
