package nl.revolution.watchboard;

import nl.revolution.watchboard.data.Dashboard;
import nl.revolution.watchboard.data.Graph;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Config {

    private static Config instance;
    private JSONObject config;
    private List<Dashboard> dashboards;

    public static Config getInstance() {
        if (instance == null) {
            synchronized(Config.class) {
                if (instance == null) {
                    try {
                        instance = new Config();
                    } catch (IOException | ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return instance;
    }

    public Config() throws IOException, ParseException {
        readConfigFromDisk();
        parseDashboards();
    }

    private void readConfigFromDisk() throws IOException, ParseException {
        String configFile = getCurrentPath() + "/config.json";
        System.out.println("Using config file: " + configFile);
        String configStr = FileUtils.readFileToString(new File(configFile));
        config = (JSONObject) new JSONParser().parse(new StringReader(configStr));
    }

    private void parseDashboards() {
        dashboards = new ArrayList<>();
        JSONArray dashArr = (JSONArray)config.get("dashboards");
        for (int dashIndex = 0; dashIndex < dashArr.size(); dashIndex++) {
            JSONObject dashObj = (JSONObject) dashArr.get(dashIndex);
            Dashboard dashboard = new Dashboard();
            dashboard.setId(readString(dashObj, "id"));
            dashboard.setTitle(readString(dashObj, "title"));

            JSONArray graphsJa = (JSONArray)dashObj.get("graphs");
            for (int graphIndex = 0; graphIndex < graphsJa.size(); graphIndex++) {
                JSONObject graphObj = (JSONObject) graphsJa.get(graphIndex);
                Graph graph = new Graph();
                graph.setUrl(readString(graphObj, "url"));
                graph.setId(readString(graphObj, "id"));
                graph.setBrowserWidth(readInt(graphObj, "browserWidth"));
                graph.setBrowserHeight(readInt(graphObj, "browserHeight"));
                graph.setImageHeight(readInt(graphObj, "imageHeight"));
                graph.setImagePath(getString("temp.path") + "/" + readString(graphObj, "id").toString() + ".png");
                dashboard.getGraphs().add(graph);
            }
            dashboards.add(dashboard);
        }
    }

    private String getCurrentPath() {
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().toString().replaceAll("file:", "");
        return path.substring(0, path.lastIndexOf("/"));
    }

    public String getString(String key) {
        return readString(config, key);
    }

    public int getInt(String key) {
        return readInt(config, key);
    }

    private String readString(JSONObject jsonObject, String key) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private int readInt(JSONObject jsonObject, String key) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return -1;
        }
        return Integer.valueOf(value.toString()).intValue();
    }

    public List<Dashboard> getDashboards() {
        return dashboards;
    }

    public List<String> getDashboardIds() {
        return Config.getInstance().getDashboards().stream().map(dash -> dash.getId()).collect(Collectors.toList());
    }
}
