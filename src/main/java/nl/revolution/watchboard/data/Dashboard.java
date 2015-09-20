package nl.revolution.watchboard.data;

import java.util.ArrayList;
import java.util.List;

public class Dashboard {
    private String id;
    private String title;
    private List<Graph> graphs = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

}
