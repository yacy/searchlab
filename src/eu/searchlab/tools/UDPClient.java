/**
 *  UDPClient
 *  Copyright 02.05.2022 by Michael Peter Christen, @orbiterlab
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;

public class UDPClient {

    private final static byte GELF_MAGIC0 = (byte) 0x1e;
    private final static byte GELF_MAGIC1 = (byte) 0x0f;
    private final static byte GZIP_MAGIC0 = (byte) 0x1f;
    private final static byte GZIP_MAGIC1 = (byte) 0x8b;
    private final static int MAXPACKET = 0xFFC6;

    private final DatagramSocket socket;
    private final InetAddress address;
    private final String host;
    private final int port;
    private final AtomicLong count;


    public UDPClient() throws IOException {
        this("localhost", 12201);
    }

    public UDPClient(final int port) throws IOException {
        this("localhost", port);
    }

    public UDPClient(final String host, final int port) throws IOException {
        this.socket = new DatagramSocket();
        this.host = host;
        this.address = InetAddress.getByName(host);
        this.port = port;
        this.count = new AtomicLong(0);
    }


    public void send(final String s) {
        final byte[] b = s.getBytes(StandardCharsets.UTF_8);
        //byte[] b0 = new byte[b.length + 1];
        //System.arraycopy(b, 0, b0, 0, b.length);
        //b0[b.length] = 0;
        //b = b0;
        sendGELF(b);
    }

    public void sendGELF(final GELFObject gelf) {
        try {send(gelf.toString(0));} catch (final JSONException e) {}
    }

    public void sendGELF(byte[] b) {
        // check if the message is gzipped
        if (b[0] != GZIP_MAGIC0 || b[1] != GZIP_MAGIC1) {
            // not compressed, we do this here to save bandwidth
            try {
                final ByteArrayOutputStream c = new ByteArrayOutputStream(b.length);
                final GZIPOutputStream z = new GZIPOutputStream(c);
                z.write(b);
                z.close();
                c.close();
                final byte[] bz = c.toByteArray();
                assert bz[0] == GZIP_MAGIC0;
                assert bz[1] == GZIP_MAGIC1;
                // sometimes the compression is larger than the original
                if (bz.length < b.length) b = bz;
            } catch (final IOException e) {
                // do nothing, we leave the data as is
            }
        }

        // check if the maximum packet size is exceeded
        if (b.length <= MAXPACKET) {
            // this can be send in one package
            sendRaw(b);
        } else {
            // send chunks
            final int chunkcount = b.length / (MAXPACKET - 12) + (b.length % (MAXPACKET - 12) == 0 ? 0 : 1);
            assert chunkcount <= 255;
            final long id = (this.host + System.currentTimeMillis()).hashCode() + this.count.incrementAndGet();
            final byte[] idh = ByteBuffer.allocate(Long.BYTES).putLong(id).array();
            for (int i = 0; i < chunkcount; i++) {
                final byte[] chunk = new byte[MAXPACKET]; // that includes the 12 bytes header
                int length = b.length - (i * (MAXPACKET - 12)); // the net data length
                if (length > MAXPACKET - 12) length = MAXPACKET - 12;
                System.arraycopy(b, i * (MAXPACKET - 12), chunk, 12, length);
                chunk[0] = GELF_MAGIC0;
                chunk[1] = GELF_MAGIC1;
                System.arraycopy(idh, 0, chunk, 2, 8);
                chunk[10] = (byte) i;
                chunk[11] = (byte) chunkcount;
                sendRaw(chunk);
                System.out.println("sent chunk " + i + " of " + chunkcount + " for id " + id);
                try {Thread.sleep(10);} catch (final InterruptedException e) {}
            }
        }
    }

    private void sendRaw(final byte[] b) {
        assert b.length <= MAXPACKET : "b.length = " + b.length + " > " + MAXPACKET + " = MAXPACKET";
        final DatagramPacket packet = new DatagramPacket(b, b.length, this.address, this.port);
        try {
            this.socket.send(packet);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        this.socket.close();
    }

}
