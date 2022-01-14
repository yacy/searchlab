/**
 *  WebServer
 *  Copyright 01.10.2021 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Deque;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.searchlab.http.services.MirrorService;
import eu.searchlab.http.services.TableGetService;
import eu.searchlab.http.services.TablePutService;
import eu.searchlab.http.services.YaCySearchService;
import eu.searchlab.storage.io.AbstractIO;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

public class WebServer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private static final Properties mimeTable = new Properties();

    static {
        try {
            mimeTable.load(new FileInputStream("conf/httpd.mime"));
        } catch (final IOException e) {
            log.error("failed loading defaults/httpd.mime", e);
        }
    }

    int port;
    String bind;
    Undertow server;

    public WebServer(int port, String bind) {
        this.port = port;
        this.bind = bind;

        // register services
        ServiceMap.register(new MirrorService());
        ServiceMap.register(new TableGetService());
        ServiceMap.register(new TablePutService());
        ServiceMap.register(new YaCySearchService());
    }

    private static String stream2string(InputStream is) {
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n")).trim();
    }

    private static String file2tring(File f) throws FileNotFoundException {
        return stream2string(new FileInputStream(f));
    }

    private static byte[] file2bytes(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    private static ByteBuffer file2bytebuffer(File f) throws IOException {
        if (! f.exists()) throw new FileNotFoundException("file " + f.toString() + " does not exist");
        if (! f.isFile()) throw new FileNotFoundException("path " + f.toString() + " is not a file");
        final RandomAccessFile raf = new RandomAccessFile(f, "r");
        final FileChannel fc = raf.getChannel();
        final long fileSize = fc.size();
        final ByteBuffer bb = ByteBuffer.allocate((int) fileSize);
        fc.read(bb);
        bb.flip();
        fc.close();
        raf.close();
        return bb;
    }

    private static class Fileserver implements HttpHandler {
        File root;
        public Fileserver(File root) {
            this.root = root;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {

            String method = exchange.getRequestMethod().toString();

            if (method.toLowerCase().equals("options")) {
                exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
                exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "Access-Control-Allow-Headers, Origin,Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
                exchange.getResponseSender().send("");
                return;
            }

            if (exchange.isInIoThread()) {
                // dispatch to a worker thread, see
                // https://undertow.io/undertow-docs/undertow-docs-2.0.0/undertow-handler-guide.html#dispatch-code
                exchange.dispatch(this);
                return;
            }

            final String requestPath = exchange.getRequestPath();

            // load requested file
            File f = new File(this.root, requestPath);
            if (f.isDirectory()) f = new File(f, "index.html");
            final String filePath = f.getAbsolutePath();
            final int p = filePath.lastIndexOf('.');
            final String ext = p < 0 ? "default" : filePath.substring(p + 1);
            final String mime = mimeTable.getProperty(ext, "application/octet-stream");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, mime);

            // switch case: files with templating / without
            if (isTemplatingFileType(ext)) {

                // read query parameters
                String post_message = "";
                if (exchange.getRequestMethod().equals(Methods.POST) || exchange.getRequestMethod().equals(Methods.PUT)) {
                    exchange.startBlocking();
                    post_message = new String(AbstractIO.readAll(exchange.getInputStream(), -1), StandardCharsets.UTF_8);
                }
                JSONObject json = new JSONObject(true);
                json.put("PATH", requestPath);
                if (post_message.length() > 0) try {
                    json = new JSONObject(new JSONTokener(post_message));
                } catch (final JSONException e) {};

                final Map<String, Deque<String>> query = exchange.getQueryParameters();
                for (final Map.Entry<String, Deque<String>> entry: query.entrySet()) {
                    json.put(entry.getKey(), entry.getValue().getFirst());
                }

                // generate response (handle servlets + handlebars)
                String html = null;
                try {html = file2tring(f);} catch (final FileNotFoundException e) {}
                html = ServiceMap.serviceDispatcher(requestPath, html, json);

                // apply server-side includes
                if (html != null) html = ssi(html);

                // send html to client
                if (html == null) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND).setReasonPhrase("not found").getResponseSender().send("");
                } else {
                    exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
                    exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "Access-Control-Allow-Headers, Origin,Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
                    exchange.getResponseSender().send(html);
                }
            } else {
                try {
                    ByteBuffer bb = file2bytebuffer(f);
                    exchange.getResponseSender().send(bb);
                } catch (IOException e) {
                    // to support the migration of the community forum from searchlab.eu to community.searchlab.eu we send of all unknown pages a redirect
                    exchange.setStatusCode(StatusCodes.PERMANENT_REDIRECT).setReasonPhrase("page moved");
                    exchange.getResponseHeaders().put(Headers.LOCATION, "https://community.searchlab.eu" + requestPath);
                    exchange.getResponseSender().send("");
                    exchange.endExchange();
                }
            }
        }

        private String recursiveRequest(String requestPath) throws Exception {

            // parse query parameters
            final JSONObject json = new JSONObject(true);
            json.put("PATH", requestPath);
            final int q = requestPath.indexOf('?');
            if (q >= 0) {
                final String qs = requestPath.substring(q + 1);
                requestPath = requestPath.substring(0, q);
                final String[] pm = qs.split("&");
                for (final String pms: pm) {
                    final int r = pms.indexOf('=');
                    if (r < 0) continue;
                    json.put(pms.substring(0, r), pms.substring(r + 1));
                }
            }

            // load requested file
            File f = new File(this.root, requestPath);
            if (f.isDirectory()) f = new File(f, "index.html");
            final String filePath = f.getAbsolutePath();
            final int p = filePath.lastIndexOf('.');
            final String ext = p < 0 ? "default" : filePath.substring(p + 1);

            // switch case: files with templating / without
            if (isTemplatingFileType(ext)) {

                // generate response (handle servlets + handlebars)
                String html = null;
                try {html = file2tring(f);} catch (final FileNotFoundException e) {}
                html = ServiceMap.serviceDispatcher(requestPath, html, json);

                // apply server-side includes
                if (html != null) html = ssi(html);

                return html;
            } else {
                return file2tring(f);
            }
        }

        boolean isTemplatingFileType(String ext) {
            return ext.equals("html") || ext.equals("json") || ext.equals("csv") || ext.equals("table") || ext.equals("tablei");
        }

        private String ssi(String html) throws Exception {
            // apply server-side includes
            /*
             * include a file in the same path as current path
             * <!--#include file="header.shtml" -->
             *
             * include a file relatively to server root
             * <!--#include virtual="script.pl" -->
             */
            int ssip = html.indexOf("<!--#include virtual=\"");
            int end;
            while (ssip >= 0 && (end = html.indexOf("-->", ssip + 24)) > 0 ) { // min length 24; <!--#include virtual="a"
                final int rightquote = html.indexOf("\"", ssip + 23);
                if (rightquote <= 0 || rightquote >= end) break;
                final String virtual = html.substring(ssip + 22, rightquote);
                final String include = recursiveRequest(virtual);
                if (include == null) {
                    html = html.substring(0, ssip) + html.substring(end + 3);
                    ssip = html.indexOf("<!--#include virtual=\"", ssip);
                } else {
                    html = html.substring(0, ssip) + include + html.substring(end + 3);
                    ssip = html.indexOf("<!--#include virtual=\"", ssip + include.length());
                }
            }
            return html;
        }

    }

    @Override
    public void run() {
        // Start webserver
        final Builder builder = Undertow.builder().addHttpListener(this.port, this.bind);
        final PathHandler ph = Handlers.path();
        ph.addPrefixPath("/", new Fileserver(new File(new File("ui"), "site")));
        builder.setHandler(ph);
        this.server = builder.build();
        this.server.start();
    }

}
