package eu.searchlab.http.services;

import org.json.JSONObject;

import eu.searchlab.http.Service;

public class MirrorService extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/mirror.json"};
    }

    @Override
    public JSONObject serveObject(JSONObject post) {
        return post; // mirror the post
    }

}
