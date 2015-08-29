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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Config {

    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String DASHBOARDS = "dashboards";
    public static final String GRAPHS = "graphs";
    public static final String URL = "url";
    public static final String BROWSER_WIDTH = "browserWidth";
    public static final String BROWSER_HEIGHT = "browserHeight";
    public static final String IMAGE_HEIGHT = "imageHeight";
    public static final String TEMP_PATH = "temp.path";
    public static final String WEB_CONTEXTROOT = "web.contextroot";
    public static final String HTTP_PORT = "httpPort";
    public static final String AWS_USERNAME = "aws.username";
    public static final String AWS_PASSWORD = "aws.password";
    public static final String BACKEND_UPDATE_INTERVAL_SECONDS = "backendUpdateIntervalSeconds";
    public static final String AWS_SIGNIN_URL = "aws.signin.url";

    private static final List<String> REQUIRED_CONFIG_KEYS = Arrays.asList(HTTP_PORT, WEB_CONTEXTROOT, AWS_USERNAME,
            AWS_PASSWORD, AWS_SIGNIN_URL, TEMP_PATH, BACKEND_UPDATE_INTERVAL_SECONDS, DASHBOARDS);
    private static final String EXTENSION_PNG = ".png";

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
        checkConfig();
        parseDashboards();

    }

    private void readConfigFromDisk() throws IOException, ParseException {
        String configFile = getCurrentPath() + "/config.json";
        System.out.println("Using config file: " + configFile);
        String configStr = FileUtils.readFileToString(new File(configFile));
        config = (JSONObject) new JSONParser().parse(new StringReader(configStr));
    }

    private void checkConfig() {
        REQUIRED_CONFIG_KEYS.stream().forEach(requiredKey -> {
            if (!config.containsKey(requiredKey)) {
                throw new RuntimeException("Required config key '" + requiredKey + "' is missing.");
            }
        });
    }

    private void parseDashboards() {
        dashboards = new ArrayList<>();
        JSONArray dashArr = (JSONArray)config.get(DASHBOARDS);
        for (int dashIndex = 0; dashIndex < dashArr.size(); dashIndex++) {
            JSONObject dashObj = (JSONObject) dashArr.get(dashIndex);
            Dashboard dashboard = new Dashboard();
            dashboard.setId(readString(dashObj, ID));
            dashboard.setTitle(readString(dashObj, TITLE));

            JSONArray graphsJa = (JSONArray)dashObj.get(GRAPHS);
            for (int graphIndex = 0; graphIndex < graphsJa.size(); graphIndex++) {
                JSONObject graphObj = (JSONObject) graphsJa.get(graphIndex);
                Graph graph = new Graph();
                graph.setUrl(readString(graphObj, URL));
                graph.setId(readString(graphObj, ID));
                graph.setBrowserWidth(readInt(graphObj, BROWSER_WIDTH));
                graph.setBrowserHeight(readInt(graphObj, BROWSER_HEIGHT));
                graph.setImageHeight(readInt(graphObj, IMAGE_HEIGHT));
                graph.setImagePath(getString(TEMP_PATH) + "/" + readString(graphObj, ID).toString() + EXTENSION_PNG);
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

    public String getContextRoot() {
        String contextRoot = getString(Config.WEB_CONTEXTROOT);
        if (contextRoot.endsWith("/")) {
            return contextRoot;
        }
        return contextRoot + "/";
    }

}
