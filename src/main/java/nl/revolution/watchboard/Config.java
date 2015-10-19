package nl.revolution.watchboard;

import nl.revolution.watchboard.data.Dashboard;
import nl.revolution.watchboard.data.Graph;
import nl.revolution.watchboard.data.Plugin;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Config {

    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String PLUGINS = "plugins";
    public static final String DASHBOARDS = "dashboards";
    public static final String GRAPHS = "graphs";
    public static final String TYPE = "type";
    public static final String URL = "url";
    public static final String COMPONENTS = "components";
    public static final String BROWSER_WIDTH = "browserWidth";
    public static final String BROWSER_HEIGHT = "browserHeight";
    public static final String TEMP_PATH = "temp.path";
    public static final String WEB_CONTEXTROOT = "web.contextroot";
    public static final String HTTP_PORT = "httpPort";
    public static final String BACKEND_UPDATE_INTERVAL_SECONDS = "backendUpdateIntervalSeconds";
    public static final String MAX_SESSION_DURATION_MINUTES = "maxSessionDurationMinutes";
    public static final String DEFAULT_NUMBER_OF_COLUMNS = "defaultNumberOfColumns";

    private static final List<String> REQUIRED_CONFIG_KEYS_GLOBAL = Arrays.asList(HTTP_PORT, WEB_CONTEXTROOT,
            TEMP_PATH, MAX_SESSION_DURATION_MINUTES, DASHBOARDS, PLUGINS);
    private static final List<String> REQUIRED_CONFIG_KEYS_DASHBOARD = Arrays.asList(ID, TITLE, GRAPHS);
    private static final List<String> REQUIRED_CONFIG_KEYS_GRAPH = Arrays.asList(TYPE, ID, BROWSER_WIDTH, BROWSER_HEIGHT);

    private static final String EXTENSION_PNG = ".png";
    public static final String LOGIN_URL = "login.url";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private static Config instance;
    private JSONObject config;
    private List<Dashboard> dashboards;
    private List<Plugin> plugins;

    private long configFileLastModified;

    public static Config getInstance() {
        if (instance == null) {
            synchronized(Config.class) {
                if (instance == null) {
                    try {
                        instance = new Config();
                    } catch (IOException | ParseException e) {
                        LOG.error("Initializing config failed: ", e);
                    }
                }
            }
        }
        return instance;
    }

    public Config() throws IOException, ParseException {
        intializeConfig();
    }

    private void intializeConfig() throws IOException, ParseException {
        readConfigFromDisk();
        checkConfig();
        parsePlugins();
        parseDashboards();
        configFileLastModified = getConfigFile().lastModified();

        LOG.info("Config initialized. Configured {} dashboards with a total of {} graphs.",
                dashboards.size(), dashboards.stream().map(Dashboard::getGraphs).flatMap(Collection::stream).count());
    }

    public void checkForConfigUpdate() {
        LOG.info("Checking for updated config file on disk.");
        File configFile = getConfigFile();
        long lastModifiedOnDisk = configFile.lastModified();

        if (lastModifiedOnDisk != configFileLastModified) {
            LOG.info("Newer config file exists on disk, reloading.");
            try {
                intializeConfig();
            } catch (IOException | ParseException ex) {
                LOG.error("Error while reloading config file from disk: ", ex);
            }
        }
    }

    private void readConfigFromDisk() throws IOException, ParseException {
        File configFile = getConfigFile();
        LOG.info("Using config file: {}", configFile.getAbsolutePath());
        String configStr = FileUtils.readFileToString(configFile);
        config = (JSONObject) new JSONParser().parse(new StringReader(configStr));
    }

    private File getConfigFile() {
        String configFilePath = getCurrentPath() + "/config.json";
        return new File(configFilePath);
    }

    private void checkConfig() {
        // Check global config.
        REQUIRED_CONFIG_KEYS_GLOBAL.stream().forEach(requiredKey -> {
            if (!config.containsKey(requiredKey)) {
                throw new RuntimeException("Required config key '" + requiredKey + "' is missing.");
            }
        });

        // Check dashboards config.
        JSONArray dashboards = (JSONArray)config.get("dashboards");
        dashboards.stream().forEach(dashboard -> {
            REQUIRED_CONFIG_KEYS_DASHBOARD.stream().forEach(requiredKey -> {
                if (!((JSONObject) dashboard).containsKey(requiredKey)) {
                    throw new RuntimeException("Required config key '" + requiredKey +
                            "' is missing for dashboard '" + ((JSONObject) dashboard).get("id") + "'.");
                }

                // Check graphs config.
                JSONArray graphs = (JSONArray) ((JSONObject) dashboard).get("graphs");
                graphs.stream().forEach(graph -> {
                    REQUIRED_CONFIG_KEYS_GRAPH.stream().forEach(requiredGraphKey -> {
                        if (!((JSONObject) graph).containsKey(requiredGraphKey)) {
                            // Graph type 'disk' has almost no requirements.
                            if (!"disk".equals(((JSONObject) graph).get("type"))) {
                                throw new RuntimeException("Required config key '" + requiredGraphKey +
                                        "' is missing for dashboard '" + ((JSONObject) dashboard).get("id") +
                                        "', graph '" + ((JSONObject) graph).get("id") + "'.");
                            }
                        }
                    });
                });
            });
        });

        // TODO: check plugin config
    }

    private void parseDashboards() {
        dashboards = new ArrayList<>();
        JSONArray dashArr = (JSONArray)config.get(DASHBOARDS);
        for (int dashIndex = 0; dashIndex < dashArr.size(); dashIndex++) {
            JSONObject dashObj = (JSONObject) dashArr.get(dashIndex);
            Dashboard dashboard = new Dashboard();
            dashboard.setId(readString(dashObj, ID));
            dashboard.setTitle(readString(dashObj, TITLE));
            dashboard.setDefaultNumberOfColumns(readInteger(dashObj, DEFAULT_NUMBER_OF_COLUMNS));

            JSONArray graphsJa = (JSONArray)dashObj.get(GRAPHS);
            for (int graphIndex = 0; graphIndex < graphsJa.size(); graphIndex++) {
                JSONObject graphObj = (JSONObject) graphsJa.get(graphIndex);
                Graph graph = new Graph();

                String typeStr = readString(graphObj, TYPE);
                Graph.Type graphType = Graph.Type.fromString(typeStr);
                graph.setType(graphType);

                String url = readString(graphObj, URL);
                if (Graph.Type.PERFORMR.equals(graph.getType()) && StringUtils.isEmpty(url)) {
                    url = getPlugin(Graph.Type.PERFORMR).getLoginUrl();
                }
                graph.setUrl(url);
                graph.setId(readString(graphObj, ID));

                graph.setBrowserWidth(readInt(graphObj, BROWSER_WIDTH));
                graph.setBrowserHeight(readInt(graphObj, BROWSER_HEIGHT));
                graph.setImagePath(getString(TEMP_PATH) + "/" + readString(graphObj, ID).toString() + EXTENSION_PNG);

                Object componentsObj = graphObj.get(COMPONENTS);
                if (componentsObj != null) {
                    graph.setComponents((List) componentsObj);
                }
                dashboard.getGraphs().add(graph);
            }
            dashboards.add(dashboard);
        }

        // Postprocess step: try to find a matching URL for each graph of type 'disk'.
        dashboards.forEach(dashboard -> dashboard.getGraphs().forEach(graphWithDiskSource -> {
            Optional<Graph> graphWithMatchingId =
                    dashboards.stream()
                            .map(d -> d.getGraphs())
                            .flatMap(g -> g.stream())
                            .filter(graph -> graph.getId().equals(graphWithDiskSource.getId()))
                            .findFirst();
            if (graphWithMatchingId.isPresent()) {
                graphWithDiskSource.setUrl(graphWithMatchingId.get().getUrl());
            }
        }));
    }

    private void parsePlugins() {
        plugins = new ArrayList<>();
        JSONArray pluginArr = (JSONArray)config.get(PLUGINS);
        pluginArr.forEach(pluginObj -> {
            JSONObject pluginJo = (JSONObject)pluginObj;
            Plugin plugin = new Plugin();
            String typeStr = readString(pluginJo, TYPE);
            plugin.setType(Graph.Type.fromString(typeStr));
            plugin.setLoginUrl(readString(pluginJo, LOGIN_URL));
            plugin.setUsername(readString(pluginJo, USERNAME));
            plugin.setPassword(readString(pluginJo, PASSWORD));
            plugin.setUpdateIntervalSeconds(readInt(pluginJo, BACKEND_UPDATE_INTERVAL_SECONDS));
            plugins.add(plugin);
        });
    }

    public Plugin getPlugin(Graph.Type type) {
        for (Plugin plugin : plugins) {
            if (type.equals(plugin.getType())) {
                return plugin;
            }
        }
        return null;
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

    private Integer readInteger(JSONObject jsonObject, String key) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value.toString());
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

    public long getTSLastUpdate() {
        return configFileLastModified;
    }

}
