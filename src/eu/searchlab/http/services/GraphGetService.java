package eu.searchlab.http.services;

import org.json.JSONObject;

import eu.searchlab.HTMLPanel;
import eu.searchlab.http.Service;

public class GraphGetService extends AbstractService implements Service {

    @Override
    public boolean supportsPath(String path) {
        if (!path.startsWith("api/graph/")) return false;
        path = path.substring(10);
        final int p = path.indexOf('.');
        if (p < 0) return false;
        final String tablename = path.substring(0, p);
        if (!HTMLPanel.htmls.containsKey(tablename)) return false;
        final String ext = path.substring(p + 1);
        return ext.equals("html");
    }

    @Override
    public Type getType() {
        return Service.Type.STRING;
    }

    @Override
    public String serveString(JSONObject post) {

        final String path = post.optString("PATH", "");
        final int p = path.lastIndexOf("/graph/");
        if (p < 0) return "";
        final int q = path.indexOf(".", p);
        final String graphname = path.substring(p + 7, q);
        final String graph = HTMLPanel.htmls.get(graphname);
        return graph;
    }
}
