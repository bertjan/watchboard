package nl.revolution.watchboard;

import nl.revolution.watchboard.data.Dashboard;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Optional;

public class APIHandler extends AbstractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(APIHandler.class);

    private static final String CONTENT_TYPE_JSON_UTF8 = "application/json;charset=utf-8";
    private static final String IMAGE_PATH = Config.getInstance().getString(Config.TEMP_PATH);
    private static final String LOADING_ICON_PATH = "/web/loading.gif";
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        final String contextRoot = Config.getInstance().getContextRoot() + "api/v1/";
        final String requestURI = request.getRequestURI();

        if (requestURI.equals(contextRoot + "dashboards")) {
            createDashboardsResponse(baseRequest, response);
            return;
        }

        // TODO: remove
        // Temporary support old context root for seamless migration to new api paths.
        final String oldContextRoot = Config.getInstance().getContextRoot();
        if (requestURI.startsWith(oldContextRoot + "status/")) {
            System.out.println("fallback");
            createStatusResponse(target, baseRequest, request, response, oldContextRoot);
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

        if (requestURI.startsWith(contextRoot + "healthcheck")) {
            createHealthCheckResponse(baseRequest, response);
            return;
        }

        LOG.info("Could not match a request for requestURI {}, responding with 404.", requestURI);
        new NotFoundHandler().handle(target, baseRequest, request, response);
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

    private void createStatusResponse(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response, String contextRoot) throws IOException, ServletException {

        String requestedDashboardId = baseRequest.getRequestURI().replaceAll(contextRoot + "status/", "");
        Optional<Dashboard> dashboardOpt = Config.getInstance().getDashboards().stream().filter(board -> board.getId().equals(requestedDashboardId)).findFirst();
        if (!dashboardOpt.isPresent()) {
            new NotFoundHandler().handle(target, baseRequest, request, response);
            return;
        }

        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        Dashboard dashboard = dashboardOpt.get();

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("appVersion", DashboardServer.getAppVersion());
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


}



