/**
 *  Searchlab
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

package eu.searchlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import eu.searchlab.aaaaa.AccountingTS;
import eu.searchlab.aaaaa.AuthorizationTS;
import eu.searchlab.aaaaa.UserDB;
import eu.searchlab.audit.AuditScheduler;
import eu.searchlab.audit.UserAudit;
import eu.searchlab.http.WebServer;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.io.MinioS3IO;
import eu.searchlab.storage.queues.QueueFactory;
import eu.searchlab.storage.queues.RabbitQueueFactory;
import eu.searchlab.tools.Logger;
import net.yacy.grid.io.index.ElasticsearchClient;
import net.yacy.grid.io.index.GridIndex;

public class Searchlab {

    // hazelcast
    //public static HazelcastInstance hzInstance;
    public static Map<String, String> hzMap;

    // IO and MinIO
    public static GenericIO io;
    public static IOPath dataIOp, statusIOp, aaaaaIOp, auditUserRequestsIOp, auditUserVisitorsIOp;

    // elastic client
    public static ElasticsearchClient ec;
    public static String crawlerIndexName, crawlstartIndexName, crawlstartTypeName;

    // Messaging client
    public static QueueFactory queues;

    // Audit
    public static UserAudit userAudit;
    public static AuditScheduler auditScheduler;

    // AAAAA
    public static UserDB userDB;
    public static AccountingTS accounting;
    public static AuthorizationTS authorization;
    public static String github_client_id, github_client_secret, patreon_client_id, patreon_client_secret;

    // Ready
    private static AtomicInteger readyCounter = new AtomicInteger(0);
    public static boolean ready = false;

    public static String getHost(final String address) {
        final String hp = t(address, '@', address);
        return h(hp, ':', hp);
    }
    public static int getPort(final String address, final String defaultPort) {
        return Integer.parseInt(t(t(address, '@', address), ':', defaultPort));
    }
    public static String getUser(final String address, final String defaultUser) {
        return h(h(address, '@', ""), ':', defaultUser);
    }
    public static String getPassword(final String address, final String defaultPassword) {
        return t(h(address, '@', ""), ':', defaultPassword);
    }

    private static String h(final String a, final char s, final String d) {
        final int p = a.indexOf(s);
        return p < 0 ? d : a.substring(0,  p);
    }

    private static String t(final String a, final char s, final String d) {
        final int p = a.indexOf(s);
        return p < 0 ? d : a.substring(p + 1);
    }

    public static void readConfig() {
        final File conf_dir = FileSystems.getDefault().getPath("conf").toFile();
        final Properties properties = new Properties();
        final File f = new File(conf_dir, "config.properties");
        if (f.exists()) try {
            properties.load(new FileInputStream(f));
        } catch (final IOException e) {
            Logger.error(e);
        }

        final Map<String, String> sysenv = System.getenv();
        for (final Object keyo: properties.keySet()) {
            final String key = (String) keyo;
            String value = properties.getProperty(key);
            if (System.getProperty(key) != null) continue; // we do not overwrite system properties that are already set

            // option to overwrite the config with environment variables.
            // Because a '.' (dot) is not allowed in system environments
            // the dot can be replaced by "_" (underscore), i.e. like:
            // grid_broker_address="anonymous:yacy@127.0.0.1:5672" java -jar build/libs/yacy_grid_mcp-0.0.1-SNAPSHOT.jar
            final String envkey0 = key.replace('.', '_');
            final String envkey1 = "SEARCHLAB_" + envkey0.toUpperCase();
            final String envval0 = sysenv.get(envkey0);
            final String envval1 = sysenv.get(envkey1);
            if (envval0 != null) {
                Logger.info("OVERWRITING CONFIG '" + key + "' with environment value '" + envval0 + "'");
                value = envval0;
            }
            if (envval1 != null) {
                Logger.info("OVERWRITING CONFIG '" + key + "' with environment value '" + envval1 + "'");
                value = envval1;
            }

            // set a system property with the new value
            System.setProperty(key, value);
        }
    }

    public static void main(final String[] args) {

        // prepare configuration
        readConfig();
        System.setProperty("java.awt.headless", "true");

        // check assertion status
        //ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
        boolean assertionenabled = false;
        assert (assertionenabled = true) == true; // compare to true to remove warning: "Possible accidental assignement"
        if (assertionenabled) Logger.info("Asserts are enabled");

        // initialize data services (in the background)
        /*
        new Thread() {
            @Override
            public void run() {

                // start Hazelcast
                final Config hzConfig = new Config().setClusterName("Searchlab");//.setNetworkConfig(new NetworkConfig())
                // hzConfig.setLiteMember(true); // lite members do not store data
                hzConfig.setProperty("hazelcast.initial.min.cluster.size", "1"); // lazily accepting that the cluster might have no redundancy
                hzConfig.setProperty("hazelcast.logging.type", "jdk");
                final NetworkConfig network = hzConfig.getNetworkConfig();
                network.setPort(5701).setPortCount(20);
                network.setPortAutoIncrement(true);
                final JoinConfig join = network.getJoin();
                join.getMulticastConfig().setEnabled(false);
                hzInstance = Hazelcast.newHazelcastInstance(hzConfig);
                final Set<Member> hzMembers = hzInstance.getCluster().getMembers();
                hzMap = hzInstance.getMap("data");
            }
        }.start();
        */

        // oauth config generated at https://github.com/settings/developers
        // these setting should not be anywhere in a confif and only passed by start parameters
        // requires also a callback url here like https://searchlab.eu/en/aaaaa/github/callback or https://searchlab.eu/en/aaaaa/patreon/callback
        github_client_id      = System.getProperty("github.client.id", "");
        github_client_secret  = System.getProperty("github.client.secret", "");
        patreon_client_id     = System.getProperty("patreon.client.id", "");
        patreon_client_secret = System.getProperty("patreon.client.secret", "");

        new Thread() {
            @Override
            public void run() {
                // get connection to elasticsearch
                final String[] elasticsearchAddress = System.getProperty("grid.elasticsearch.address", "127.0.0.1:9300").split(",");
                final String elasticsearchClusterName = System.getProperty("grid.elasticsearch.clusterName", "elasticsearch"); // default is elasticsearch but "" will ignore it
                crawlerIndexName = System.getProperty("grid.elasticsearch.indexName.crawler", GridIndex.DEFAULT_INDEXNAME_CRAWLER);
                crawlstartIndexName = System.getProperty("grid.elasticsearch.indexName.crawlstart", GridIndex.DEFAULT_INDEXNAME_CRAWLSTART);
                crawlstartTypeName = System.getProperty("grid.elasticsearch.typeName", GridIndex.DEFAULT_TYPENAME);
                try {
                    ec = new ElasticsearchClient(elasticsearchAddress, elasticsearchClusterName);
                    Logger.info("Connected elasticsearch at " + elasticsearchAddress[0]);
                    if (readyCounter.incrementAndGet() >= 3) ready = true;
                } catch (final IOException e) {
                    Logger.warn("No connection to elasticsearch");
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                try {
                    // get connection to RabbitMQ
                    final String brokerAddress = System.getProperty("grid.broker.address", "");
                    queues = new RabbitQueueFactory(getHost(brokerAddress), getPort(brokerAddress, "-1"), getUser(brokerAddress, "anonymous"), getPassword(brokerAddress, "yacy"), true, 0);
                    Logger.info("Connected Broker at " + getHost(brokerAddress));
                    if (readyCounter.incrementAndGet() >= 3) ready = true;
                } catch (final IOException e) {
                    Logger.error(e);
                }
            }
        }.start();

        // connect to minio without background thread because we need this before we initialize the server for audit
        final String s3address = System.getProperty("grid.s3.address", "admin:12345678@yacygrid.b00:9000");
        final String s3DataPath = System.getProperty("grid.s3.datapath", "/data");
        final String bucket_endpoint = getHost(s3address);
        final int p = bucket_endpoint.indexOf('.');
        assert p > 0;
        final String bucket = bucket_endpoint.substring(0, p);
        final String endpoint = bucket_endpoint.substring(p + 1);
        io = new MinioS3IO("http://" + endpoint + ":" + getPort(s3address, "9000"), getUser(s3address, "admin"), getPassword(s3address, "12345678"));
        dataIOp = new IOPath(bucket, s3DataPath);
        statusIOp = dataIOp.append("status");
        aaaaaIOp  = dataIOp.append("aaaaa");
        auditUserRequestsIOp = statusIOp.append("audit_user_requests.csv");
        auditUserVisitorsIOp = statusIOp.append("audit_user_visitors.csv");
        Logger.info("Connected S3 at " + s3address);

        // initialize audit and aaaaa
        // if this fails, we cannot start the searchlab!
        try {
            userDB = new UserDB(io, io, aaaaaIOp);
            userAudit = new UserAudit(io, auditUserRequestsIOp, auditUserVisitorsIOp);
            accounting = new AccountingTS(io, aaaaaIOp);
            authorization = new AuthorizationTS(io, aaaaaIOp);
        } catch (final IOException e) {
            Logger.error("could not load data from IO", e);
            System.exit(-1);
        }
        if (readyCounter.incrementAndGet() >= 3) ready = true;
        final AuditScheduler auditScheduler = new AuditScheduler(60000, userAudit);
        auditScheduler.start();

        // Start webserver
        final String port = System.getProperty("port", "8400");
        Logger.info("starting server at port " + port);
        final WebServer webserver = new WebServer(Integer.parseInt(port), "0.0.0.0", userAudit);

        // give positive feedback
        Logger.info("Searchlab started at port " + port);

        // prepare shutdown signal
        boolean pidkillfileCreated = false;
        File killfile = null;
        // we use two files: one kill file which can be used to stop the process and one pid file which exists until the process runs
        // in case that the deletion of the kill file does not cause a termination, still a "fuser -k" on the pid file can be used to
        // terminate the process.
        try {
            final File data_dir = FileSystems.getDefault().getPath("data").toFile();
            if (!data_dir.exists()) data_dir.mkdirs();
            final File pidfile = new File(data_dir, "searchlab-" + port + ".pid");
            killfile = new File(data_dir, "searchlab-" + port + ".kill");
            if (pidfile.exists()) pidfile.delete();
            if (killfile.exists()) killfile.delete();
            if (!pidfile.exists()) try {
                pidfile.createNewFile();
                if (pidfile.exists()) {pidfile.deleteOnExit(); pidkillfileCreated = true;}
            } catch (final IOException e) {
                Logger.info("pid file " + pidfile.getAbsolutePath() + " creation failed: " + e.getMessage());
            }
            if (!killfile.exists()) try {
                killfile.createNewFile();
                if (killfile.exists()) killfile.deleteOnExit(); else pidkillfileCreated = false;
            } catch (final IOException e) {
                Logger.info("kill file " + killfile.getAbsolutePath() + " creation failed: " + e.getMessage());
                pidkillfileCreated = false;
            }
        } catch (final Exception e) {}

        // wait for shutdown signal (kill on process)
        if (pidkillfileCreated) {
            // we can control this by deletion of the kill file
            Logger.info("to stop this process, delete kill file " + killfile.getAbsolutePath());
            while (!webserver.server.getWorker().isTerminated() && killfile.exists()) {
                try {Thread.sleep(1000);} catch (final InterruptedException e) {}
            }
            webserver.server.stop();
            Logger.info("server kill termination requested");
            System.exit(1); // not soo nixe because it goes around the CronBox, but that is stateless so just exit here.
        } else {
            // something with the pid file creation did not work; fail-over to normal operation waiting for a kill command
            try {
                webserver.server.getWorker().awaitTermination();
            } catch (final InterruptedException e) {
                Logger.error(e);
            }
        }
        Logger.info("server nominal termination");
    }

}
