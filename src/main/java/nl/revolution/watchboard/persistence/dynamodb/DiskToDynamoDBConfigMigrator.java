package nl.revolution.watchboard.persistence.dynamodb;

import nl.revolution.watchboard.Config;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskToDynamoDBConfigMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(DiskToDynamoDBConfigMigrator.class);

    public static void main(String... args) {
        // Before running this, make sure that config key "dashboard.config.persistence.type"
        // is set to "disk" since we'll need to read it from disk once in order to write it to DynamoDB.
        JSONObject dashboardsJo = Config.getInstance().getDashboardsConfig();
        JSONObject dashboardConfig = new JSONObject();
        dashboardConfig.put("id", DynamoDBConfigStore.DASHBOARD_CONFIG_DOCUMENT_KEY);
        dashboardConfig.put("dashboards", dashboardsJo.get("dashboards"));
        DynamoDBConfigStore dynamoDBConfigStore = new DynamoDBConfigStore(Config.getInstance().getGlobalConfig());
        dynamoDBConfigStore.writeConfig(dashboardConfig.toJSONString());

        String configFromDynamoDB = dynamoDBConfigStore.readConfig();
        LOG.info("Wrote config. Result: ");
        LOG.info(configFromDynamoDB);
    }
}
