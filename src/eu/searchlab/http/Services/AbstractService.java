package eu.searchlab.http.services;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.searchlab.http.Service;

public abstract class AbstractService implements Service {

    @Override
    public Type getType() {
        return Service.Type.OBJECT;
    }

    @Override
    public JSONObject serveObject(JSONObject post) {
        return new JSONObject();
    }

    @Override
    public JSONArray serveArray(JSONObject post) {
        return new JSONArray();
    }

}
