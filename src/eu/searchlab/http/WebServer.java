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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;

import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.audit.UserAudit;
import eu.searchlab.http.services.AppsService;
import eu.searchlab.http.services.AssetDirectoryService;
import eu.searchlab.http.services.AssetDownloadService;
import eu.searchlab.http.services.CrawlStartService;
import eu.searchlab.http.services.IDGeneratorService;
import eu.searchlab.http.services.IDValidationService;
import eu.searchlab.http.services.IndexService;
import eu.searchlab.http.services.IndexStatusService;
import eu.searchlab.http.services.LogService;
import eu.searchlab.http.services.MirrorService;
import eu.searchlab.http.services.QueueStatusService;
import eu.searchlab.http.services.ReadyService;
import eu.searchlab.http.services.SuggestService;
import eu.searchlab.http.services.TableGetService;
import eu.searchlab.http.services.TablePutService;
import eu.searchlab.http.services.ThreaddumpService;
import eu.searchlab.http.services.YaCySearchService;
import eu.searchlab.storage.io.AbstractIO;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

public class WebServer {

    private static final Properties mimeTable = new Properties();
    public final static String COOKIE_NAME = "searchlab-user";

    private final static byte[] SSI_MARKER = "<!--#".getBytes();

    public static File UI_PATH, APPS_PATH, HTDOCS_PATH;

    static {
        try {
            mimeTable.load(new FileInputStream("conf/httpd.mime"));
            UI_PATH = new File(new File("ui"), "site");
            APPS_PATH = new File(new File(new File(".").getCanonicalFile().getParentFile(), "searchlab_apps"), "htdocs").getCanonicalFile();
            HTDOCS_PATH = new File("htdocs");
        } catch (final IOException e) {
            Logger.error("failed loading defaults/httpd.mime", e);
        }
    }

    private final int port;
    private final String bind;
    public final Undertow server;
    private final UserAudit audit;

    public WebServer(final int port, final String bind, final UserAudit audit) {
        this.port = port;
        this.bind = bind;
        this.audit = audit;

        // register services
        ServiceMap.register(new MirrorService());
        ServiceMap.register(new TableGetService());
        ServiceMap.register(new TablePutService());
        ServiceMap.register(new YaCySearchService());
        ServiceMap.register(new SuggestService());
        ServiceMap.register(new AppsService());
        ServiceMap.register(new IndexService());
        ServiceMap.register(new IDGeneratorService());
        ServiceMap.register(new IDValidationService());
        ServiceMap.register(new CrawlStartService());
        ServiceMap.register(new ThreaddumpService());
        ServiceMap.register(new ReadyService());
        ServiceMap.register(new QueueStatusService());
        ServiceMap.register(new LogService());
        ServiceMap.register(new IndexStatusService());
        ServiceMap.register(new AssetDirectoryService());
        ServiceMap.register(new AssetDownloadService());

        // Start webserver
        final PathHandler ph = Handlers.path();
        ph.addPrefixPath("/", new Fileserver(new File[] {UI_PATH, APPS_PATH, HTDOCS_PATH}));
        final HttpHandler encodingHandler = new EncodingHandler.Builder().build(null).wrap(ph);
        final Builder builder = Undertow.builder().addHttpListener(this.port, this.bind);
        builder.setHandler(encodingHandler);
        this.server = builder.build();
        this.server.start();
    }

    private class Fileserver implements HttpHandler {

        private final File[] rootSet;

        public Fileserver(final File[] root) {
            this.rootSet = root;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {

            final String client = "-";
            final String method = exchange.getRequestMethod().toString();

            // read client address
            final SocketAddress address = exchange.getConnection().getPeerAddress();
            String ip = address.toString();
            if (address instanceof InetSocketAddress) {
                ip = ((InetSocketAddress) address).getAddress().getHostAddress();
            }
            int p = ip.indexOf("/");
            if (p > 0) ip = ip.substring(p + 1);

            final HeaderMap requestHeader = exchange.getRequestHeaders();
            if (requestHeader.contains("X-Real-IP")) ip = requestHeader.getFirst("X-Real-IP"); // X-Forwarded-For ??

            // pseudonymization: de-identification of the ip, implements article 4(5) of the GDPR
            p = ip.lastIndexOf('.');
            if (p >= 0) ip = ip.substring(0, p) + ".1"; // we use a "1" here to make this a proper ip

            // process header
            final HeaderMap responseHeader = exchange.getResponseHeaders();
            responseHeader.put(new HttpString("Access-Control-Allow-Origin"), "*");
            responseHeader.put(new HttpString("Access-Control-Allow-Methods"), "POST, GET, OPTIONS");
            responseHeader.put(new HttpString("Access-Control-Allow-Headers"), "Access-Control-Allow-Headers, Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Methods, Access-Control-Request-Headers");

            if (method.toLowerCase().equals("options")) {
                exchange.getResponseSender().send("");
                return;
            }

            if (exchange.isInIoThread()) {
                // dispatch to a worker thread, see
                // https://undertow.io/undertow-docs/undertow-docs-2.0.0/undertow-handler-guide.html#dispatch-code
                exchange.dispatch(this);
                return;
            }

            // read query parameters; this must be done first because it produces the 'cleaned' requestPath without get attributes (after '?')
            final ServiceRequest serviceRequest = getQueryParams(exchange);

            final String user = serviceRequest.getUser();
            final String path = serviceRequest.getPath();
            final String query = serviceRequest.getQuery();

            // we force using of a user/language path
            if (user == null || user.length() == 0) {
                exchange.setStatusCode(StatusCodes.TEMPORARY_REDIRECT).setReasonPhrase("page moved");
                exchange.getResponseHeaders().put(Headers.LOCATION, "/en" + path + (query.length() > 0 ? "?" + query : ""));
                exchange.getResponseSender().send("");
                log(ip, client, user, method, path, StatusCodes.TEMPORARY_REDIRECT, 0);
                return;
            }

            WebServer.this.audit.event(user, ip);

            if (user.length() != 2) {
                // add a canonical and noindex tag to the response header
                // see:
                // https://developers.google.com/search/docs/advanced/crawling/consolidate-duplicate-urls?hl=de#rel-canonical-header-method
                exchange.getResponseHeaders().put(new HttpString("X-Robots-Tag"), "noindex");
                exchange.getResponseHeaders().put(new HttpString("Link"), "<https://searchlab.eu/en" + path + ">; rel=\"canonical\"");
            }

            // before we consider a servlet operation, find a requested file in the path because that would be an input for handlebars operation later
            final File f = findFile(path);
            p = path.lastIndexOf('.');
            final String ext = p < 0 ? "html" : path.substring(p + 1);
            final String mime = mimeTable.getProperty(ext, "application/octet-stream");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, mime);

            if (!isTemplatingFileType(ext) && f != null) {
                // just serve the file
                try {
                    final ByteBuffer bb = file2bytebuffer(f);
                    final long d = f.lastModified();
                    exchange.getResponseHeaders().put(Headers.DATE, DateParser.formatRFC1123(new Date(d))); // like a proper file server
                    exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "public, max-age=" + (System.currentTimeMillis() - d + 600)); // 10 minutes cache, for production: increase
                    exchange.getResponseHeaders().remove(Headers.EXPIRES); // MUST NOT appear in headers to enable caching with cache-control
                    exchange.getResponseSender().send(bb);
                    log(ip, client, user, method, path, StatusCodes.OK, f.length());
                } catch (final IOException e) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND).setReasonPhrase("not found");
                    exchange.getResponseSender().send("");
                    log(ip, client, user, method, path, StatusCodes.NOT_FOUND, 0);
                }
                return;
            }

            try {
                // generate response (handle servlets + handlebars)
                final ServiceResponse serviceResponse = processPost(serviceRequest);
                final byte[] b = serviceResponse.toByteArray(false);
                final Cookie cookie = serviceResponse.getCookie();
                if (cookie != null) {
                	exchange.setResponseCookie(cookie);
                }

                // send html to client
                if (b == null) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND).setReasonPhrase("not found").getResponseSender().send("");
                    log(ip, client, user, method, path, StatusCodes.NOT_FOUND, 0);
                } else {
                    if ("application/json".equals(mime) && endsWith(b, "]);".getBytes())) {
                        // JSONP patch
                        exchange.getResponseHeaders().remove(Headers.CONTENT_TYPE);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/javascript");
                    }
                    if (query.endsWith(".jsonlist") || query.endsWith(".gz")) {
                        exchange.getResponseHeaders().remove(Headers.CONTENT_TYPE);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
                        int q = 0;
                        q = query.lastIndexOf('/');
                        if (q > 0) {
                            final String filename = query.substring(q + 1);
                            exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
                        }
                    }
                    exchange.getResponseHeaders().put(Headers.DATE, DateParser.formatRFC1123(new Date())); // current time because it is generated right now
                    exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-cache");
                    exchange.getResponseSender().send(ByteBuffer.wrap(b));
                    log(ip, client, user, method, path, StatusCodes.OK, b.length);
                }
            } catch (final IOException e) {
                // to support the migration of the community forum from searchlab.eu to community.searchlab.eu we send of all unknown pages a redirect
                if (e instanceof FileNotFoundException) {
                    final String redirect = "https://community.searchlab.eu" + path + (query.length() > 0 ? "?" + query : "");
                    exchange.setStatusCode(StatusCodes.PERMANENT_REDIRECT).setReasonPhrase("page moved");
                    exchange.getResponseHeaders().put(Headers.LOCATION, redirect);
                    exchange.getResponseSender().send("");
                    log(ip, client, user, method, path, StatusCodes.PERMANENT_REDIRECT, 0);
                } else {
                    exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE).setReasonPhrase(e.getMessage());
                    exchange.getResponseSender().send("");
                    log(ip, client, user, method, path, StatusCodes.SERVICE_UNAVAILABLE, 0);
                }
            }
        }

        private final void log(final String ip, final String client, final String user, final String method, final String path, final int response, final long size) {
            Logger.info(ip + " " + client + " " + user + " " + method + " " + path + " " + response + " " + size);
        }

        /**
         * processing a request with parameters
         * @param post the post request with special object "PATH" which containes the request path
         * @return full html or any kind of response that should be transferred with http status code 200
         * @throws IOException in case this request cannot be fullfilled.
         */
        private ServiceResponse processPost(final ServiceRequest serviceRequest) throws IOException {

        	final String path = serviceRequest.getPath();

            // load requested file
            final File f = findFile(path);

            // generate response (handle servlets + handlebars)
            byte[] b = null;
            if (f != null) b = file2bytes(f); // throws FileNotFoundException which must be handled outside
            final Service service = ServiceMap.getService(path);

            // in case that html and service is defined by a static page and a json service is defined, we use handlebars to template the html
            ServiceResponse serviceResponse = null;
            if (service == null) {
            	serviceResponse = new ServiceResponse(b);
            } else {
            	serviceResponse = service.serve(serviceRequest);
                if (b != null && serviceResponse.getType() == Service.Type.OBJECT) {
                    final JSONObject json = serviceResponse.getObject();
                    final Handlebars handlebars = new Handlebars();
                    final Context context = Context
                            .newBuilder(json)
                            .resolver(JSONObjectValueResolver.INSTANCE)
                            .build();
                    try {
                        final Template template = handlebars.compileInline(new String(b, StandardCharsets.UTF_8));
                        serviceResponse.setValue(template.apply(context));
                    } catch (final HandlebarsException e) {
                        Logger.error("Handlebars Error", e);
                        throw new IOException(e.getMessage());
                    }
                } else if (b != null && serviceResponse.getType() == Service.Type.ARRAY) {
                    final JSONArray json = serviceResponse.getArray();
                    final Handlebars handlebars = new Handlebars();
                    final Context context = Context
                            .newBuilder(json)
                            .resolver(JSONObjectValueResolver.INSTANCE)
                            .build();
                    try {
                        final Template template = handlebars.compileInline(new String(b, StandardCharsets.UTF_8));
                        serviceResponse.setValue(template.apply(context));
                    } catch (final HandlebarsException e) {
                        Logger.error("Handlebars Error", e);
                        throw new IOException(e.getMessage());
                    }
                } else {
                    // the response is defined only by the service, we ignore b[]
                	serviceResponse = ServiceMap.serviceDispatcher(service, path, serviceRequest);
                }
            }

            // check finally if the resulting byte array was defined
            // (either by a file or a service)
            b = serviceResponse.toByteArray(false);
            if (b == null && f == null) {
                throw new FileNotFoundException("not found:" + path);
            }

            // apply server-side includes
            if (b != null) b = ssi(serviceRequest, b);
            serviceResponse.setValue(b);
            return serviceResponse;
        }

        private byte[] ssi(final ServiceRequest serviceRequest, final byte[] b) throws IOException {
            // apply server-side includes
            /*
             * include a file in the same path as current path
             * <!--#include file="header.shtml" -->
             *
             * include a file relatively to server root
             * <!--#include virtual="script.pl" -->
             */
            if (indexOf(b, SSI_MARKER, 0) < 0) return b;
            String html = new String(b, StandardCharsets.UTF_8);
            int ssip = html.indexOf("<!--#include virtual=\"");
            int end;
            while (ssip >= 0 && (end = html.indexOf("-->", ssip + 24)) > 0 ) { // min length 24; <!--#include virtual="a"
                final int rightquote = html.indexOf("\"", ssip + 23);
                if (rightquote <= 0 || rightquote >= end) break;
                final String virtual = html.substring(ssip + 22, rightquote);
                final ServiceRequest serviceRequest0 = getQueryParams(serviceRequest.getUser(), virtual);
                final ServiceResponse ibbr = processPost(serviceRequest0);
                final byte[] ibb = ibbr.toByteArray(false);
                final String include = ibb == null ? null : new String(ibb, StandardCharsets.UTF_8);
                if (include == null) {
                    html = html.substring(0, ssip) + html.substring(end + 3);
                    ssip = html.indexOf("<!--#include virtual=\"", ssip);
                } else {
                    html = html.substring(0, ssip) + include + html.substring(end + 3);
                    ssip = html.indexOf("<!--#include virtual=\"", ssip + include.length());
                }
            }
            ssip = html.indexOf("<!--#echo var=\"");//  <!--#echo var="CANONICAL_TAG" -->
            while (ssip >= 0 && (end = html.indexOf("-->", ssip + 17)) > 0 ) { // min length 17; <!--#echo var="a"
                final int rightquote = html.indexOf("\"", ssip + 16);
                if (rightquote <= 0 || rightquote >= end) break;
                final String var = html.substring(ssip + 15, rightquote);
                if ("CANONICAL_TAG".equals(var)) {
                    final String include = "<link rel=\"canonical\" href=\"" + "https://searchlab.eu/en" + serviceRequest.getPath() + "\">";
                    html = html.substring(0, ssip) + include + html.substring(end + 3);
                    ssip = html.indexOf("<!--#echo var=\"", ssip + include.length());
                } else {
                    html = html.substring(0, ssip) + html.substring(end + 3);
                    ssip = html.indexOf("<!--#echo var=\"", ssip);
                }
            }
            return html.getBytes(StandardCharsets.UTF_8);
        }


        /**
         * find any file that is inside one of the given root paths
         * @param requestPath
         * @return a file if it exists or null if it does not exist
         */
        private File findFile(final String requestPath) {
            for (final File g: this.rootSet) {
                File f = new File(g, requestPath);
                if (!f.exists()) continue;
                if (f.isDirectory()) f = new File(f, "index.html");
                return f;
            }
            return null;
        }

        private String file2String(final File f) throws IOException {
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

        private ByteBuffer file2bytebuffer(final File f) throws IOException {
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

        private byte[] file2bytes(final File f) throws IOException {
            if (! f.exists()) throw new FileNotFoundException("file " + f.toString() + " does not exist");
            if (! f.isFile()) throw new FileNotFoundException("path " + f.toString() + " is not a file");
            final FileInputStream fis = new FileInputStream(f);
            final long fileSize = f.length();
            final byte[] b = new byte[(int) fileSize];
            fis.read(b);
            fis.close();
            return b;
        }

        private ServiceRequest getQueryParams(final HttpServerExchange exchange) throws IOException {
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

            final Map<String, Deque<String>> queryParams = exchange.getQueryParameters();
            for (final Map.Entry<String, Deque<String>> entry: queryParams.entrySet()) {
                try {json.put(entry.getKey(), entry.getValue().getFirst());} catch (final JSONException e) {}
            }
            String path = exchange.getRequestPath();
            final int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            final String user = getUserPrefix(path);
            if (user != null) path = path.substring(user.length() + 1);
            final String query = exchange.getQueryString();
            try {json.put("USER", user);} catch (final JSONException e) {} // TODO: delete
            try {json.put("PATH", path);} catch (final JSONException e) {} // TODO: delete
            try {json.put("QUERY", query);} catch (final JSONException e) {} // TODO: delete
            final Cookie cookie = exchange.getRequestCookie(COOKIE_NAME);
            return new ServiceRequest(json, user, path, query, cookie);
        }

        private ServiceRequest getQueryParams(final String knownuser, String path)  {
            // parse query parameters
            final JSONObject json = new JSONObject(true);
            final int q = path.indexOf('?');
            if (q >= 0) {
                final String qs = path.substring(q + 1);
                path = path.substring(0, q);
                try {json.put("PATH", path);} catch (final JSONException e) {}
                final String[] pm = qs.split("&");
                for (final String pms: pm) {
                    final int r = pms.indexOf('=');
                    if (r < 0) continue;
                    try {json.put(pms.substring(0, r), pms.substring(r + 1));} catch (final JSONException e) {}
                }
            }
            String user = getUserPrefix(path);
            if (user == null) {
                user = knownuser;
            } else {
                path = path.substring(user.length() + 1);
            }
            try {json.put("USER", user);} catch (final JSONException e) {} // TODO: delete
            try {json.put("PATH", path);} catch (final JSONException e) {} // TODO: delete
            try {json.put("QUERY", "");} catch (final JSONException e) {} // TODO: delete
            return new ServiceRequest(json, user, path, "", null);
        }

        /**
         * check if path starts with a user id.
         * @param path the full path of the request, starting with "/"
         * @return the user id, or null if the path does not start with the user id
         */
        private String getUserPrefix(final String path) {
            assert path.charAt(0) == '/';
            if (path.length() < 2) return null;
            final int p = path.indexOf('/', 1);
            if (p < 0) return null;
            final String prefix = path.substring(1, p);
            if (prefix.length() == 2) {
                return prefix.equals("en") ? "en": null;
            }
            if (Authentication.isValid(prefix)) return prefix;
            return null;
        }

        private boolean isTemplatingFileType(final String ext) {
            return ext.equals("html") || ext.equals("json") || ext.equals("csv") || ext.equals("table") || ext.equals("tablei");
        }
    }

    public static int indexOf(final byte[] source, final byte[] query, int fromIndex) {

        if (fromIndex >= source.length) return (query.length == 0 ? source.length : -1);
        if (fromIndex < 0) fromIndex = 0;
        if (query.length == 0) return fromIndex;

        final byte first = query[0];
        final int max = source.length - query.length;

        for (int i = fromIndex; i <= max; i++) {
            if (source[i] != first) {
                while (++i <= max && source[i] != first);
            }
            if (i <= max) {
                int j = i + 1;
                final int end = j + query.length - 1;
                for (int k = 1; j < end && source[j] == query[k]; j++, k++);
                if (j == end) return i;
            }
        }
        return -1;
    }

    public boolean endsWith(final byte[] source, final byte[] suffix) {
        return startsWith(source, suffix, source.length - suffix.length);
    }

    public static boolean startsWith(final byte[] source, final byte[] prefix, final int toffset) {
        final byte ta[] = source;
        int to = toffset;
        final byte pa[] = prefix;
        int po = 0;
        int pc = prefix.length;
        // Note: toffset might be near -1>>>1.
        if ((toffset < 0) || (toffset > source.length - pc)) {
            return false;
        }
        while (--pc >= 0) {
            if (ta[to++] != pa[po++]) {
                return false;
            }
        }
        return true;
    }

    public void stop() {
        this.server.stop();
    }
}
