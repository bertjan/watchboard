package nl.revolution.watchboard.data;

import org.json.simple.JSONObject;

import java.io.File;

public class Graph {

    public static final String ID = "id";
    public static final String URL = "url";
    public static final String FILENAME = "filename";
    public static final String LAST_MODIFIED = "lastModified";
    public static final String HEIGHT = "height";
    public static final String IMAGES_PATH = "images/";
    public static final String IMAGE_SUFFIX = ".png";

    private String url;
    private String id;
    private String imagePath;
    private int browserWidth;
    private int browserHeight;
    private int imageHeight;

    public JSONObject toJSON(String contextRoot) {
        JSONObject json = new JSONObject();
        json.put(ID, id);
        json.put(URL, url);
        json.put(FILENAME, contextRoot + IMAGES_PATH + id + IMAGE_SUFFIX);
        json.put(LAST_MODIFIED, determineLastModified());
        json.put(HEIGHT, imageHeight);
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

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }


}

