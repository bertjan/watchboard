package nl.revolution.watchboard;

import nl.revolution.watchboard.data.Dashboard;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class APIHandler extends AbstractHandler {

    private static final String CONTENT_TYPE_JSON_UTF8 = "application/json;charset=utf-8";
    private static final String IMAGE_PATH = Config.getInstance().getString("temp.path");

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        final String contextRoot = Config.getInstance().getString("web.contextroot").endsWith("/") ? Config.getInstance().getString("web.contextroot") : Config.getInstance().getString("web.contextroot") + "/";
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

        new NotFoundHandler().handle(target, baseRequest, request, response);
    }


    private void createDashboardsResponse(Request baseRequest, HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE_JSON_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        JSONArray dashboards = new JSONArray();
        Config.getInstance().getDashboards().stream().forEach(dashboard -> {
            JSONObject dashObj = new JSONObject();
            dashObj.put("id", dashboard.getId());
            dashObj.put("title", dashboard.getTitle());
            dashboards.add(dashObj);
        });

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("dashboards", dashboards);

        try {
            OutputStream out = response.getOutputStream();
            out.write(jsonResponse.toJSONString().getBytes(Charsets.UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
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
        jsonResponse.put("id", dashboard.getId());
        jsonResponse.put("title", dashboard.getTitle());

        JSONArray imagesArr = new JSONArray();
        dashboard.getGraphs().stream().forEach(graph -> imagesArr.add(graph.toJSON(contextRoot)));
        jsonResponse.put("images", imagesArr);

        try {
            OutputStream out = response.getOutputStream();
            out.write(jsonResponse.toJSONString().getBytes(Charsets.UTF_8));
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
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
        IOUtils.copy(new FileInputStream(imageFile), out);
        out.flush();
        out.close();
        System.out.println("Served " + filename + ".");
    }

}



