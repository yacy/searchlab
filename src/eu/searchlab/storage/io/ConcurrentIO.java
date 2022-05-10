/**
 *  ConcurrentIO
 *  Copyright 10.05.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.io;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Use lock files to get exclusive access to shared files.
 */
public class ConcurrentIO {

    private final GenericIO io;

    /**
     *
     * @param io
     */
    public ConcurrentIO(final GenericIO io) {
        this.io = io;
    }

    public GenericIO getIO() {
    	return this.io;
    }

    private static IOPath lockFile(final IOPath iop) {
        if (iop.isFolder()) throw new RuntimeException("IOPath must not be a folder: " + iop.toString());
        return new IOPath(iop.getBucket(), iop.getPath() + ".lock");
    }

    private JSONObject readLockFile(final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        assert this.io.exists(lockFile);
        final byte[] a = this.io.readAll(lockFile);
        try {
            final JSONObject json = new JSONObject(new JSONTokener(new String(a, StandardCharsets.UTF_8)));
            return json;
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private void writeLockFile(final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        assert !this.io.exists(lockFile);
        final InetAddress localhost = InetAddress.getLocalHost();
        final long time = System.currentTimeMillis();
        try {
            final JSONObject json = new JSONObject(true)
                    .put("host", localhost.getCanonicalHostName())
                    .put("ip", localhost.getHostAddress())
                    .put("time", time);
            this.io.write(lockFile, json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private void releaseeLockFile(final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        assert this.io.exists(lockFile);
        this.io.remove(lockFile);
    }

    private boolean waitUntilUnlock(final IOPath iop, final long waitingtime) {
		final IOPath lockFile = lockFile(iop);
    	if (waitingtime <= 0) {
    		return !this.io.exists(lockFile);
    	} else {
    		final long timeout = System.currentTimeMillis() + waitingtime;
    		while (this.io.exists(lockFile)) {
    			if (System.currentTimeMillis() > timeout) return false;
    			try {Thread.sleep(1000);} catch (final InterruptedException e) {}
    		}
    		return true;
    	}
    }

    public void write(final IOPath iop, final byte[] object, final long waitingtime) throws IOException {
        if (waitUntilUnlock(iop, waitingtime)) {
        	writeLockFile(iop);
            this.io.write(iop, object);
            releaseeLockFile(iop);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public void writeForced(final IOPath iop, final byte[] object, final long waitingtime) throws IOException {
    	try {
			write(iop, object, waitingtime);
		} catch (final IOException e) {
			deleteLock(iop);
			write(iop, object, -1);
		}
    }

    public byte[] read(final IOPath iop, final long waitingtime) throws IOException {
    	if (waitUntilUnlock(iop, waitingtime)) {
        	writeLockFile(iop);
        	final byte[] a = this.io.readAll(iop);
            releaseeLockFile(iop);
            return a;
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public byte[] readForced(final IOPath iop, final long waitingtime) throws IOException {
    	try {
			return read(iop, waitingtime);
		} catch (final IOException e) {
			deleteLock(iop);
			return read(iop, -1);
		}
    }

    public void remove(final IOPath iop, final long waitingtime) throws IOException {
    	if (waitUntilUnlock(iop, waitingtime)) {
        	writeLockFile(iop);
            this.io.remove(iop);
            releaseeLockFile(iop);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public void removeForced(final IOPath iop, final long waitingtime) throws IOException {
    	try {
			remove(iop, waitingtime);
		} catch (final IOException e) {
			deleteLock(iop);
			remove(iop, -1);
		}
    }

    public boolean isLocked(final IOPath iop) {
        return this.io.exists(lockFile(iop));
    }

    public void deleteLock(final IOPath iop) {
        try {
            this.io.remove(lockFile(iop));
        } catch (final IOException e) {}
    }

    public String lockedByHost(final IOPath iop) throws IOException {
        final JSONObject json = readLockFile(iop);
        try {
            return json.getString("host");
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public String lockedByIP(final IOPath iop) throws IOException {
        final JSONObject json = readLockFile(iop);
        try {
            return json.getString("ip");
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public long lockedByTime(final IOPath iop) throws IOException {
        final JSONObject json = readLockFile(iop);
        try {
            return json.getLong("time");
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

}
