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

import eu.searchlab.aaaaa.Authorization.Grade;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;
import net.yacy.grid.io.index.IndexDAO;

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
        final boolean authorized = serviceRequest.isAuthorized();
        try {
            context.put("forUser", user_id);
            context.put("forUser_disabled", !maintainer);
            context.put("all_delete_disabled", !authorized);
            context.put("all_simulate_disabled", !authorized);
            context.put("collection_delete_disabled", !authorized);
            context.put("collection_simulate_disabled", !authorized);
            context.put("domain_delete_disabled", !authorized);
            context.put("domain_simulate_disabled", !authorized);
        	context.put("domain", "");
        	context.put("deleted", 0);
        	context.put("simulated", 0);
        } catch (final JSONException e) {
            Logger.error(e);
        }
        
        // prepare result json
        final JSONObject json = new JSONObject(true);
        try {
            json.put("context", context);
        } catch (final JSONException e) {
            Logger.error(e);
        }

        // admin settings to delete for another user
        final String for_user_id = serviceRequest.get("forUser", user_id);
        if (for_user_id.length() > 0 && maintainer) user_id = for_user_id;
        
        // get selected feature
        final boolean allSimulateDeletion = !serviceRequest.get("AllSimulateDeletion", "").isEmpty();
        final boolean allDelete = !serviceRequest.get("AllDelete", "").isEmpty();

        final boolean collectionSimulateDeletion = !serviceRequest.get("CollectionSimulateDeletion", "").isEmpty();
        final boolean collectionDelete = !serviceRequest.get("CollectionDelete", "").isEmpty();

        final boolean domainSimulateDeletion = !serviceRequest.get("DomainSimulateDeletion", "").isEmpty();
        final boolean domainDelete = !serviceRequest.get("DomainDelete", "").isEmpty();

        // perform the wanted feature
        long deleted = 0;

        // do the deletion for all documents
        if (authorized && allSimulateDeletion) {
            deleted = IndexDAO.getIndexDocumentsByUserID(user_id);
            Logger.info("deleted (simulated) " + deleted + " documents for user " + user_id);
            try {context.put("simulated", deleted);} catch (JSONException e) {}
        }
        if (authorized && allDelete) {
            deleted = IndexDAO.deleteIndexDocumentsByUserID(user_id);
            Logger.info("deleted " + deleted + " documents for user " + user_id);
            try {context.put("deleted", deleted);} catch (JSONException e) {}
        }

        // do the deletion for collections
        final String collectionss = serviceRequest.get("collection", "").trim();
        final String[] collections = collectionss.isEmpty() ? new String[0]: collectionss.split(",");
        if (authorized && collectionSimulateDeletion && collections.length > 0) {
        	try {context.put("domain", collectionss);} catch (JSONException e) {}
            for (final String collection: collections) {
                deleted += IndexDAO.getIndexDocumentByCollectionCount(user_id, collection.trim());
                Logger.info("deleted (simulated) " + deleted + " documents for user " + user_id + ", collection " + collection.trim());
            }
            try {context.put("simulated", deleted);} catch (JSONException e) {}
        }
        if (authorized && collectionDelete && collections.length > 0) {
        	try {context.put("domain", collectionss);} catch (JSONException e) {}
            for (final String collection: collections) {
            	deleted += IndexDAO.deleteIndexDocumentsByCollectionName(user_id, collection.trim());
                Logger.info("deleted " + deleted + " documents for user " + user_id + ", collection " + collection.trim());
            }
            try {context.put("deleted", deleted);} catch (JSONException e) {}
        }

        // do the deletion for domains
        final String domainss = serviceRequest.get("domain", "").trim();
        final String[] domains = domainss.isEmpty() ? new String[0]: domainss.split(",");
        if (authorized && domainSimulateDeletion && domains.length > 0) {
        	try {context.put("domain", domainss);} catch (JSONException e) {}
            for (final String domain: domains) {
                deleted += IndexDAO.getIndexDocumentsByDomainNameCount(user_id, domain.trim());
                Logger.info("deleted (simulated) " + deleted + " documents for user " + user_id + ", domain " + domain.trim());
            }
            try {context.put("simulated", deleted);} catch (JSONException e) {}
        }
        if (authorized && domainDelete && domains.length > 0) {
        	try {context.put("domain", domainss);} catch (JSONException e) {}
            for (final String domain: domains) {
            	deleted += IndexDAO.deleteIndexDocumentsByDomainName(user_id, domain.trim());
                Logger.info("deleted " + deleted + " documents for user " + user_id + ", domain " + domain.trim());
            }
            try {context.put("deleted", deleted);} catch (JSONException e) {}
        }

        // prepare result
        final long documentCount = IndexDAO.getIndexDocumentTimeCount(user_id, System.currentTimeMillis() - 10000).count;
        final long collectionCount = IndexDAO.getIndexDocumentCollectionCount(user_id);
        try {
            final JSONObject assets = new JSONObject(true);
            final JSONObject size = new JSONObject(true);
            assets.put("size", size);
            size.put("documents", documentCount);
            size.put("collections", collectionCount);
            json.put("assets", assets);
        } catch (final JSONException e) {
            Logger.error(e);
        }

        // finally add the crawl start on the queue
        return new ServiceResponse(json);
    }
}
