package nl.revolution.watchboard;

import nl.revolution.watchboard.data.*;
import nl.revolution.watchboard.persistence.DashboardConfig;
import nl.revolution.watchboard.persistence.disk.DiskConfigStore;
import nl.revolution.watchboard.persistence.dynamodb.DynamoDBConfigStore;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static java.util.stream.Collectors.toList;

public class Config {

    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    public static final String PLUGINS = "plugins";
    public static final String TEMP_PATH = "temp.path";
    public static final String WEB_CONTEXTROOT = "web.contextroot";
    public static final String HTTP_PORT = "httpPort";
    public static final String TYPE = "type";
    public static final String DASHBOARDS = "dashboards";
    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String DEFAULT_NUMBER_OF_COLUMNS = "defaultNumberOfColumns";
    public static final String BACKEND_UPDATE_INTERVAL_SECONDS = "backendUpdateIntervalSeconds";
    public static final String MAX_SESSION_DURATION_MINUTES = "maxSessionDurationMinutes";
    public static final String AWS_REGION = "aws.region";
    public static final String AWS_ACCESS_KEY_ID = "aws.accessKeyId";
    public static final String AWS_SECRET_KEY_ID = "aws.secretKeyId";
    public static final String AWS_DYNAMODB_TABLE_NAME = "aws.dynamoDB.tableName";
    public static final String DASHBOARD_CONFIG_PERSISTENCE_TYPE = "dashboard.config.persistence.type";
    public static final String BROWSER_INSTANCES = "browserInstances";
    public static final String BROWSER_INSTANCE = "browserInstance";

    private enum DashboardConfigPersistenceType {
        DISK,
        DYNAMODB
    }

    private static final List<String> REQUIRED_CONFIG_KEYS_GLOBAL = Arrays.asList(HTTP_PORT, WEB_CONTEXTROOT,
            TEMP_PATH, MAX_SESSION_DURATION_MINUTES, PLUGINS, DASHBOARD_CONFIG_PERSISTENCE_TYPE, BROWSER_INSTANCES);

    public static final String LOGIN_URL = "login.url";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private static Config instance;
    private JSONObject globalConfig;
    private JSONObject dashboardsConfig;
    private List<Dashboard> dashboards;
    private List<Plugin> plugins;
    private DashboardConfigPersistenceType dashboardConfigPersistenceType;
    private DashboardConfig dashboardConfigStore;
    private DiskConfigStore diskConfigStore;
    private String globalConfigFileLastModifiedOnDisk;
    private String dashboardConfigLastModified;

    public static Config getInstance() {
        if (instance != null) {
            return instance;
        }

        synchronized(Config.class) {
            if (instance == null) {
                try {
                    instance = new Config();
                } catch (IOException | ParseException e) {
                    LOG.error("Initializing config failed: ", e);
                }
            }
            return instance;
        }

    }

    public Config() throws IOException, ParseException {
        diskConfigStore = new DiskConfigStore();
        intializeConfig();
    }

    private void intializeConfig() throws IOException, ParseException {
        readGlobalConfig();
        validateGlobalConfig();
        readDashboardsConfig();
        validateDashboardsConfig();
        parsePlugins();
        parseDashboards();
        globalConfigFileLastModifiedOnDisk = diskConfigStore.getLastUpdated();
        dashboardConfigLastModified = dashboardConfigStore.getLastUpdated();

        LOG.info("Config initialized. Configured {} dashboards with a total of {} graphs.",
                dashboards.size(), dashboards.stream().map(Dashboard::getGraphs).flatMap(Collection::stream).count());
    }

    public void checkForConfigUpdate() {
        LOG.info("Checking for updated global config file on disk.");
        String lastModifiedOnDisk = diskConfigStore.getLastUpdated();
        if (!lastModifiedOnDisk.equals(globalConfigFileLastModifiedOnDisk)) {
            LOG.info("Newer config file exists on disk, reloading.");
            try {
                intializeConfig();
                return;
            } catch (IOException | ParseException ex) {
                LOG.error("Error while reloading config file from disk: ", ex);
            }
        }

        // In case of DynamoDB config storage, also check for updates on database level.
        if (dashboardConfigPersistenceType == DashboardConfigPersistenceType.DYNAMODB) {
            LOG.info("Checking for updated config in DynamoDB.");
            String configLastUpdatedInDB = dashboardConfigStore.getLastUpdated();
            if (!configLastUpdatedInDB.equals(dashboardConfigLastModified)) {
                LOG.info("Newer config in DynamoDB than in memory, reloading.");
                try {
                    intializeConfig();
                    return;
                } catch (IOException | ParseException ex) {
                    LOG.error("Error while reloading config from DynamoDB: ", ex);
                }
            }
        }
    }

    private void readGlobalConfig() throws IOException, ParseException {
        // Read base config file from disk.
        String configStr = diskConfigStore.readGlobalConfigFromDisk();
        globalConfig = (JSONObject) new JSONParser().parse(new StringReader(configStr));

        // In case of disk dashboard config storage, the dashboard config is in the same config file,
        // but it's stored in a different object. Remove it from global config when present.
        globalConfig.remove(DASHBOARDS);
    }

    private void readDashboardsConfig() throws IOException, ParseException {
        String persistenceType = getString(DASHBOARD_CONFIG_PERSISTENCE_TYPE);
        if ("dynamodb".equals(persistenceType.toLowerCase())) {
            LOG.info("Using DynamoDB as persistence store for dashboard config.");
            dashboardConfigPersistenceType = DashboardConfigPersistenceType.DYNAMODB;
            dashboardConfigStore = new DynamoDBConfigStore(globalConfig);
        } else {
            LOG.info("Using disk as persistence store for dashboard config.");
            dashboardConfigPersistenceType = DashboardConfigPersistenceType.DISK;

            // Re-use existing disk config store (needed for global config).
            dashboardConfigStore = diskConfigStore;
        }

        String configStr = dashboardConfigStore.readConfig();
        dashboardsConfig = new JSONObject();

        if (configStr != null) {
            JSONObject fullConfig = (JSONObject) new JSONParser().parse(new StringReader(configStr));
            dashboardsConfig.put("dashboards", fullConfig.get("dashboards"));
        }
    }


    private void validateGlobalConfig() {
        // Check global config.
        REQUIRED_CONFIG_KEYS_GLOBAL.stream().forEach(requiredKey -> {
            if (!globalConfig.containsKey(requiredKey)) {
                throw new RuntimeException("Required config key '" + requiredKey + "' is missing.");
            }
        });
    }

    private void validateDashboardsConfig() {
        String validationResults = Dashboard.validateConfig(dashboardsConfig);

        if (StringUtils.isNotEmpty(validationResults)) {
            LOG.error("Validation of dashboards config failed: \n" + validationResults);
            throw new RuntimeException("Validation of dashboards config failed.");
        }
    }

    private void parseDashboards() {
        dashboards = Dashboard.parseConfig(dashboardsConfig, plugins, getString(TEMP_PATH));
    }

    private void parsePlugins() {
        plugins = new ArrayList<>();
        JSONArray pluginArr = (JSONArray)globalConfig.get(PLUGINS);
        pluginArr.forEach(pluginObj -> {
            JSONObject pluginJo = (JSONObject)pluginObj;
            Plugin plugin = new Plugin();
            String typeStr = readString(pluginJo, TYPE);
            plugin.setType(Graph.Type.fromString(typeStr));
            plugin.setLoginUrl(readString(pluginJo, LOGIN_URL));
            plugin.setUsername(readString(pluginJo, USERNAME));
            plugin.setPassword(readString(pluginJo, PASSWORD));
            plugin.setUpdateIntervalSeconds(readInt(pluginJo, BACKEND_UPDATE_INTERVAL_SECONDS));
            plugin.setBrowserInstance(readString(pluginJo, BROWSER_INSTANCE));
            plugins.add(plugin);
        });
    }

    public static Plugin getPlugin(List<Plugin> plugins, Graph.Type type) {
        if (plugins == null) {
            return null;
        }

        for (Plugin plugin : plugins) {
            if (type.equals(plugin.getType())) {
                return plugin;
            }
        }
        return null;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public Plugin getPlugin(Graph.Type type) {
        return getPlugin(plugins, type);
    }

    public String getString(String key) {
        return readString(globalConfig, key);
    }

    public int getInt(String key) {
        return readInt(globalConfig, key);
    }

    public static String readString(JSONObject jsonObject, String key) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public static int readInt(JSONObject jsonObject, String key) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return -1;
        }
        return Integer.valueOf(value.toString()).intValue();
    }

    public static Integer readInteger(JSONObject jsonObject, String key) {
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
        return Config.getInstance().getDashboards().stream().map(dash -> dash.getId()).collect(toList());
    }

    public String getContextRoot() {
        String contextRoot = getString(Config.WEB_CONTEXTROOT);
        if (contextRoot.endsWith("/")) {
            return contextRoot;
        }
        return contextRoot + "/";
    }

    public String getTSLastUpdate() {
        // Combination of global last modified and dashboards last modified.
        return globalConfigFileLastModifiedOnDisk + "-" + dashboardConfigLastModified;
    }

    public JSONObject getGlobalConfig() {
        return globalConfig;
    }

    public JSONObject getDashboardsConfig() {
        // Trigger check for config update to make sure that the config we fetch is up to date.
        Config.getInstance().checkForConfigUpdate();
        return dashboardsConfig;
    }

    public void updateDashboardsConfig(String dashboardsConfig, String tsPreviousUpdate) {
        dashboardConfigStore.updateConfig(dashboardsConfig, tsPreviousUpdate);

        // Trigger check for config update to make sure that the config in memory is up to date.
        Config.getInstance().checkForConfigUpdate();
    }

    public String getDashboardConfigLastModified() {
        return dashboardConfigLastModified;
    }

    public List<String> getBrowserInstances() {
        JSONArray browserInstances = (JSONArray) globalConfig.get("browserInstances");
        return (List<String>)browserInstances.stream().collect(toList());
    }

    public long getGraphCountForType(Graph.Type graphType) {
        return getGrapsForType(graphType).size();
    }

    public List<Graph> getGrapsForType(Graph.Type graphType) {
        return dashboards.stream().flatMap(dashboards -> dashboards.getGraphs().stream())
                .filter(graph -> graph.getType().equals(graphType)).collect(toList());

    }

}
