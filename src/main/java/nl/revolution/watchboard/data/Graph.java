package nl.revolution.watchboard.data;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.util.List;

public class Graph {

    public static final String ID = "id";
    public static final String URL = "url";
    public static final String TYPE = "type";
    public static final String FILENAME = "filename";
    public static final String LAST_MODIFIED = "lastModified";
    public static final String IMAGES_PATH = "images/";
    public static final String IMAGE_SUFFIX = ".png";
    public static final String COMPONENTS = "components";

    public enum Type {
        CLOUDWATCH, PERFORMR, DISK, CLOUDWATCH_DASHBOARD, KIBANA, KIBANA5, SONAR;

        public static Type fromString(String typeStr) {
            if (StringUtils.isEmpty(typeStr)) {
                return null;
            }
            typeStr = typeStr.toUpperCase();
            if (CLOUDWATCH.toString().equals(typeStr)) {
                return CLOUDWATCH;
            } else if (PERFORMR.toString().equals(typeStr)) {
                return PERFORMR;
            } else if (DISK.toString().equals(typeStr)) {
                return DISK;
            } else if ("CLOUDWATCH-DASHBOARD".toString().equals(typeStr)) {
                return CLOUDWATCH_DASHBOARD;
            } else if (KIBANA.toString().equals(typeStr)) {
                return KIBANA;
            } else if (KIBANA5.toString().equals(typeStr)) {
                return KIBANA5;
            } else if (SONAR.toString().equals(typeStr)) {
                return SONAR;
            }
        return null;
        }
    }

    private String url;
    private String id;
    private Type type;
    private String imagePath;
    private int browserWidth;
    private int browserHeight;
    private int timeRange;
    private List<String> components;

    public JSONObject toJSON(String contextRoot) {
        JSONObject json = new JSONObject();
        json.put(ID, id);
        json.put(URL, url);
        json.put(TYPE, type.toString());
        json.put(FILENAME, contextRoot + IMAGES_PATH + id + IMAGE_SUFFIX);
        json.put(LAST_MODIFIED, determineLastModified());

        if (components != null) {
            JSONArray componentsJa = new JSONArray();
            componentsJa.addAll(components);
            json.put(COMPONENTS, componentsJa);
        }
        return json;
    }

    protected long determineLastModified() {
        return new File(imagePath).lastModified();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getBrowserWidth() {
        return browserWidth;
    }

    public void setBrowserWidth(int browserWidth) {
        this.browserWidth = browserWidth;
    }

    public int getBrowserHeight() {
        return browserHeight;
    }

    public void setBrowserHeight(int browserHeight) {
        this.browserHeight = browserHeight;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public int getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(int timeRange) {
        this.timeRange = timeRange;
    }

}

