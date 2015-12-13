package nl.revolution.watchboard.persistence.dynamodb;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import nl.revolution.watchboard.Config;
import nl.revolution.watchboard.persistence.DashboardConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class DynamoDBConfigStore implements DashboardConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DynamoDBConfigStore.class);

    public static final String DASHBOARD_CONFIG_DOCUMENT_KEY = "dashboards";
    public static final String ID_KEY = "id";
    public static final String UPDATED_AT_KEY = "updatedAt";
    private Table table;
    private static DynamoDBConfigStore instance;

    public static DynamoDBConfigStore getInstance() {
        if (instance == null) {
            synchronized(DynamoDBConfigStore.class) {
                if (instance == null) {
                    instance = new DynamoDBConfigStore();
                }
            }
        }
        return instance;
    }

    private DynamoDBConfigStore() {
        String accessKeyId = Config.getInstance().getAWSAccessKeyId();
        String secretKeyId = Config.getInstance().getAWSSecretKeyId();
        AWSCredentials awsCredentials = new BasicAWSCredentials(accessKeyId, secretKeyId);
        AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient(new StaticCredentialsProvider(awsCredentials));
        dynamoDBClient.setRegion(Region.getRegion(Regions.fromName(Config.getInstance().getAWSRegion())));
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
        table = dynamoDB.getTable(Config.getInstance().getAWSDynamoDBTableName());
    }

    private Item readConfigAsItem() {
        return table.getItem(new PrimaryKey().addComponent(ID_KEY, DASHBOARD_CONFIG_DOCUMENT_KEY));
    }

    @Override
    public String readConfig() {
        Item config = readConfigAsItem();
        return config.toJSONPretty();
    }

    @Override
    public void updateConfig(String dashboardConfig) {
        // TODO: locking on UPDATED_AT_KEY

        Item oldConfig = table.getItem(new PrimaryKey().addComponent(ID_KEY, DASHBOARD_CONFIG_DOCUMENT_KEY));
        String currentTime = LocalDateTime.now().toString();
        String newId = DASHBOARD_CONFIG_DOCUMENT_KEY + "-" + currentTime;
        oldConfig.withKeyComponent(ID_KEY, newId);
        writeConfig(oldConfig);
        LOG.info("Backed up old config version with suffix '" + currentTime + "'.");

        Item newConfig = Item.fromJSON(dashboardConfig);
        newConfig.with(UPDATED_AT_KEY, currentTime);
        writeConfig(newConfig);
    }

    @Override
    public String getLastUpdated() {
        Item configItem = readConfigAsItem();
        return String.valueOf(configItem.get(UPDATED_AT_KEY));
    }

    public void writeConfig(Item dashboardConfig) {
        if (!dashboardConfig.isPresent(UPDATED_AT_KEY)) {
            dashboardConfig.with(UPDATED_AT_KEY, LocalDateTime.now().toString());
        }
        table.putItem(dashboardConfig);
        LOG.info("Wrote config.");
    }

    public void writeConfig(String dashboardConfig) {
        writeConfig(Item.fromJSON(dashboardConfig));
    }

}
