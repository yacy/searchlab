package eu.searchlab.http;

import org.json.JSONArray;
import org.json.JSONObject;

public interface Service {

    public enum Type {
        OBJECT, ARRAY;
    }

    public String[] getPaths();

    public Type getType();

    public JSONObject serveObject(JSONObject post);

    public JSONArray serveArray(JSONObject post);

}
