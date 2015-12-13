package nl.revolution.watchboard.persistence.dynamodb;

import nl.revolution.watchboard.Config;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskToDynamoDBConfigMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(DiskToDynamoDBConfigMigrator.class);

    public static void main(String... args) {
        JSONArray dashboardsJa = (JSONArray) Config.getInstance().getObject("dashboards");
        JSONObject dashboardConfig = new JSONObject();
        dashboardConfig.put("id", DynamoDBConfigStore.DASHBOARD_CONFIG_DOCUMENT_KEY);
        dashboardConfig.put("dashboards", dashboardsJa);
        DynamoDBConfigStore.getInstance().writeConfig(dashboardConfig.toJSONString());

        String configFromDynamoDB = DynamoDBConfigStore.getInstance().readConfig();
        LOG.info("Wrote config. Result: ");
        LOG.info(configFromDynamoDB);
    }
}
