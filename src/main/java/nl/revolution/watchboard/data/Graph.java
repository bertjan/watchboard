package nl.revolution.watchboard.data;

import org.json.simple.JSONObject;

import java.io.File;

public class Graph {

    private String url;
    private String id;
    private String imagePath;
    private int browserWidth;
    private int browserHeight;
    private int imageHeight;

    public JSONObject toJSON(String contextRoot) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("url", url);
        json.put("filename", contextRoot + "images/" + id + ".png");
        json.put("lastModified", new File(imagePath).lastModified());
        json.put("height", imageHeight);
        return json;
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

