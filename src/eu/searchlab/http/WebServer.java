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
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;

import eu.searchlab.http.services.MirrorService;
import eu.searchlab.http.services.TableGetService;
import eu.searchlab.http.services.TablePutService;
import eu.searchlab.http.services.YaCySearchService;
import eu.searchlab.storage.io.AbstractIO;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
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

    private static final Properties mimeTable = new Properties();

    static {
        try {
            mimeTable.load(new FileInputStream("conf/httpd.mime"));
        } catch (final IOException e) {
            Logger.error("failed loading defaults/httpd.mime", e);
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
    
    private static class Fileserver implements HttpHandler {
        private final File[] root;
        public Fileserver(File[] root) {
            this.root = root;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {

            final String method = exchange.getRequestMethod().toString();

            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Methods"), "POST, GET, OPTIONS");
            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "Access-Control-Allow-Headers, Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Methods, Access-Control-Request-Headers");

            if (method.toLowerCase().equals("options")) {
                exchange.getResponseSender().send("");
                exchange.endExchange();
                return;
            }

            if (exchange.isInIoThread()) {
                // dispatch to a worker thread, see
                // https://undertow.io/undertow-docs/undertow-docs-2.0.0/undertow-handler-guide.html#dispatch-code
                exchange.dispatch(this);
                return;
            }

            // read query parameters; this must be done first because it produces the 'cleaned' requestPath without get attributes (after '?')
            final JSONObject post = getQueryParams(exchange);
            final String path = post.optString("PATH", "/"); // this looks like "/js/jquery.min.js", a root path looks like "/"
            final String user = post.optString("USER", null);
            
            Logger.info("WebServer request: USER=" + user + ", PARH=" + path);
            
            // we force using of a user/language path
            if (user == null) {
                exchange.setStatusCode(StatusCodes.PERMANENT_REDIRECT).setReasonPhrase("page moved");
                exchange.getResponseHeaders().put(Headers.LOCATION, "/en" + path);
                exchange.getResponseSender().send("");
                exchange.endExchange();
                return;
            }
            
            if (user.length() != 2) {
                // add a noindex flag to http response header
                exchange.getResponseHeaders().put(new HttpString("X-Robots-Tag"), "noindex");
                exchange.getResponseHeaders().put(new HttpString("Link"), "</en" + path + ">; rel=\"canonical\""); // see https://developers.google.com/search/docs/advanced/crawling/consolidate-duplicate-urls#rel-canonical-header-method
            }
            
            // before we consider a servlet operation, find a requested file in the path because that would be an input for handlebars operation later
            final File f = findFile(path);
            final int p = path.lastIndexOf('.');
            final String ext = p < 0 ? "html" : path.substring(p + 1);
            final String mime = mimeTable.getProperty(ext, "application/octet-stream");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, mime);

            if (!isTemplatingFileType(ext) && f != null) {
                // just serve the file
                final ByteBuffer bb = file2bytebuffer(f);
                final long d = f.lastModified();
                exchange.getResponseHeaders().put(Headers.DATE, DateParser.formatRFC1123(new Date(d))); // like a proper file server
                exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "public, max-age=" + (System.currentTimeMillis() - d + 600)); // 10 minutes cache, for production: increase
                exchange.getResponseHeaders().remove(Headers.EXPIRES); // MUST NOT appear in headers to enable caching with cache-control
                exchange.getResponseSender().send(bb);
                exchange.endExchange();
                return;
            }
            
            try {
                // generate response (handle servlets + handlebars)
                final String html = processPost(post);

                // send html to client
                if (html == null) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND).setReasonPhrase("not found").getResponseSender().send("");
                } else {
                    exchange.getResponseSender().send(html);
                }
                exchange.endExchange();
            } catch (final IOException e) {
                // to support the migration of the community forum from searchlab.eu to community.searchlab.eu we send of all unknown pages a redirect
                if (e instanceof FileNotFoundException) {
                    exchange.setStatusCode(StatusCodes.PERMANENT_REDIRECT).setReasonPhrase("page moved");
                    exchange.getResponseHeaders().put(Headers.LOCATION, "https://community.searchlab.eu" + path);
                } else {
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR).setReasonPhrase(e.getMessage());
                }
                exchange.getResponseSender().send("");
                exchange.endExchange();
            }
        }

        /**
         * processing a request with parameters
         * @param post the post request with special object "PATH" which containes the request path
         * @return full html or any kind of response that should be transferred with http status code 200
         * @throws IOException in case this request cannot be fullfilled.
         */
        private String processPost(final JSONObject post) throws IOException {

            final String path = post.optString("PATH", "/");
            final String knownuser = post.optString("USER", null);
            
            // load requested file
            final File f = findFile(path);

            // generate response (handle servlets + handlebars)
            String html = null;
            if (f != null) html = file2String(f); // throws FileNotFoundException which must be handled outside
            final Service service = ServiceMap.getService(path);
            
            // in case that html and service is defined by a static page and a json service is defined, we use handlebars to template the html
            if (html != null && service != null && service.getType() == Service.Type.OBJECT) {
                final JSONObject json = service.serveObject(post);
                final Handlebars handlebars = new Handlebars();
                final Context context = Context
                        .newBuilder(json)
                        .resolver(JSONObjectValueResolver.INSTANCE)
                        .build();
                try {
                    final Template template = handlebars.compileInline(html);
                    html = template.apply(context);
                } catch (final HandlebarsException e) {
                    Logger.error("Handlebars Error in \n" + html, e);
                    throw new IOException(e.getMessage());
                }
            } else if (service != null) {
                // the response is defined only by the service
                html = ServiceMap.serviceDispatcher(service, path, post);
            }
            if (html == null && f == null) {
                throw new FileNotFoundException("not found:" + path);
            }
            
            // apply server-side includes
            if (html != null) html = ssi(knownuser, html);

            return html;
        }

        private String ssi(String knownuser, String html) throws IOException {
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
                final JSONObject post = getQueryParams(knownuser, virtual);
                final String include = processPost(post);
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

        private File findFile(String requestPath) {
            for (final File g: this.root) {
                File f = new File(g, requestPath);
                if (!f.exists()) continue;
                if (f.isDirectory()) f = new File(f, "index.html");
                return f;
            }
            return null;
        }

        private static String file2String(File f) throws IOException {
            if (! f.exists()) throw new FileNotFoundException("file " + f.toString() + " does not exist");
            if (! f.isFile()) throw new FileNotFoundException("path " + f.toString() + " is not a file");
            final FileInputStream fis = new FileInputStream(f);
            final InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            final BufferedReader br = new BufferedReader(isr);
            final String html = br.lines().collect(Collectors.joining("\n")).trim();
            br.close();
            isr.close();
            fis.close();
            return html;
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
        
        private JSONObject getQueryParams(HttpServerExchange exchange) throws IOException {
            // read query parameters
            String post_message = "";
            if (exchange.getRequestMethod().equals(Methods.POST) || exchange.getRequestMethod().equals(Methods.PUT)) {
                exchange.startBlocking();
                post_message = new String(AbstractIO.readAll(exchange.getInputStream(), -1), StandardCharsets.UTF_8);
            }
            JSONObject json = new JSONObject(true);
            if (post_message.length() > 0) try {
                json = new JSONObject(new JSONTokener(post_message));
            } catch (final JSONException e) {};

            final Map<String, Deque<String>> query = exchange.getQueryParameters();
            for (final Map.Entry<String, Deque<String>> entry: query.entrySet()) {
                try {json.put(entry.getKey(), entry.getValue().getFirst());} catch (final JSONException e) {}
            }
            String requestPath = exchange.getRequestPath();
            final int q = requestPath.indexOf('?');
            if (q >= 0) requestPath = requestPath.substring(0, q);
            final String user = getUserPrefix(requestPath);
            if (user != null) requestPath = requestPath.substring(user.length() + 1);
            try {json.put("PATH", requestPath);} catch (final JSONException e) {}
            try {json.put("USER", user);} catch (final JSONException e) {}
            return json;
        }
        
        private JSONObject getQueryParams(String knownuser, String requestPath)  {
            // parse query parameters
            final JSONObject json = new JSONObject(true);
            final int q = requestPath.indexOf('?');
            if (q >= 0) {
                final String qs = requestPath.substring(q + 1);
                requestPath = requestPath.substring(0, q);
                try {json.put("PATH", requestPath);} catch (final JSONException e) {}
                final String[] pm = qs.split("&");
                for (final String pms: pm) {
                    final int r = pms.indexOf('=');
                    if (r < 0) continue;
                    try {json.put(pms.substring(0, r), pms.substring(r + 1));} catch (final JSONException e) {}
                }
            }
            String user = getUserPrefix(requestPath);
            if (user == null) {
                user = knownuser;
            } else {
                requestPath = requestPath.substring(user.length() + 1);
            }
            try {json.put("PATH", requestPath);} catch (final JSONException e) {}
            try {json.put("USER", user);} catch (final JSONException e) {}
            return json;
        }
        
        /**
         * check if path starts with a user id.
         * @param path the full path of the request, starting with "/"
         * @return the user id, or null if the path does not start with the user id
         */
        private String getUserPrefix(String path) {
            assert path.charAt(0) == '/';
            if (path.length() < 2) return null;
            final int p = path.indexOf('/', 1);
            if (p < 0) return null;
            final String prefix = path.substring(1, p);
            if (prefix.length() == 2) {
                return prefix.equals("en") ? "en": null;
            }
            if (prefix.length() == 9) {
                // must consist of only digits and every digit must appear only once;
                final Set<Integer> c = new HashSet<>(); for (int i = 1; i <= 9; i++) c.add(i);
                for (int i = 0; i < 9; i++) {
                    if (!c.remove(prefix.charAt(i) - 48)) return null;
                }
                return prefix;
            }
            return null;
        }

        private boolean isTemplatingFileType(String ext) {
            return ext.equals("html") || ext.equals("json") || ext.equals("csv") || ext.equals("table") || ext.equals("tablei");
        }

    }

    @Override
    public void run() {
        // Start webserver
        final Builder builder = Undertow.builder().addHttpListener(this.port, this.bind);
        final PathHandler ph = Handlers.path();
        ph.addPrefixPath("/", new Fileserver(new File[] {
                new File(new File("ui"), "site"),
                new File("htdocs")
        }));
        builder.setHandler(ph);
        this.server = builder.build();
        this.server.start();
    }

}
