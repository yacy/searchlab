/**
 *  CrawlStartService
 *  Copyright 21.04.2022 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.searchlab.http.services.production;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authorization.Grade;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;
import net.yacy.grid.io.index.ElasticsearchClient;
import net.yacy.grid.io.index.WebMapping;

/**
 *
 * Test URL:
 * http://localhost:8400/en/api/deletion.json?domain=tagesschau.de
 */
public class IndexDeletionService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/deletion.json", "/production/deletion/"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {

        final JSONObject context = new JSONObject();
        String user_id = serviceRequest.getUser();
        final boolean maintainer = serviceRequest.getAuthorizationGrade() == Grade.L08_Maintainer;
        try {
            context.put("forUser", user_id);
            context.put("forUser_disabled", !maintainer);
            context.put("delete_disabled", !serviceRequest.isAuthorized());
        } catch (final JSONException e) {
            Logger.error(e);
        }

        final String for_user_id = serviceRequest.get("forUser", user_id);
        if (for_user_id.length() > 0 && maintainer) user_id = for_user_id;

        // do the deletion
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final String domainss = serviceRequest.get("domain", "").trim();
        if (!domainss.isEmpty()) {
            final String[] domains = domainss.split(",");
            for (final String domain: domains) {
                final int deleted = Searchlab.ec.delete(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, WebMapping.host_s.getMapping().name(), domain.trim());
                Logger.info("deleted " + deleted + " documents for user " + user_id + ", domain " + domain.trim());
            }
        }

        // prepare result
        final long documents = Searchlab.ec.count(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id);
        final long collections = Searchlab.ec.aggregationCount(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, WebMapping.collection_sxt.getMapping().name());
        final JSONObject json = new JSONObject(true);
        try {
            final JSONObject assets = new JSONObject(true);
            final JSONObject size = new JSONObject(true);
            assets.put("size", size);
            size.put("documents", documents);
            size.put("collections", collections);
            json.put("assets", assets);

            json.put("context", context);
        } catch (final JSONException e) {
            Logger.error(e);
        }

        // finally add the crawl start on the queue
        return new ServiceResponse(json);
    }
}
