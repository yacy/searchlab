/**
 *  Domains
 *  Copyright 2007 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany, @orbiterlab
 *  First released 23.7.2007 at http://yacy.net
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

package eu.searchlab.tools;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.UncheckedTimeoutException;

public class Domains {

    public  static final String LOCALHOST = "localhost"; // replace with IPv6 0:0:0:0:0:0:0:1 ?
    private static       String LOCALHOST_NAME = LOCALHOST; // this will be replaced with the actual name of the local host

    private static final String PRESENT = "";
    private static final String LOCALHOST_IPv4_PATTERN = "(127\\..*)";
    private static final String LOCALHOST_IPv6_PATTERN = "((\\[?fe80\\:.*)|(\\[?0\\:0\\:0\\:0\\:0\\:0\\:0\\:1.*)|(\\[?\\:\\:1))(/.*|%.*|\\z)";
    private static final String INTRANET_IPv4_PATTERN = "(10\\..*)|(172\\.(1[6-9]|2[0-9]|3[0-1])\\..*)|(169\\.254\\..*)|(192\\.168\\..*)";
    private static final String INTRANET_IPv6_PATTERN = "(\\[?(fc|fd).*\\:.*)";
    private static final Pattern LOCALHOST_PATTERNS = Pattern.compile("(localhost)|" + LOCALHOST_IPv4_PATTERN + "|" + LOCALHOST_IPv6_PATTERN, Pattern.CASE_INSENSITIVE);
    private static final Pattern INTRANET_PATTERNS = Pattern.compile(LOCALHOST_PATTERNS.pattern() + "|" + INTRANET_IPv4_PATTERN + "|" + INTRANET_IPv6_PATTERN, Pattern.CASE_INSENSITIVE);

    private static final int MAX_NAME_CACHE_HIT_SIZE = 10000;
    private static final int MAX_NAME_CACHE_MISS_SIZE = 1000;
    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors() * 2;

    // a dns cache
    private static final ARC<String, InetAddress> NAME_CACHE_HIT = new ConcurrentARC<>(MAX_NAME_CACHE_HIT_SIZE, CONCURRENCY_LEVEL);
    private static final ARC<String, String> NAME_CACHE_MISS = new ConcurrentARC<>(MAX_NAME_CACHE_MISS_SIZE, CONCURRENCY_LEVEL);
    private static final ConcurrentHashMap<String, Object> LOOKUP_SYNC = new ConcurrentHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors() * 2);
    private static       List<Pattern> nameCacheNoCachingPatterns = Collections.synchronizedList(new LinkedList<Pattern>());
    public static long cacheHit_Hit = 0, cacheHit_Miss = 0, cacheHit_Insert = 0; // for statistics only; do not write
    public static long cacheMiss_Hit = 0, cacheMiss_Miss = 0, cacheMiss_Insert = 0; // for statistics only; do not write

    private static Set<InetAddress> myHostAddresses = new HashSet<InetAddress>();
    private static Set<InetAddress> localHostAddresses = new HashSet<InetAddress>(); // subset of myHostAddresses
    private static Set<InetAddress> publicIPv4HostAddresses = new HashSet<InetAddress>(); // subset of myHostAddresses
    private static Set<InetAddress> publicIPv6HostAddresses = new HashSet<InetAddress>(); // subset of myHostAddresses
    private static Set<String> localHostNames = new HashSet<String>(); // subset of myHostNames
    static {
        localHostNames.add(LOCALHOST);
        try {
            InetAddress localHostAddress = InetAddress.getLocalHost();
            if (localHostAddress != null) myHostAddresses.add(localHostAddress);
        } catch (final UnknownHostException e) {}
        try {
            final InetAddress[] moreAddresses = InetAddress.getAllByName(LOCALHOST_NAME);
            if (moreAddresses != null) myHostAddresses.addAll(Arrays.asList(moreAddresses));
        } catch (final UnknownHostException e) {}

        // to get the local host name, a dns lookup is necessary.
        // if such a lookup blocks, it can cause that the static initiatializer does not finish fast
        // therefore we start the host name lookup as concurrent thread
        // meanwhile the host name is "127.0.0.1" which is not completely wrong
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Domains: init");
                // try to get local addresses from interfaces
                try {
                    final Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                    while (nis.hasMoreElements()) {
                        final NetworkInterface ni = nis.nextElement();
                        final Enumeration<InetAddress> addrs = ni.getInetAddresses();
                        while (addrs.hasMoreElements()) {
                            final InetAddress addr = addrs.nextElement();
                            if (addr != null) myHostAddresses.add(addr);
                        }
                    }
                } catch (final SocketException e) {
                }

                // now look up the host name
                try {
                    LOCALHOST_NAME = getHostName(InetAddress.getLocalHost());
                } catch (final UnknownHostException e) {}

                // after the host name was resolved, we try to look up more local addresses
                // using the host name:
                try {
                    final InetAddress[] moreAddresses = InetAddress.getAllByName(LOCALHOST_NAME);
                    if (moreAddresses != null) myHostAddresses.addAll(Arrays.asList(moreAddresses));
                } catch (final UnknownHostException e) {
                }

                // fill a cache of local host names
                for (final InetAddress a: myHostAddresses) {
                    String hostaddressP = chopZoneID(a.getHostAddress());
                    Set<String> hns = new LinkedHashSet<>();
                    // generate alternative representations of IPv6 addresses which are needed to check access on the interface (i.e. localhost check)
                    if (hostaddressP.indexOf("::") < 0) {
                        hns.add(hostaddressP.replaceFirst(":0:0:0:0:0:0:", "::"));
                        hns.add(hostaddressP.replaceFirst(":0:0:0:0:0:", "::"));
                        hns.add(hostaddressP.replaceFirst(":0:0:0:0:", "::"));
                        hns.add(hostaddressP.replaceFirst(":0:0:0:", "::"));
                        hns.add(hostaddressP.replaceFirst(":0:0:", "::"));
                        hns.add(hostaddressP.replaceFirst(":0:", "::"));
                    }
                    hns.add(hostaddressP);
                    final String hostname = getHostName(a);
                    for (String hostaddress: hns) {
                        if (hostaddress.contains("::0:") || hostaddress.contains(":0::")) continue; // not common (but possible); we skip that
                        // we write the local tests into variables to be able to debug these values
                        boolean isAnyLocalAddress  = a.isAnyLocalAddress();
                        boolean isLinkLocalAddress = a.isLinkLocalAddress(); // true i.e. for localhost/fe80:0:0:0:0:0:0:1%1, myhost.local/fe80:0:0:0:223:dfff:fedf:30ce%7
                        boolean isLoopbackAddress  = a.isLoopbackAddress();  // true i.e. for localhost/0:0:0:0:0:0:0:1, localhost/127.0.0.1
                        boolean isSiteLocalAddress = a.isSiteLocalAddress(); // true i.e. for myhost.local/192.168.1.33
                        if (isAnyLocalAddress || isLinkLocalAddress || isLoopbackAddress || isSiteLocalAddress) {
                            localHostAddresses.add(a);
                            if (hostname != null) {localHostNames.add(chopZoneID(hostname)); localHostNames.add(chopZoneID(hostaddress));}
                        } else {
                            if (a instanceof Inet4Address) {
                                publicIPv4HostAddresses.add(a);
                            } else {
                                publicIPv6HostAddresses.add(a);
                            }
                        }
                    }
                }
            }
        }.start();
    }

    private static Map<String, Integer> TLDID = new ConcurrentHashMap<String, Integer>(32);
    //private static HashMap<String, String> TLDName = new HashMap<String, String>();

    private static boolean noLocalCheck = false;


    /**
     * the isLocal check can be switched off to gain a better crawling speed.
     * however, if the check is switched off, then ALL urls are considered as local
     * this will create url-hashes for global domains which do not fit in environments
     * where the isLocal switch is not de-activated. Please handle this method with great care
     * Bad usage will make peers inoperable.
     * @param v
     */
    public static void setNoLocalCheck(final boolean v) {
        noLocalCheck = v;
    }

    /**
    * Does an DNS-Check to resolve a hostname to an IP.
    *
    * @param host Hostname of the host in demand.
    * @return String with the ip. null, if the host could not be resolved.
    */
    public static InetAddress dnsResolveFromCache(String host) throws UnknownHostException {
        if ((host == null) || host.isEmpty()) return null;
        host = host.toLowerCase().trim();

        // trying to resolve host by doing a name cache lookup
        InetAddress ip = NAME_CACHE_HIT.get(host);
        if (ip != null) {
            cacheHit_Hit++;
            return ip;
        }
        cacheHit_Miss++;

        if (NAME_CACHE_MISS.containsKey(host)) {
            cacheMiss_Hit++;
            return null;
        }
        cacheMiss_Miss++;
        throw new UnknownHostException("host not in cache");
    }

    public static void setNoCachingPatterns(final String patternList) throws PatternSyntaxException {
        nameCacheNoCachingPatterns = makePatterns(patternList);
    }

    public static List<Pattern> makePatterns(final String patternList) throws PatternSyntaxException {
        final String[] entries = (patternList != null) ? CommonPattern.COMMA.split(patternList) : new String[0];
        final List<Pattern> patterns = new ArrayList<Pattern>(entries.length);
        for (final String entry : entries) {
            patterns.add(Pattern.compile(entry.trim()));
        }
        return patterns;
    }

    public static boolean matchesList(final String obj, final List<Pattern> patterns) {
        for (final Pattern nextPattern: patterns) {
            if (nextPattern.matcher(obj).matches()) return true;
        }
        return false;
    }

    public static String getHostName(final InetAddress i) {
        final Collection<String> hosts = NAME_CACHE_HIT.getKeys(i);
        if (!hosts.isEmpty()) return hosts.iterator().next();
        final String host = i.getHostName();
        NAME_CACHE_HIT.insertIfAbsent(host, i);
        cacheHit_Insert++;
        return host;
    }

    /**
     * in case that the host name was resolved using a time-out request
     * it can be nice to push that information to the name cache
     * @param i the inet address
     * @param host the known host name
     */
    public static void setHostName(final InetAddress i, final String host) {
        NAME_CACHE_HIT.insertIfAbsent(host, i);
        cacheHit_Insert++;
    }

    /**
     * strip off any parts of an url, address string (containing host/ip:port) or raw IPs/Hosts,
     * considering that the host may also be an (IPv4) IP or a IPv6 IP in brackets.
     * @param target
     * @return a host name or IP string
     */
    public static String stripToHostName(String target) {
        // normalize
        if (target == null || target.isEmpty()) return null;
        target = target.toLowerCase().trim(); // we can lowercase this because host names are case-insensitive

        // extract the address (host:port) part (applies if this is an url)
        int p = target.indexOf("://");
        if (p > 0) target = target.substring(p + 3);
        p = target.indexOf('/');
        if (p > 0) target = target.substring(0, p);

        // IPv4 / host heuristics
        p = target.lastIndexOf(':');
        if ( p < 0 ) {
            p = target.lastIndexOf('%');
            if (p > 0) target = target.substring(0, p);
            return target;
        }

        // the ':' at pos p may be either a port divider or a part of an IPv6 address
        if ( p > target.lastIndexOf(']')) { // if after ] it's a port divider (not IPv6 part)
            target = target.substring(0, p );
        }

        // may be IPv4 or IPv6, we chop off brackets if exist
        if (target.charAt(0) == '[') target = target.substring(1);
        if (target.charAt(target.length() - 1) == ']') target = target.substring(0, target.length() - 1);
        p = target.lastIndexOf('%');
        if (p > 0) target = target.substring(0, p);
        return target;
    }

    /**
     * Reads the port out of a url string (the url must start with a protocol
     * like http:// to return correct default port). If no port is given, default
     * ports are returned. On missing protocol, port=80 is assumed.
     * @param target url (must start with protocol)
     * @return port number
     */
    public static int stripToPort(String target) {
        int port = 80; // default port

        // normalize
        if (target == null || target.isEmpty()) return port;
        target = target.toLowerCase().trim(); // we can lowercase this because host names are case-insensitive

        // extract the address (host:port) part (applies if this is an url)
        int p = target.indexOf("://");
        if (p > 0) {
            String protocol = target.substring(0, p);
            target = target.substring(p + 3);
            if ("https".equals(protocol)) port = 443;
            if ("s3".equals(protocol)) port = 9000;
            if ("ftp".equals(protocol)) port = 21;
            if ("smb".equals(protocol)) port = 445;
        }
        p = target.indexOf('/');
        if (p > 0) target = target.substring(0, p);

        // IPv4 / host heuristics
        p = target.lastIndexOf(':');
        if ( p < 0 ) return port;

        // the ':' must be a port divider or part of ipv6
        if (target.lastIndexOf(']') < p) {
            port = Integer.parseInt(target.substring(p + 1));
        }
        return port;
    }

    /**
     * resolve a host address using a local DNS cache and a DNS lookup if necessary
     * @param clienthost
     * @return the hosts InetAddress or null if the address cannot be resolved
     */
    public static InetAddress dnsResolve(final String host0) {
        // consider to call stripToHostName() before calling this
        if (host0 == null || host0.isEmpty()) return null;
        final String host = host0.toLowerCase().trim();

        if (host0.endsWith(".yacyh")) {
            // that should not happen here
            return null;
        }

        // try to resolve host by doing a name cache lookup
        InetAddress ip = NAME_CACHE_HIT.get(host);
        if (ip != null) {
            //System.out.println("DNSLOOKUP-CACHE-HIT(CONC) " + host);
            cacheHit_Hit++;
            return ip;
        }
        cacheHit_Miss++;
        if (NAME_CACHE_MISS.containsKey(host)) {
            //System.out.println("DNSLOOKUP-CACHE-MISS(CONC) " + host);
            cacheMiss_Hit++;
            return null;
        }
        cacheMiss_Miss++;

        // call dnsResolveNetBased(host) using concurrency to interrupt execution in case of a time-out
        final Object sync_obj_new = new Object();
        Object sync_obj = LOOKUP_SYNC.putIfAbsent(host, sync_obj_new);
        if (sync_obj == null) sync_obj = sync_obj_new;
        synchronized (sync_obj) {
            // now look again if the host is in the cache where it may be meanwhile because of the synchronization

            ip = NAME_CACHE_HIT.get(host);
            if (ip != null) {
                //System.out.println("DNSLOOKUP-CACHE-HIT(SYNC) " + host);
                LOOKUP_SYNC.remove(host);
                cacheHit_Hit++;
                return ip;
            }
            cacheHit_Miss++;
            if (NAME_CACHE_MISS.containsKey(host)) {
                //System.out.println("DNSLOOKUP-CACHE-MISS(SYNC) " + host);
                LOOKUP_SYNC.remove(host);
                cacheMiss_Hit++;
                return null;
            }
            cacheMiss_Miss++;

            // do the dns lookup on the dns server
            //if (!matchesList(host, nameCacheNoCachingPatterns)) System.out.println("DNSLOOKUP " + host);
            try {
                //final long t = System.currentTimeMillis();
                String oldName = Thread.currentThread().getName();
                Thread.currentThread().setName("Domains: DNS resolve of '" + host + "'"); // thread dump show which host is resolved
                if (InetAddresses.isInetAddress(host)) {
                    try {
                        ip = InetAddresses.forString(host);
                    } catch (final IllegalArgumentException e) {
                        ip = null;
                    }
                }
                Thread.currentThread().setName(oldName);
                if (ip == null) try {
                    ip = InetAddress.getByName(host);
                    //ip = TimeoutRequest.getByName(host, 1000); // this makes the DNS request to backbone
                } catch (final UncheckedTimeoutException e) {
                    // in case of a timeout - maybe cause of massive requests - do not fill NAME_CACHE_MISS
                    LOOKUP_SYNC.remove(host);
                    return null;
                }
                //.out.println("DNSLOOKUP-*LOOKUP* " + host + ", time = " + (System.currentTimeMillis() - t) + "ms");
            } catch (final Throwable e) {
                // add new entries
                NAME_CACHE_MISS.insertIfAbsent(host, PRESENT);
                cacheMiss_Insert++;
                LOOKUP_SYNC.remove(host);
                return null;
            }

            if (ip == null) {
                // add new entries
                NAME_CACHE_MISS.insertIfAbsent(host, PRESENT);
                cacheMiss_Insert++;
                LOOKUP_SYNC.remove(host);
                return null;
            }

            if (!ip.isLoopbackAddress() && !matchesList(host, nameCacheNoCachingPatterns)) {
                // add new ip cache entries
                NAME_CACHE_HIT.insertIfAbsent(host, ip);
                cacheHit_Insert++;
            }
            LOOKUP_SYNC.remove(host);
            return ip;
        }
    }

    public static void clear() {
        NAME_CACHE_HIT.clear();
        NAME_CACHE_MISS.clear();
    }

    /**
    * Returns the number of entries in the nameCacheHit map
    *
    * @return int The number of entries in the nameCacheHit map
    */
    public static int nameCacheHitSize() {
        return NAME_CACHE_HIT.size();
    }

    public static int nameCacheMissSize() {
        return NAME_CACHE_MISS.size();
    }

    public static int nameCacheNoCachingPatternsSize() {
        return nameCacheNoCachingPatterns.size();
    }

    /**
     * myPublicLocalIP() returns the IP of this host which is reachable in the public network under this address
     * This is deprecated since it should be possible that the host is reachable with more than one IP
     * That is particularly the case if the host supports IPv4 and IPv6.
     * Please use myPublicIPv4() or (preferred) myPublicIPv6() instead.
     * @return
     */
    @Deprecated
    public static InetAddress myPublicLocalIP() {
        // for backward compatibility, we try to select a IPv4 address here.
        // future methods should use myPublicIPs() and prefer IPv6
        if (publicIPv4HostAddresses.size() > 0) return publicIPv4HostAddresses.iterator().next();
        if (publicIPv6HostAddresses.size() > 0) return publicIPv6HostAddresses.iterator().next();
        return null;
    }

    public static Set<String> myPublicIPs() {
        // use a LinkedHashSet to get an order of IPs where the IPv4 are preferred to get a better compatibility with older implementations
        Set<String> h = new LinkedHashSet<>(publicIPv4HostAddresses.size() + publicIPv6HostAddresses.size());
        for (InetAddress i: publicIPv4HostAddresses) h.add(i.getHostAddress());
        for (InetAddress i: publicIPv6HostAddresses) h.add(i.getHostAddress());
        return h;
    }

    /**
     * Get all IPv4 addresses which are assigned to the local host but are public IP addresses.
     * These should be the possible addresses which can be used to access this peer.
     * @return the public IPv4 Addresses of this peer
     */
    public static Set<InetAddress> myPublicIPv4() {
        return publicIPv4HostAddresses;
    }

    /**
     * Get all IPv6 addresses which are assigned to the local host but are public IP addresses.
     * These should be the possible addresses which can be used to access this peer.
     * @return the public IPv6 addresses of this peer
     */
    public static Set<InetAddress> myPublicIPv6() {
        return publicIPv6HostAddresses;
    }

    /**
     * generate a list of intranet InetAddresses
     * @return list of all intranet addresses
     */
    public static Set<InetAddress> myIntranetIPs() {
        if (localHostAddresses.size() < 1) try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        return localHostAddresses;
    }

    /**
     * this method is deprecated in some way because it is not applicable on IPv6
     * TODO: remove / replace
     * @param hostName
     * @return
     */
    public static boolean isThisHostIP(final String hostName) {
        if ((hostName == null) || (hostName.isEmpty())) return false;
        if (hostName.indexOf(':') > 0) return false; // IPv6 addresses do not count because they are always host IPs
        return isThisHostIP(Domains.dnsResolve(hostName));
    }

    /**
     * this method is deprecated in some way because it is not applicable on IPv6
     * TODO: remove / replace
     * @param hostName
     * @return
     */
    public static boolean isThisHostIP(final Set<String> hostNames) {
        if ((hostNames == null) || (hostNames.isEmpty())) return false;
        for (String hostName: hostNames) {
            if (hostName.indexOf(':') > 0) return false; // IPv6 addresses do not count because they are always host IPs
            if (isThisHostIP(Domains.dnsResolve(hostName))) return true;
        }
        return false;
    }

    public static boolean isThisHostIP(final InetAddress clientAddress) {
        if (clientAddress == null) return false;
        if (clientAddress.isAnyLocalAddress() || clientAddress.isLoopbackAddress()) return true;
        return myHostAddresses.contains(clientAddress); // includes localHostAddresses
    }

    public static String chopZoneID(String ip) {
        int i = ip.indexOf('%');
        return i < 0 ? ip : ip.substring(0, i);
    }

    /**
     * check the host ip string against localhost names
     * @param host
     * @return true if the host from the string is the localhost
     */
    public static boolean isLocalhost(String host) {
        if (host == null) return true; // filesystems do not have host names
        host = chopZoneID(host);
        return LOCALHOST_PATTERNS.matcher(host).matches() || localHostNames.contains(host);
    }

    /**
     * check if a given host is the name for a local host address
     * this method will return true if noLocalCheck is switched on. This means that
     * not only local and global addresses are then not distinguished but also that
     * global address hashes do not fit any more to previously stored address hashes since
     * local/global is marked in the hash.
     * @param host
     * @return
     */
    public static boolean isIntranet(final String host) {
        return (noLocalCheck || // DO NOT REMOVE THIS! it is correct to return true if the check is off
                host == null || // filesystems do not have host names
                INTRANET_PATTERNS.matcher(host).matches()) ||
                localHostNames.contains(host);
    }

    /**
     * check if the given host is a local address.
     * the hostaddress is optional and shall be given if the address is already known
     * @param host
     * @param hostaddress may be null if not known yet
     * @return true if the given host is local
     */
    public static boolean isLocal(final String host, final InetAddress hostaddress) {
        return isLocal(host, hostaddress, true);
    }

    private static boolean isLocal(final String host, InetAddress hostaddress, final boolean recursive) {

        if (noLocalCheck || // DO NOT REMOVE THIS! it is correct to return true if the check is off
            host == null ||
            host.isEmpty()) return true;

        // check local ip addresses
        if (isIntranet(host)) return true;
        if (hostaddress != null && (isIntranet(hostaddress.getHostAddress()) || isLocal(hostaddress))) return true;

        // check if there are other local IP addresses that are not in
        // the standard IP range
        if (localHostNames.contains(host)) return true;

        // check simply if the tld in the host is a known tld
        final int p = host.lastIndexOf('.');
        final String tld = (p > 0) ? host.substring(p + 1) : "";
        final Integer i = TLDID.get(tld);
        if (i != null) return false;

        // check dns lookup: may be a local address even if the domain name looks global
        if (!recursive) return false;
        if (hostaddress == null) hostaddress = dnsResolve(host);
        return isLocal(hostaddress);
    }

    private static boolean isLocal(final InetAddress a) {
        final boolean
            localp = noLocalCheck || // DO NOT REMOVE THIS! it is correct to return true if the check is off
            a == null ||
            a.isAnyLocalAddress() ||
            a.isLinkLocalAddress() ||
            a.isLoopbackAddress() ||
            a.isSiteLocalAddress();
        return localp;
    }

    public static void main(final String[] args) {
        /*
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    System.out.println(addr);
                }
            }
        } catch(SocketException e) {
            System.err.println(e);
        }
        */
        InetAddress a;
        a = dnsResolve("yacy.net"); System.out.println(a);
        a = dnsResolve("kaskelix.de"); System.out.println(a);
        a = dnsResolve("yacy.net"); System.out.println(a);

        try { Thread.sleep(1000);} catch (final InterruptedException e) {} // get time for class init
        System.out.println("myPublicLocalIP: " + myPublicLocalIP());
        for (final InetAddress b : myIntranetIPs()) {
            System.out.println("Intranet IP: " + b);
        }
    }
}
