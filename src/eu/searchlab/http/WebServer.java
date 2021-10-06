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

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;

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

    private static HttpHandler requestmirror() {
        // HttpServerExchange
        return exchange -> {
            //exchange.startBlocking();
            //String post_message = readStream(exchange.getInputStream());
            final String post_message = "";
            JSONObject post = new JSONObject();
            if (post_message.length() > 0) try {
                post = new JSONObject(new JSONTokener(post_message));
            } catch (final JSONException e) {}

            final Map<String, Deque<String>> query = exchange.getQueryParameters();
            final JSONObject json = new JSONObject(true);
            json.put("POST", post);
            for (final Map.Entry<String, Deque<String>> entry: query.entrySet()) {
                json.put(entry.getKey(), entry.getValue().getFirst());
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(json.toString(2), StandardCharsets.UTF_8);
        };
    }

    private static HttpHandler fileserver(File root) {
        return exchange -> {
            File f = new File(root, exchange.getRequestPath());
            if (f.isDirectory()) f = new File(f, "index.html");
            final String path = f.getAbsolutePath();
            final int p = path.lastIndexOf('.');
            final String ext = p < 0 ? "default" : path.substring(p + 1);
            final String mime = mimeTable.getProperty(ext, "application/octet-stream");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, mime);
            if (ext.equals("html")) {
                String html = file2tring(f);
                final JSONObject json = new JSONObject();
                json.put("name", "Baeldung");

                final Handlebars handlebars = new Handlebars();
                final Context context = Context
                        .newBuilder(json)
                        .resolver(JSONObjectValueResolver.INSTANCE)
                        .build();
                final Template template = handlebars.compileInline(html);
                html = template.apply(context);

                exchange.getResponseSender().send(html);
            } else {
                exchange.getResponseSender().send(file2bytebuffer(f));
            }
        };
    }


    @Override
    public void run() {
        // Start webserver
        final Builder builder = Undertow.builder().addHttpListener(this.port, this.bind);
        final PathHandler ph = Handlers.path();
        ph.addExactPath("/api/mirror.json", requestmirror());
        ph.addPrefixPath("/", fileserver(new File(new File("ui"), "site")));
        builder.setHandler(ph);
        this.server = builder.build();
        this.server.start();
    }

}
