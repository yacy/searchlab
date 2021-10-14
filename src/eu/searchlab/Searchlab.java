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

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class Searchlab {


    private static final Logger log = LoggerFactory.getLogger(Searchlab.class);
    public static HazelcastInstance hzInstance;
    public static Map<String, String> hzMap;

    public static void main(final String[] args) {

        // prepare logging
        final ConsoleAppender console = new ConsoleAppender();
        console.setLayout(new PatternLayout("%d [%p|%c|%C{1}] %m%n"));
        console.setThreshold(Level.DEBUG);
        console.activateOptions();
        org.apache.log4j.Logger.getRootLogger().addAppender(console);

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
        if (assertionenabled) log.info("Asserts are enabled");

        // start Hazelcast
        final Config hzConfig = new Config().setClusterName("CubeServer");//.setNetworkConfig(new NetworkConfig())
        // hzConfig.setLiteMember(true); // lite members do not store data
        hzConfig.setProperty("hazelcast.initial.min.cluster.size", "1"); // lazily accepting that the cluster might have no redundancy
        hzConfig.setProperty( "hazelcast.logging.type", "log4j");
        final NetworkConfig network = hzConfig.getNetworkConfig();
        network.setPort(5701).setPortCount(20);
        network.setPortAutoIncrement(true);
        final JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);
        hzInstance = Hazelcast.newHazelcastInstance(hzConfig);
        final Set<Member> hzMembers = hzInstance.getCluster().getMembers();
        hzMap = hzInstance.getMap("data");

        // get connection to minio
        final String s3Url = System.getProperty("S3URL", "http://b00:9000");
        final String s3AccessKey = System.getProperty("S3ACCESSKEY", "admin");
        final String s3SecretKey = System.getProperty("S3SECRETKEY", "12345678");
        final String s3Bucket = System.getProperty("S3BUCKET", "searchlab");
        final String s3SettingsPath = System.getProperty("S3SETTINGSPATH", "data/settings");
        final GenericIO io = new S3IO(s3Url, s3AccessKey, s3SecretKey);
        final IOPath iop = new IOPath(s3Bucket, s3SettingsPath);

        // Start webserver
        final String port = System.getProperty("PORT", "8400");
        log.info("starting server at port " + port);
        WebServer webserver = new WebServer(Integer.parseInt(port), "0.0.0.0");
        webserver.run();
    }

}
