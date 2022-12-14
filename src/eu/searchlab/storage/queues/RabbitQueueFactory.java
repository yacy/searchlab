/**
 *  RabbitQueueFactory
 *  Copyright 14.01.2017 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.queues;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import eu.searchlab.tools.Logger;

/**
 * to monitor the rabbitMQ queue, open the admin console at
 * http://127.0.0.1:15672/
 * and log with admin/admin
 */
public class RabbitQueueFactory implements QueueFactory {

    public final static String TARGET_LIMIT_MESSAGE = "message not delivered - target limitation";

    public final static int DEFAULT_PORT = 5672;
    protected static String DEFAULT_EXCHANGE = "";
    public static String PROTOCOL_PREFIX = "amqp://";


    private final String server, username, password;
    private final int managementPort, nodePort;
    private final ConnectionFactory connectionFactory;
    protected Connection connection;
    private Map<String, Queue> queues;
    protected final AtomicBoolean lazy;
    protected final AtomicInteger queueLimit;

    /**
     * create a queue factory for a rabbitMQ message server
     * @param server the host name of the rabbitMQ server
     * @param port a port for the access to the rabbitMQ server. If given -1, then the default port will be used
     * @param username
     * @param password
     * @param lazy
     * @param queueLimit maximum number of entries for the queue, 0 = unlimited
     * @throws IOException
     */
    public RabbitQueueFactory(final String server, final int managementPort, final int nodePort, final String username, final String password, final boolean lazy, final int queueLimit) throws IOException {
        this.server = server;
        this.managementPort = managementPort;
        this.nodePort = nodePort;
        this.username = username;
        this.password = password;
        this.lazy = new AtomicBoolean(lazy);
        this.queueLimit = new AtomicInteger(queueLimit);
        this.connection = null;
        this.queues = new ConcurrentHashMap<>();
        this.connectionFactory = new ConnectionFactory();
        this.connectionFactory.setAutomaticRecoveryEnabled(false); // false -> SIC! - when leaving this 'true', old connections will be reused even if a old connection is closed and replace with a new one, resulting is "already closed exception".
        this.connectionFactory.setHost(this.server);
        if (this.nodePort > 0) this.connectionFactory.setPort(this.nodePort);
        if (this.username != null && this.username.length() > 0) this.connectionFactory.setUsername(this.username);
        if (this.password != null && this.password.length() > 0) this.connectionFactory.setPassword(this.password);
    }

    private Connection getConnection() throws IOException {
        if (this.connection != null && this.connection.isOpen()) return this.connection;
        try {
            this.connection = this.connectionFactory.newConnection();
            //Map<String, Object> map = this.connection.getServerProperties();
            if (!this.connection.isOpen()) throw new IOException("no connection");
            return this.connection;
        } catch (final TimeoutException e) {
            throw new IOException(e.getMessage());
        }
    }

    protected Channel getChannel() throws IOException {
        getConnection();
        final Channel channel = this.connection.createChannel();
        if (!channel.isOpen()) throw new IOException("no channel");
        return channel;
    }

    @Override
    public Queue getQueue(final String queueName) throws IOException {
        if (this.queues == null) return null;
        Queue queue = this.queues.get(queueName);
        if (queue != null) return queue;
        synchronized (this) {
            queue = this.queues.get(queueName);
            if (queue != null) return queue;
            queue = new RabbitQueue(this, queueName);
            this.queues.put(queueName, queue);
            return queue;
        }
    }

    @Override
    public Map<String, QueueStats> getAllQueues() throws IOException {
        // make url, i.e. http://localhost:15672/api/queues
        String url = this.server.startsWith("http") ? this.server : "http://" + this.server;
        if (this.managementPort >= 0) url = url + ":" + this.managementPort;
        if (!url.endsWith("/")) url = url + "/";
        url = url + "api/queues";

        // load content from api
        final String content = getContent(url, this.username, this.password);

        // parse json and evaluate content
        final Map<String, QueueStats> queues = new LinkedHashMap<>();
        try {
            final JSONArray queueList = new JSONArray(new JSONTokener(content));
            for (int i = 0; i < queueList.length(); i++) {
                final JSONObject q = queueList.getJSONObject(i);
                final String name = q.optString("name");
                if (name != null && name.length() > 0) {
                    String idle_since = q.optString("idle_since", null); // null or like "2022-12-01 12:29:28"
                    // XXXXTODOXXX
                    final QueueStats stats = new QueueStats()
                            .setTotal(q.optLong("messages", 0))
                            .setReady(q.optLong("messages_ready", 0))
                            .setUnacknowledged(q.optLong("messages_unacknowledged", 0))
                            .setIdletime(0);
                    queues.put(name, stats);
                }
            }
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
        return queues;
    }

    @Override
    public Map<String, Queue> getOpenQueues() throws IOException {
        return this.queues;
    }

    @Override
    public QueueStats getAggregatedStats() throws IOException {
        // make url, i.e. http://localhost:15672/api/overview
        String url = this.server.startsWith("http") ? this.server : "http://" + this.server;
        if (this.managementPort >= 0) url = url + ":" + this.managementPort;
        if (!url.endsWith("/")) url = url + "/";
        url = url + "api/overview";

        // load content from api
        final String content = getContent(url, this.username, this.password);

        // parse json and evaluate content
        final QueueStats result = new QueueStats();
        try {
            final JSONObject overview = new JSONObject(new JSONTokener(content));
            final JSONObject queue_totals = overview.getJSONObject("queue_totals");
            result.setTotal(queue_totals.optLong("messages", 0));
            result.setReady(queue_totals.optLong("messages_ready", 0));
            result.setUnacknowledged(queue_totals.optLong("messages_unacknowledged", 0));
            result.setIdletime(0);
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
        return result;
    }

    private static String getContent(String url, String username, String password) throws IOException {
        // load content from api
        final HttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
        final HttpUriRequest request = RequestBuilder.get()
                .setUri(url)
                //.setHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .setHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build(); //Authorization: Basic Z3Vlc3Q6Z3Vlc3Q=
        final HttpResponse response = httpclient.execute(request);
        final HttpEntity entity = response.getEntity();
        final String content = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
        return content;
    }

    @Override
    public void close() {
        //for (final Queue<byte[]> q: this.queues.values()) {try {q.close();} catch (final IOException e) {}}
        //
        // ATTENTION:
        // We do not close the queues because RabbitMQ tries to recover old queues even if a new connection is initiated.
        // Am exception will occur in case that we make a new connection and use old closed queues, stating that the queue was already closed.
        // There must be a static status inside the RabbitMQ client which remembers all queues, even if they have been closed.
        //
        try {this.connection.close();} catch (final IOException e) {}
        this.queues.clear();
        this.queues = null;
        this.connection = null;
    }

    public static void main(final String[] args) {
        RabbitQueueFactory qc;
        try {
            qc = new RabbitQueueFactory("127.0.0.1", 15672, -1, "guest", "guest", true, 0);
            qc.getQueue("test").send("Hello World".getBytes());
            System.out.println(new String(qc.getQueue("test2").receive(60000, true).getPayload()));
            qc.close();
        } catch (final IOException e) {
            Logger.warn(e);
        }
    }
}
