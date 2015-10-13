package nl.revolution.watchboard.plugins;

public interface WatchboardPlugin {

    void initialize();

    void performUpdate();

    void shutdown();

}
