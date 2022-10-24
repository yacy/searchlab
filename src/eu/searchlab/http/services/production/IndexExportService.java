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
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authorization.Grade;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.Cons;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import eu.searchlab.tools.Progress;
import net.yacy.grid.io.index.IndexDAO;

/**
 *
 * Test URL:
 * http://localhost:8400/en/api/export.json?domain=tagesschau.de
 */
public class IndexExportService  extends AbstractService implements Service {

    private final static ExecutorService executorService = Executors.newCachedThreadPool();

    // we maintain a static object to track already running export threads
    private static Map<String, Cons<Progress<Long>, Future<Long>>> exportRunners = new ConcurrentHashMap<>(); // only one runner for each user at the same time

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
        context.put("showSimulated", false);
        context.put("showExporting", false);
        context.put("showExported", false);
        context.put("exported", 0);
        context.put("simulated", 0);

        // prepare result json
        final JSONObject json = new JSONObject(true);
        json.put("context", context);

        // admin settings to export for another user
        final String for_user_id = serviceRequest.get("forUser", user_id);
        if (for_user_id.length() > 0 && maintainer) user_id = for_user_id;

        final long expected = serviceRequest.get("expected", 0);

        // get selected feature
        final boolean allSimulateExport = !serviceRequest.get("AllSimulateExport", "").isEmpty();
        final boolean allExport = expected > 0 && !serviceRequest.get("AllExport", "").isEmpty();

        final boolean collectionSimulateExport = !serviceRequest.get("CollectionSimulateExport", "").isEmpty();
        final boolean collectionExport = expected > 0 && !serviceRequest.get("CollectionExport", "").isEmpty();

        final boolean domainSimulateExport = !serviceRequest.get("DomainSimulateExport", "").isEmpty();
        final boolean domainExport = expected > 0 && !serviceRequest.get("DomainExport", "").isEmpty();

        final boolean querySimulateExport = !serviceRequest.get("QuerySimulateExport", "").isEmpty();
        final boolean queryExport = expected > 0 && !serviceRequest.get("QueryExport", "").isEmpty();

        // perform the wanted feature
        long exported = 0;
        final String prefix = "searchlab-export-" + new SimpleDateFormat(DateParser.YEARTOSECONDFILENAME, Locale.US).format(new Date());
        final IOPath assetsPath = Searchlab.accounting.getAssetsPathForUser(user_id);
        final IOPath exportPath = assetsPath.append("export");

        // get running export data (1st time we retieve this to enable/disable functions; we read this again later aftter functions have performed)
        Cons<Progress<Long>, Future<Long>> exportStatus = exportRunners.get(user_id);
        Progress<Long> exportProgress = exportStatus == null ? null : exportStatus.car;
        Future<Long> exportFuture = exportStatus == null ? null : exportStatus.cdr;
        boolean exportRunning = exportProgress != null && exportFuture != null && !exportFuture.isDone();

        // do the export for all documents
        if (authorized && !exportRunning && allSimulateExport) {
            exported = IndexDAO.getIndexDocumentsByUserID(user_id);
            Logger.info("exported (simulated) " + exported + " documents for user " + user_id);
            context.put("simulated", exported);
            context.put("showSimulated", true);
            context.put("all_export_disabled", false);
        }
        if (authorized && !exportRunning && allExport) {
            final String exportName = prefix + "-all.jsonlist.gz";
            final IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, null);
                final OutputStream os = new GZIPOutputStream(new FileOutputStream(tempFile), 8192);
                // create a progress and future object to work on the task
                final Progress<Long> callable = IndexDAO.exportIndexDocumentsByUserID(expected, user_id, os);
                final Future<Long> exportThread = executorService.submit(callable);
                exportRunners.put(user_id, Cons.of(callable, exportThread));
                Searchlab.io.write(targetPath, tempFile);
                tempFile.delete();
                Logger.info("starting exported of " + expected + " documents for user " + user_id + " to " + targetPath.toString());
                context.put("showExported", true);
            } catch (final Exception e) {
                Logger.warn("failed to export");
            }
        }

        // do the export for collections
        final String collectionss = serviceRequest.get("collection", "").trim();
        final String[] collections = collectionss.isEmpty() ? new String[0]: collectionss.split(",");
        if (authorized && !exportRunning && collectionSimulateExport && collections.length > 0) {
            context.put("collection", collectionss);
            for (final String collection: collections) {
                exported += IndexDAO.getIndexDocumentByCollectionCount(user_id, collection.trim());
                Logger.info("exported (simulated) " + exported + " documents for user " + user_id + ", collection " + collection.trim());
            }
            context.put("simulated", exported);
            context.put("showSimulated", true);
            context.put("collection_export_disabled", false);
        }
        if (authorized && !exportRunning && collectionExport && collections.length > 0) {
            final String exportName = prefix + "-collection.jsonlist.gz";
            final IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, null);
                final OutputStream os = new GZIPOutputStream(new FileOutputStream(tempFile), 8192);
                context.put("collection", collectionss);
                for (final String collection: collections) {
                    exported += IndexDAO.exportIndexDocumentsByCollectionName(expected, user_id, collection.trim(), os).call();
                    Logger.info("exported " + exported + " documents for user " + user_id + ", collection " + collection.trim() + " to " + targetPath.toString());
                }
                os.close();
                Searchlab.io.write(targetPath, tempFile);
                tempFile.delete();
                context.put("showExported", true);
                context.put("exported", exported);
            } catch (final Exception e) {
                Logger.warn("failed to export");
            }
        }

        // do the export for domains
        final String domainss = serviceRequest.get("domain", "").trim();
        final String[] domains = domainss.isEmpty() ? new String[0]: domainss.split(",");
        if (authorized && !exportRunning && domainSimulateExport && domains.length > 0) {
            context.put("domain", domainss);
            for (final String domain: domains) {
                exported += IndexDAO.getIndexDocumentsByDomainNameCount(user_id, domain.trim());
                Logger.info("exported (simulated) " + exported + " documents for user " + user_id + ", domain " + domain.trim());
            }
            context.put("simulated", exported);
            context.put("showSimulated", true);
            context.put("domain_export_disabled", false);
        }
        if (authorized && !exportRunning && domainExport && domains.length > 0) {
            final String exportName = prefix + "-domain.jsonlist.gz";
            final IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, null);
                final OutputStream os = new GZIPOutputStream(new FileOutputStream(tempFile), 8192);
                context.put("domain", domainss);
                for (final String domain: domains) {
                    exported += IndexDAO.exportIndexDocumentsByDomainName(expected, user_id, domain.trim(), os).call();
                    Logger.info("exported " + exported + " documents for user " + user_id + ", domain " + domain.trim() + " to " + targetPath.toString());
                }
                os.close();
                Searchlab.io.write(targetPath, tempFile);
                tempFile.delete();
                context.put("showExported", true);
                context.put("exported", exported);
            } catch (final Exception e) {
                Logger.warn("failed to export");
            }
        }

        // do the export for queries
        final String query = serviceRequest.get("query", "").trim();
        if (authorized && !exportRunning && querySimulateExport && query.length() > 0) {
            context.put("query", query);
            exported += IndexDAO.getIndexDocumentsByQueryCount(user_id, query);
            Logger.info("exported (simulated) " + exported + " documents for user " + user_id + ", query " + query.trim());
            context.put("simulated", exported);
            context.put("showSimulated", true);
            context.put("query_export_disabled", false);
        }
        if (authorized && !exportRunning && queryExport && query.length() > 0) {
            final String exportName = prefix + "-query.jsonlist.gz";
            final IOPath targetPath = exportPath.append(exportName);
            try {
                final File tempFile = File.createTempFile(exportName, null);
                final OutputStream os = new GZIPOutputStream(new FileOutputStream(tempFile), 8192);
                context.put("query", query);
                exported = IndexDAO.exportIndexDocumentsByQuery(expected, user_id, query, os).call();
                os.close();
                Searchlab.io.write(targetPath, tempFile);
                tempFile.delete();
                Logger.info("exported " + exported + " documents for user " + user_id + ", query " + query.trim() + " to " + targetPath.toString());
                context.put("showExported", true);
                context.put("exported", exported);
            } catch (final Exception e) {
                Logger.warn("failed to export");
            }
        }

        // get running export data
        exportStatus = exportRunners.get(user_id);
        exportProgress = exportStatus == null ? null : exportStatus.car;
        exportFuture = exportStatus == null ? null : exportStatus.cdr;
        context.put("exportProgressDocs", exportProgress == null ? 0 : exportProgress.getProgress() == null ? 0 : exportProgress.getProgress().longValue()); // count of records
        context.put("exportTargetDocs", exportProgress == null ? 0 : exportProgress.getTarget() == null ? 0 : exportProgress.getTarget().longValue()); // count of records
        context.put("exportProgressPercent", exportProgress == null ? 0 : exportProgress.getPercent()); // percent
        context.put("exportRemainingSeconds", exportProgress == null ? 0 : exportProgress.getRemainingTime() / 1000); // milliseconds
        context.put("exportDocsPerMinute", exportProgress == null ? 0 : (int) exportProgress.getProgressPerSecond() * 60); // records per second
        exportRunning = exportProgress != null && exportFuture != null && !exportFuture.isDone();
        final boolean exportDone = exportProgress != null && exportFuture != null && exportFuture.isDone();
        if (exportRunning) {
            // Exported process is running, {{context.exportProgressDocs}} of {{context.exportTargetDocs}} documents, {{context.exportProgressPercent}}% so far. {{context.exportDocsPerMinute}} per minute. Remaining Time: {{context.exportRemainingSeconds}} seconds.
            context.put("exported",  exportProgress.getProgress() == null ? 0 : exportProgress.getProgress().longValue());
            context.put("showExporting", true);
        }
        if (exportDone) {
            // Exported {{context.exported}} Documents. Download from <a href="/data_warehouse/assets/?path=/export">Asset Export</a> folder.
            context.put("exported", exportProgress.getProgress() == null ? 0 : exportProgress.getProgress().longValue());
            context.put("showExported", true);
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
