package nl.revolution.watchboard.data;

import nl.revolution.watchboard.Config;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Dashboard {

    private static final Logger LOG = LoggerFactory.getLogger(Dashboard.class);

    public static final String DASHBOARDS = "dashboards";
    public static final String GRAPHS = "graphs";
    public static final String TYPE = "type";
    public static final String BROWSER_WIDTH = "browserWidth";
    public static final String BROWSER_HEIGHT = "browserHeight";
    public static final String URL = "url";
    public static final String COMPONENTS = "components";
    public static final String EXTENSION_PNG = ".png";

    private static final List<String> REQUIRED_CONFIG_KEYS_DASHBOARD = Arrays.asList(Config.ID, Config.TITLE, GRAPHS);
    private static final List<String> REQUIRED_CONFIG_KEYS_GRAPH = Arrays.asList(TYPE, Config.ID, BROWSER_WIDTH, BROWSER_HEIGHT);

    private String id;
    private String title;
    private Integer defaultNumberOfColumns;
    private List<Graph> graphs = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

    public Integer getDefaultNumberOfColumns() {
        return defaultNumberOfColumns;
    }

    public void setDefaultNumberOfColumns(Integer defaultNumberOfColumns) {
        this.defaultNumberOfColumns = defaultNumberOfColumns;
    }

    public static String validateConfig(JSONObject dashboardsConfig) {
        StringBuilder validationResults = new StringBuilder();

        // Check dashboards config.
        JSONArray dashboards = (JSONArray)dashboardsConfig.get("dashboards");
        if (dashboards == null) {
            validationResults.append("Dashboard config is missing.");
            return validationResults.toString();
        }

        dashboards.stream().forEach(dashboard -> {
            REQUIRED_CONFIG_KEYS_DASHBOARD.stream().forEach(requiredKey -> {
                if (!((JSONObject) dashboard).containsKey(requiredKey)) {
                    validationResults.append("Required config key '" + requiredKey +
                            "' is missing for dashboard '" + ((JSONObject) dashboard).get("id") + "'.\n");
                }
            });

            // Check graphs config.
            JSONArray graphs = (JSONArray) ((JSONObject) dashboard).get("graphs");
            if (graphs == null) {
                validationResults.append("Dashboard '" + ((JSONObject) dashboard).get("id") + "' is missing graphs.");
            }

            if (graphs != null) {
                graphs.stream().forEach(graph -> {
                    REQUIRED_CONFIG_KEYS_GRAPH.stream().forEach(requiredGraphKey -> {
                        if (!((JSONObject) graph).containsKey(requiredGraphKey)) {
                            // Graph type 'disk' has almost no requirements, skip validation.
                            if (!"disk".equals(((JSONObject) graph).get("type"))) {
                                validationResults.append("Required config key '" + requiredGraphKey +
                                        "' is missing for dashboard '" + ((JSONObject) dashboard).get("id") +
                                        "', graph '" + ((JSONObject) graph).get("id") + "'.\n");
                            }
                        }
                    });
                });
            }

        });

        // TODO: validate plugin config

        return validationResults.toString();
    }



    public static List<Dashboard> parseConfig(JSONObject dashboardsConfig, List<Plugin> plugins, String tempPath) {
        List<Dashboard> dashboards = new ArrayList<>();

        JSONArray dashArr = (JSONArray)dashboardsConfig.get(DASHBOARDS);
        for (int dashIndex = 0; dashIndex < dashArr.size(); dashIndex++) {
            JSONObject dashObj = (JSONObject) dashArr.get(dashIndex);
            Dashboard dashboard = new Dashboard();
            dashboard.setId(Config.readString(dashObj, Config.ID));
            dashboard.setTitle(Config.readString(dashObj, Config.TITLE));
            dashboard.setDefaultNumberOfColumns(Config.readInteger(dashObj, Config.DEFAULT_NUMBER_OF_COLUMNS));

            JSONArray graphsJa = (JSONArray)dashObj.get(GRAPHS);
            for (int graphIndex = 0; graphIndex < graphsJa.size(); graphIndex++) {
                JSONObject graphObj = (JSONObject) graphsJa.get(graphIndex);
                Graph graph = new Graph();

                String typeStr = Config.readString(graphObj, TYPE);
                Graph.Type graphType = Graph.Type.fromString(typeStr);
                graph.setType(graphType);

                String url = Config.readString(graphObj, URL);
                if (Graph.Type.PERFORMR.equals(graph.getType()) && StringUtils.isEmpty(url)) {
                    url = Config.getPlugin(plugins, Graph.Type.PERFORMR).getLoginUrl();
                }
                graph.setUrl(url);
                graph.setId(Config.readString(graphObj, Config.ID));

                graph.setBrowserWidth(Config.readInt(graphObj, BROWSER_WIDTH));
                graph.setBrowserHeight(Config.readInt(graphObj, BROWSER_HEIGHT));
                graph.setImagePath(tempPath + "/" + Config.readString(graphObj, Config.ID).toString() + EXTENSION_PNG);

                Object componentsObj = graphObj.get(COMPONENTS);
                if (componentsObj != null) {
                    graph.setComponents((List) componentsObj);
                }
                dashboard.getGraphs().add(graph);
            }
            dashboards.add(dashboard);
        }

        // Postprocess step: try to find a matching URL for each graph of type 'disk'.
        dashboards.stream().flatMap(dbs -> dbs.getGraphs().stream())
                .filter(graph -> graph.getType().equals(Graph.Type.DISK)).forEach(graphWithDiskSource -> {
            Optional<Graph> graphWithMatchingId =
                    dashboards.stream().flatMap(dbs -> dbs.getGraphs().stream())
                            .filter(graph -> !graph.getType().equals(Graph.Type.DISK))
                            .filter(graph -> graph.getId().equals(graphWithDiskSource.getId()))
                            .findFirst();
            if (graphWithMatchingId.isPresent()) {
                graphWithDiskSource.setUrl(graphWithMatchingId.get().getUrl());
            }
        });

        return dashboards;
    }


}
