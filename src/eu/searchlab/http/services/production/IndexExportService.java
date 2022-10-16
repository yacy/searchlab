/**
 *  IndexExportService
 *  Copyright 12.10.2022 by Michael Peter Christen, @orbiterlab
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authorization.Grade;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import net.yacy.grid.io.index.IndexDAO;

/**
 *
 * Test URL:
 * http://localhost:8400/en/api/export.json?domain=tagesschau.de
 */
public class IndexExportService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/export.json", "/production/export/"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {

        final JSONObject context = new JSONObject();
        String user_id = serviceRequest.getUser();
        final boolean maintainer = serviceRequest.getAuthorizationGrade() == Grade.L08_Maintainer;
        final boolean authorized = serviceRequest.isAuthorized();
        context.put("forUser", user_id);
        context.put("forUser_disabled", !maintainer);
        context.put("simulate_disabled", !authorized);
        context.put("all_export_disabled", true);
        context.put("collection_export_disabled", true);
        context.put("domain_export_disabled", true);
        context.put("query_export_disabled", true);
        context.put("domain", "");
        context.put("exported", 0);
        context.put("simulated", 0);

        // prepare result json
        final JSONObject json = new JSONObject(true);
        json.put("context", context);

        // admin settings to export for another user
        final String for_user_id = serviceRequest.get("forUser", user_id);
        if (for_user_id.length() > 0 && maintainer) user_id = for_user_id;

        // get selected feature
        final boolean allSimulateDeletion = !serviceRequest.get("AllSimulateDeletion", "").isEmpty();
        final boolean allExport = !serviceRequest.get("AllExport", "").isEmpty();

        final boolean collectionSimulateDeletion = !serviceRequest.get("CollectionSimulateDeletion", "").isEmpty();
        final boolean collectionExport = !serviceRequest.get("CollectionExport", "").isEmpty();

        final boolean domainSimulateDeletion = !serviceRequest.get("DomainSimulateDeletion", "").isEmpty();
        final boolean domainExport = !serviceRequest.get("DomainExport", "").isEmpty();

        final boolean querySimulateDeletion = !serviceRequest.get("QuerySimulateDeletion", "").isEmpty();
        final boolean queryExport = !serviceRequest.get("QueryExport", "").isEmpty();

        // perform the wanted feature
        long exported = 0;
        final String prefix = "export-" + DateParser.secondDateFormatParser().format(new Date());
        final IOPath assetsPath = Searchlab.accounting.getAssetsPathForUser(user_id);
        final IOPath exportPath = assetsPath.append("export");
        
        // do the export for all documents
        if (authorized && allSimulateDeletion) {
            exported = IndexDAO.getIndexDocumentsByUserID(user_id);
            Logger.info("exported (simulated) " + exported + " documents for user " + user_id);
            context.put("simulated", exported);
            context.put("all_export_disabled", false);
        }
        if (authorized && allExport) {
        	String exportName = prefix + "-all";
        	IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, "jsonlist");
                FileOutputStream fos = new FileOutputStream(tempFile);
                exported = IndexDAO.exportIndexDocumentsByUserID(user_id, fos);
                fos.close();
                Searchlab.io.writeGZIP(targetPath, tempFile);
                tempFile.delete();
                Logger.info("exported all " + exported + " documents for user " + user_id + " to " + targetPath.toString());
                context.put("exported", exported);
            } catch (final IOException e) {
                Logger.warn("failed to export");
            }
        }

        // do the export for collections
        final String collectionss = serviceRequest.get("collection", "").trim();
        final String[] collections = collectionss.isEmpty() ? new String[0]: collectionss.split(",");
        if (authorized && collectionSimulateDeletion && collections.length > 0) {
            context.put("domain", collectionss);
            for (final String collection: collections) {
                exported += IndexDAO.getIndexDocumentByCollectionCount(user_id, collection.trim());
                Logger.info("exported (simulated) " + exported + " documents for user " + user_id + ", collection " + collection.trim());
            }
            context.put("simulated", exported);
            context.put("collection_export_disabled", false);
        }
        if (authorized && collectionExport && collections.length > 0) {
        	String exportName = prefix + "-collection";
        	IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, "jsonlist");
                FileOutputStream fos = new FileOutputStream(tempFile);
                context.put("domain", collectionss);
                for (final String collection: collections) {
                    exported += IndexDAO.exportIndexDocumentsByCollectionName(user_id, collection.trim(), fos);
                    Logger.info("exported " + exported + " documents for user " + user_id + ", collection " + collection.trim() + " to " + targetPath.toString());
                }
                fos.close();
                Searchlab.io.writeGZIP(targetPath, tempFile);
                tempFile.delete();
                context.put("exported", exported);
            } catch (final IOException e) {
                Logger.warn("failed to export");
            }
        }

        // do the export for domains
        final String domainss = serviceRequest.get("domain", "").trim();
        final String[] domains = domainss.isEmpty() ? new String[0]: domainss.split(",");
        if (authorized && domainSimulateDeletion && domains.length > 0) {
            context.put("domain", domainss);
            for (final String domain: domains) {
                exported += IndexDAO.getIndexDocumentsByDomainNameCount(user_id, domain.trim());
                Logger.info("exported (simulated) " + exported + " documents for user " + user_id + ", domain " + domain.trim());
            }
            context.put("simulated", exported);
            context.put("domain_export_disabled", false);
        }
        if (authorized && domainExport && domains.length > 0) {
        	String exportName = prefix + "-domain";
        	IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, "jsonlist");
                FileOutputStream fos = new FileOutputStream(tempFile);
                context.put("domain", domainss);
                for (final String domain: domains) {
                    exported += IndexDAO.exportIndexDocumentsByDomainName(user_id, domain.trim(), fos);
                    Logger.info("exported " + exported + " documents for user " + user_id + ", domain " + domain.trim() + " to " + targetPath.toString());
                }
                fos.close();
                Searchlab.io.writeGZIP(targetPath, tempFile);
                tempFile.delete();
                context.put("exported", exported);
            } catch (final IOException e) {
                Logger.warn("failed to export");
            }
        }

        // do the export for queries
        final String query = serviceRequest.get("query", "").trim();
        if (authorized && querySimulateDeletion && query.length() > 0) {
            context.put("query", query);
            exported += IndexDAO.getIndexDocumentsByQueryCount(user_id, query);
            Logger.info("exported (simulated) " + exported + " documents for user " + user_id + ", query " + query.trim());
            context.put("simulated", exported);
            context.put("query_export_disabled", false);
        }
        if (authorized && queryExport && query.length() > 0) {
        	String exportName = prefix + "-query";
        	IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, "jsonlist");
                FileOutputStream fos = new FileOutputStream(tempFile);
                context.put("query", query);
                exported = IndexDAO.exportIndexDocumentsByQuery(user_id, query, fos);
                fos.close();
                Searchlab.io.writeGZIP(targetPath, tempFile);
                tempFile.delete();
                Logger.info("exported " + exported + " documents for user " + user_id + ", query " + query.trim() + " to " + targetPath.toString());
                context.put("exported", exported);
            } catch (final IOException e) {
                Logger.warn("failed to export");
            }
        }

        // prepare result
        final long documentCount = IndexDAO.getIndexDocumentTimeCount(user_id, System.currentTimeMillis() - 10000).count;
        final long collectionCount = IndexDAO.getIndexDocumentCollectionCount(user_id);
        final JSONObject assets = new JSONObject(true);
        final JSONObject size = new JSONObject(true);
        assets.put("size", size);
        size.put("documents", documentCount);
        size.put("collections", collectionCount);
        json.put("assets", assets);

        // finally add the crawl start on the queue
        return new ServiceResponse(json);
    }
}
