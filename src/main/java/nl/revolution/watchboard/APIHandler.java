package nl.revolution.watchboard;

import nl.revolution.watchboard.data.Dashboard;
import nl.revolution.watchboard.utils.IpAddressUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class APIHandler extends AbstractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(APIHandler.class);

    private static final String CONTENT_TYPE_JSON_UTF8 = "application/json;charset=utf-8";
    private static final String IMAGE_PATH = Config.getInstance().getString(Config.TEMP_PATH);
    private static final String LOADING_ICON_PATH = "/web/loading.gif";
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
    private static final int USER_STATS_LOG_INTERVAL_MINUTES = 5;

    // Caches.
    private static String dashboardHTML;
    private static Map<String, ContextHandler> resourceHandlerCache = new HashMap<>();

    private Set<String> userStats = new HashSet<>();
    private long tsLastLoggedUserStats = 0;

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        final String contextRoot = Config.getInstance().getContextRoot() + "api/v1/";
        final String requestURI = request.getRequestURI();

        if (requestURI.equals(contextRoot + "dashboards")) {
            createDashboardsResponse(baseRequest, response);
            return;
        }

        if (requestURI.startsWith(contextRoot + "status/")) {
            createStatusResponse(target, baseRequest, request, response, contextRoot);
            return;
        }

        if (requestURI.startsWith(contextRoot + "images/")) {
            createImageResponse(baseRequest, request, response, contextRoot, target);
            return;
        }

        if (requestURI.startsWith(contextRoot + "config")) {
            if ("POST".equals(request.getMethod())) {
                handlePOSTConfigRequest(baseRequest, request, response);
            } else {
                createGETConfigResponse(baseRequest, response, null);
            }
            return;
        }

        if (requestURI.startsWith(contextRoot + "healthcheck")) {
            createHealthCheckResponse(baseRequest, response);
            return;
        }

        // Serve dashboard.html for all configured dashboards.
        for (String dashboardId : Config.getInstance().getDashboardIds()) {
            if (requestURI.startsWith(Config.getInstance().getContextRoot() + dashboardId)) {
                getResourceHandlerForDashboard(dashboardId).handle(target, baseRequest, request, response);
                baseRequest.setHandled(true);
                return;
            }
        }

        LOG.info("Could not match a request for requestURI {}, responding with 404.", requestURI);
        new NotFoundHandler().handle(target, baseRequest, request, response);
    }

    private ContextHandler getResourceHandlerForDashboard(String dashboardId) {
        // Fetch resource handler from cache.
        if (resourceHandlerCache.get(dashboardId) == null) {
            LOG.debug("Creating dashboard resource handler for dashboard '" + dashboardId + "'.");
            ResourceHandler dashboardResource = new ResourceHandler();
            dashboardResource.setDirectoriesListed(false);
            dashboardResource.setResourceBase(WebServer.STATIC_RESOURCE_PATH);
            dashboardResource.setWelcomeFiles(new String[]{"dashboard.html"});
            ContextHandler dashboardContextHandler;
            dashboardContextHandler = new ContextHandler(Config.getInstance().getContextRoot() + dashboardId);
            dashboardContextHandler.setHandler(dashboardResource);
            try {
                dashboardContextHandler.start();
            } catch (Exception e) {
                LOG.error("Error starting context handler for dashboard '" + dashboardId + "': ", e);
            }
            resourceHandlerCache.put(dashboardId, dashboardContextHandler);
        }

        return resourceHandlerCache.get(dashboardId);
    }

    private void createDashboardsResponse(Request baseRequest, HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        JSONArray dashboards = new JSONArray();
        Config.getInstance().getDashboards().stream().forEach(dashboard -> {
            JSONObject dashObj = new JSONObject();
            dashObj.put(Config.ID, dashboard.getId());
            dashObj.put(Config.TITLE, dashboard.getTitle());
            if (dashboard.getDefaultNumberOfColumns() != null) {
                dashObj.put(Config.DEFAULT_NUMBER_OF_COLUMNS, dashboard.getDefaultNumberOfColumns());
            }
            dashboards.add(dashObj);
        });

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put(Config.DASHBOARDS, dashboards);

        try {
            OutputStream out = response.getOutputStream();
            out.write(jsonResponse.toJSONString().getBytes(CHARSET_UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            LOG.error("Error while creating dashboards response: ", e);
        }
    }


    private void createDashboardHTMLResponse(Request baseRequest, HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        try {
            response.setContentType("text/html");
            OutputStream out = response.getOutputStream();
            out.write(getDashboardHTML().getBytes(CHARSET_UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            LOG.error("Error while creating dashboard HTML response: ", e);
        }
    }

    private String getDashboardHTML() {
        if (dashboardHTML == null) {
            try {
                InputStream input = this.getClass().getClassLoader().getResourceAsStream("web/dashboard.html");
                dashboardHTML = readFully(new InputStreamReader(input));
            } catch (IOException e) {
                LOG.error("Error while reading dashboard HTML: ", e);
            }
        }
        return dashboardHTML;
    }

    private void createStatusResponse(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response, String contextRoot) throws IOException, ServletException {

        String requestedDashboardId = baseRequest.getRequestURI().replaceAll(contextRoot + "status/", "");
        Optional<Dashboard> dashboardOpt = Config.getInstance().getDashboards().stream().filter(board -> board.getId().equals(requestedDashboardId)).findFirst();
        if (!dashboardOpt.isPresent()) {
            new NotFoundHandler().handle(target, baseRequest, request, response);
            return;
        }

        long tsNow = System.currentTimeMillis();
        if (tsNow - tsLastLoggedUserStats > USER_STATS_LOG_INTERVAL_MINUTES*60*1000) {
            synchronized (userStats) {
                LOG.info("Estimated number of users accessing graphs in the past " + USER_STATS_LOG_INTERVAL_MINUTES + " minutes: " + userStats.size());
                LOG.debug("User stats: \n" + StringUtils.join(userStats, "\n"));
                userStats.clear();
                tsLastLoggedUserStats = tsNow;
            }
        }

        userStats.add(determineRemoteUserFingerPrint(request));

        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        Dashboard dashboard = dashboardOpt.get();

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("appVersion", DashboardServer.getAppVersion());
        jsonResponse.put("configLastUpdated", Config.getInstance().getTSLastUpdate());

        jsonResponse.put("id", dashboard.getId());
        jsonResponse.put("title", dashboard.getTitle());

        JSONArray imagesArr = new JSONArray();
        dashboard.getGraphs().stream().forEach(graph -> imagesArr.add(graph.toJSON(contextRoot)));
        jsonResponse.put("images", imagesArr);

        try {
            OutputStream out = response.getOutputStream();
            out.write(jsonResponse.toJSONString().getBytes(CHARSET_UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            LOG.error("Error while creating status response: ", e);
        }
    }


    private void createImageResponse(Request baseRequest, HttpServletRequest request, HttpServletResponse response,
                                     String contextRoot, String target) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        String filename = request.getRequestURI().replaceAll(contextRoot + "images/", "");

        if (filename.contains("/") || filename.contains("..")) {
            new NotFoundHandler().handle(target, baseRequest, request, response);
            return;
        }

        File imageFile = new File(IMAGE_PATH + "/" + filename);
        OutputStream out = response.getOutputStream();
        try {
            IOUtils.copy(new FileInputStream(imageFile), out);
        } catch (IOException e) {
            LOG.error("Could not serve image file: {}. Serving loading icon.", imageFile);
            IOUtils.copy(getClass().getResourceAsStream(LOADING_ICON_PATH), out);
        }
        out.flush();
        out.close();
        LOG.info("Served " + filename + ".");
    }



    private void createGETConfigResponse(Request baseRequest, HttpServletResponse response, String message) throws IOException, ServletException {
        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("config", Config.getInstance().getDashboardsConfig());
        jsonResponse.put("persistenceType", Config.getInstance().getString(Config.DASHBOARD_CONFIG_PERSISTENCE_TYPE));
        if (message != null) {
            jsonResponse.put("message", message);
        }

        try {
            OutputStream out = response.getOutputStream();
            out.write(jsonResponse.toJSONString().getBytes(CHARSET_UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            LOG.error("Error while creating config response: ", e);
        }
    }

    private void handlePOSTConfigRequest(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String postData = readFully(request.getReader());
        JSONObject postDataJo;
        try {
            postDataJo = (JSONObject) new JSONParser().parse(postData);
        } catch (ParseException e) {
            LOG.error("Error while parsing POST data");
            createGETConfigResponse(baseRequest, response, "Error while saving dashboard configuration.");
            return;
        }
        String dashboardConfig = ((JSONObject)postDataJo.get("config")).toJSONString();
        Config.getInstance().updateDashboardsConfig(dashboardConfig);

        createGETConfigResponse(baseRequest, response, "Dashboard configuration saved.");
    }

    private void createHealthCheckResponse(Request baseRequest, HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("status", "all is well.");

        try {
            OutputStream out = response.getOutputStream();
            out.write(jsonResponse.toJSONString().getBytes(CHARSET_UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            LOG.error("Error while creating healthcheck response: ", e);
        }
    }

    private String determineRemoteUserFingerPrint(HttpServletRequest request) {
        try {
            return IpAddressUtil.getClientIp(request) + "_" + request.getHeader("User-Agent") + "_" + request.getHeader("Accept-Language");
        } catch (Exception e) {
            LOG.error("Error while determining remote user fingerprint: ", e);
            return null;
        }
    }

    private String readFully(Reader reader) throws IOException {
        char[] arr = new char[8*1024]; // 8K at a time
        StringBuffer buf = new StringBuffer();
        int numChars;
        while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
            buf.append(arr, 0, numChars);
        }
        return buf.toString();
    }

}



