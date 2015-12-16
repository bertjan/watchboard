package nl.revolution.watchboard.persistence;

import java.io.IOException;

public interface DashboardConfig {

    String readConfig() throws IOException;
    void updateConfig(String config, String tsPreviousUpdate);
    String getLastUpdated();

}
