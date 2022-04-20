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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import eu.searchlab.http.WebServer;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.io.S3IO;
import eu.searchlab.tools.Logger;
import net.yacy.grid.io.index.ElasticsearchClient;

public class Searchlab {

    public static HazelcastInstance hzInstance;
    public static Map<String, String> hzMap;
    public static GenericIO io;
    public static IOPath settingsIop;
    public static ElasticsearchClient ec;

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

    public static void main(final String[] args) {

        // prepare configuration
        final Properties sysprops = System.getProperties(); // system properties
        System.getenv().forEach((k,v) -> {
            if (k.startsWith("SEARCHLAB_")) sysprops.put(k.substring(10).replace('_', '.'), v);
        }); // add also environment variables
        System.setProperty("java.awt.headless", "true");

        // check assertion status
        //ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
        boolean assertionenabled = false;
        assert (assertionenabled = true) == true; // compare to true to remove warning: "Possible accidental assignement"
        if (assertionenabled) Logger.info("Asserts are enabled");


        // initialize data services (in the background)
        new Thread() {
            @Override
            public void run() {
                // get connection to minio
                final String s3address = System.getProperty("grid.s3.address", "admin:12345678@searchlab.b00:9000");
                final String s3SettingsPath = System.getProperty("grid.s3.path", "data/settings");
                final String bucket_endpoint = getHost(s3address);
                final int p = bucket_endpoint.indexOf('.');
                assert p > 0;
                final String bucket = bucket_endpoint.substring(0, p);
                final String endpoint = bucket_endpoint.substring(p + 1);
                io = new S3IO("http://" + endpoint + ":" + getPort(s3address, "9000"), getUser(s3address, "admin"), getPassword(s3address, "12345678"));
                settingsIop = new IOPath(bucket, s3SettingsPath);

            }
        }.start();
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
        new Thread() {
            @Override
            public void run() {
                // get connection to elasticsearch
                final String[] elasticsearchAddress = System.getProperty("grid.elasticsearch.address", "127.0.0.1:9300").split(",");
                final String elasticsearchClusterName = System.getProperty("grid.elasticsearch.clusterName", "elasticsearch"); // default is elasticsearch but "" will ignore it
                ec = new ElasticsearchClient(elasticsearchAddress, elasticsearchClusterName);
            }
        }.start();


        // Start webserver
        final String port = System.getProperty("PORT", "8400");
        Logger.info("starting server at port " + port);
        final WebServer webserver = new WebServer(Integer.parseInt(port), "0.0.0.0");

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
