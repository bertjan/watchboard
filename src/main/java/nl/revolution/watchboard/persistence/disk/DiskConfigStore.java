package nl.revolution.watchboard.persistence.disk;

import nl.revolution.watchboard.persistence.DashboardConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class DiskConfigStore implements DashboardConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DiskConfigStore.class);

    @Override
    public String readConfig() throws IOException {
        File configFile = getConfigFile();
        LOG.info("Using config file: {}", configFile.getAbsolutePath());
        return FileUtils.readFileToString(configFile);
    }

    @Override
    public void updateConfig(String config, String tsPreviousUpdate) {
        throw new NotImplementedException("Disk config can't be updated (yet).");
    }

    @Override
    public String getLastUpdated() {
        return String.valueOf(getConfigFile().lastModified());
    }

    public File getConfigFile() {
        String configFilePath = getCurrentPath() + "/config.json";
        return new File(configFilePath);
    }

    private String getCurrentPath() {
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().toString().replaceAll("file:", "");
        return path.substring(0, path.lastIndexOf("/"));
    }

    public String readGlobalConfigFromDisk() throws IOException {
        return readConfig();
    }

}
