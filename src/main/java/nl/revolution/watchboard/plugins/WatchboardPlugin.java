package nl.revolution.watchboard.plugins;

import nl.revolution.watchboard.utils.WebDriverWrapper;

public interface WatchboardPlugin {

    void performLogin();

    void performUpdate();

    void shutdown();

    void setDriver(WebDriverWrapper driver);

    String getName();

    int getUpdateInterval();

}
