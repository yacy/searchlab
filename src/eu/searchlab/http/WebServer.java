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
import eu.searchlab.storage.io.AbstractIO;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

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
        final RandomAccessFile raf = new RandomAccessFile(f,"r");
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

            if (exchange.isInIoThread()) {
                // dispatch to a worker thread, see
                // https://undertow.io/undertow-docs/undertow-docs-2.0.0/undertow-handler-guide.html#dispatch-code
                exchange.dispatch(this);
                return;
            }

            final String requestPath = exchange.getRequestPath();
            File f = new File(this.root, requestPath);
            if (f.isDirectory()) f = new File(f, "index.html");
            final String filePath = f.getAbsolutePath();
            final int p = filePath.lastIndexOf('.');
            final String ext = p < 0 ? "default" : filePath.substring(p + 1);
            final String mime = mimeTable.getProperty(ext, "application/octet-stream");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, mime);

            if (ext.equals("html") || ext.equals("json") || ext.equals("csv") || ext.equals("table")) {

                String post_message = "";
                // read query parameters
                if (exchange.getRequestMethod().equals(Methods.POST) || exchange.getRequestMethod().equals(Methods.PUT)) {
                    exchange.startBlocking();
                    post_message = new String(AbstractIO.readAll(exchange.getInputStream(), -1), StandardCharsets.UTF_8);
                }
                JSONObject post = new JSONObject(true);
                if (post_message.length() > 0) try {
                    post = new JSONObject(new JSONTokener(post_message));
                } catch (final JSONException e) {};

                final Map<String, Deque<String>> query = exchange.getQueryParameters();
                final JSONObject json = new JSONObject(true);
                json.put("POST", post);
                for (final Map.Entry<String, Deque<String>> entry: query.entrySet()) {
                    json.put(entry.getKey(), entry.getValue().getFirst());
                }

                // generate response
                String template = null;
                try {template = file2tring(f);} catch (final FileNotFoundException e) {}
                final String response = ServiceMap.propose(requestPath, template, json);
                if (response == null) {
                    exchange.setStatusCode(404).setReasonPhrase("not found").getResponseSender().send("");
                } else {
                    exchange.getResponseSender().send(response);
                }
            } else {
                exchange.getResponseSender().send(file2bytebuffer(f));
            }
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
