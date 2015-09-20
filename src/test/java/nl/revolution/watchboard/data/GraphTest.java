package nl.revolution.watchboard.data;

import org.json.simple.JSONObject;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GraphTest {

    public static final int LAST_MODIFIED_VALUE = 200;
    public static final int IMAGE_HEIGHT = 100;
    public static final String ID = "id";
    public static final String URL = "url";
    public static final String IMAGE_PATH = "imagePath";
    public static final String CONTEXT_ROOT = "contextRoot/";

    @Test
    public void toJSON() throws Exception {
        Graph graph = createTestGraph();
        graph.setId(ID);
        graph.setUrl(URL);
        graph.setImagePath(IMAGE_PATH);
        graph.setImageHeight(IMAGE_HEIGHT);

        JSONObject expected = new JSONObject();
        expected.put(Graph.FILENAME, CONTEXT_ROOT + Graph.IMAGES_PATH + ID + Graph.IMAGE_SUFFIX);
        expected.put(Graph.ID, ID);
        expected.put(Graph.LAST_MODIFIED, LAST_MODIFIED_VALUE);
        expected.put(Graph.URL, URL);
        expected.put(Graph.HEIGHT, IMAGE_HEIGHT);

        JSONObject actual = graph.toJSON(CONTEXT_ROOT);

        assertThat(actual.toJSONString(), is(expected.toJSONString()));
    }

    private Graph createTestGraph() {
        return new Graph() {
            protected long determineLastModified() {
                return LAST_MODIFIED_VALUE;
            }
        };
    }
}