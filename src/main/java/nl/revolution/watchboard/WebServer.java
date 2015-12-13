package nl.revolution.watchboard;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlets.gzip.GzipHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServer {

    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);

    public Server createServer() {
        int httpPort = Config.getInstance().getInt(Config.HTTP_PORT);
        String webDir = this.getClass().getClassLoader().getResource("web").toExternalForm();
        // String webDir = "/Users/bertjan/IdeaProjects/sandbox/watchboard/src/main/resources/web";
        HandlerList webHandlers = new HandlerList();

        // Resource handler for initial request (index.html).
        ResourceHandler indexResource = new ResourceHandler();
        indexResource.setDirectoriesListed(false);
        indexResource.setResourceBase(webDir);
        indexResource.setWelcomeFiles(new String[]{"index.html"});
        ContextHandler indexContextHandler = new ContextHandler(Config.getInstance().getString(Config.WEB_CONTEXTROOT));
        indexContextHandler.setHandler(indexResource);
        webHandlers.addHandler(indexContextHandler);

        // Resource handler for config screen (config.html).
        ResourceHandler configResource = new ResourceHandler();
        configResource.setDirectoriesListed(false);
        configResource.setResourceBase(webDir);
        configResource.setWelcomeFiles(new String[]{"config.html"});
        ContextHandler configContextHandler;
        configContextHandler = new ContextHandler(Config.getInstance().getContextRoot() + "config");
        configContextHandler.setHandler(configResource);
        webHandlers.addHandler(configContextHandler);

        // Dashboard-specific context/resource handler for second request (dashboard.html).
        Config.getInstance().getDashboardIds().stream().forEach(dashboardId -> {
            ResourceHandler dashboardResource = new ResourceHandler();
            dashboardResource.setDirectoriesListed(false);
            dashboardResource.setResourceBase(webDir);
            dashboardResource.setWelcomeFiles(new String[]{"dashboard.html"});
            ContextHandler dashboardContextHandler;
            dashboardContextHandler = new ContextHandler(Config.getInstance().getContextRoot() + dashboardId);
            dashboardContextHandler.setHandler(dashboardResource);
            webHandlers.addHandler(dashboardContextHandler);
        });

        webHandlers.addHandler(new APIHandler());
        webHandlers.addHandler(new NotFoundHandler());
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(webHandlers);

        Server webServer = new Server(httpPort);
        webServer.setHandler(gzipHandler);

        LOG.info("Webserver created, listening on port {}", httpPort);
        return webServer;
    }

}
