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

/**
 * Use lock files to get exclusive access to shared files.
 */
public final class ConcurrentIO {

    private final GenericIO io;

    /**
     * ConcurrentIO
     * @param io
     */
    public ConcurrentIO(final GenericIO io) {
        this.io = io;
    }

    public final GenericIO getIO() {
        return this.io;
    }

    private final static IOPath lockFile(final IOPath iop) {
        if (iop.isFolder()) throw new RuntimeException("IOPath must not be a folder: " + iop.toString());
        return new IOPath(iop.getBucket(), iop.getPath() + ".lock");
    }

    private final static IOPath[] lockFiles(final IOPath... iop) {
        final IOPath[] lockFiles = new IOPath[iop.length];
        for (int i = 0; i < iop.length; i++) lockFiles[i] = lockFile(iop[i]);
        return lockFiles;
    }

    private IOObject readLockFile(final IOPath lockFile) throws IOException {
        assert this.io.exists(lockFile);
        final byte[] a = this.io.readAll(lockFile);
        return new IOObject(lockFile, a);
    }

    private void writeLockFile(final IOPath lockFile) throws IOException {
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

    private void releaseeLockFile(final IOPath lockFile) throws IOException {
        assert this.io.exists(lockFile);
        this.io.remove(lockFile);
    }

    private boolean waitUntilUnlock(final long waitingtime, final IOPath lockFile) {
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

    public void write(final long waitingtime, final IOObject ioo) throws IOException {
        final IOPath lockFile = lockFile(ioo.getPath());
        if (waitUntilUnlock(waitingtime, lockFile)) {
            writeLockFile(lockFile);
            this.io.write(ioo.getPath(), ioo.getObject());
            releaseeLockFile(lockFile);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public void writeForced(final long waitingtime, final IOObject ioo) throws IOException {
        try {
            write(waitingtime, ioo);
        } catch (final IOException e) {
            deleteLock(ioo.getPath());
            write(-1, ioo);
        }
    }

    public IOObject read(final long waitingtime, final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        if (waitUntilUnlock(waitingtime, lockFile)) {
            writeLockFile(lockFile);
            final byte[] a = this.io.readAll(iop);
            releaseeLockFile(lockFile);
            return new IOObject(iop, a);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public IOObject readForced(final long waitingtime, final IOPath iop) throws IOException {
        try {
            return read(waitingtime, iop);
        } catch (final IOException e) {
            deleteLock(iop);
            return read(-1, iop);
        }
    }

    public void remove(final long waitingtime, final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        if (waitUntilUnlock(waitingtime, lockFile)) {
            writeLockFile(lockFile);
            this.io.remove(iop);
            releaseeLockFile(lockFile);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public void removeForced(final long waitingtime, final IOPath iop) throws IOException {
        try {
            remove(waitingtime, iop);
        } catch (final IOException e) {
            deleteLock(iop);
            remove(-1, iop);
        }
    }

    public boolean isLocked(final IOPath iop) {
        final IOPath lockFile = lockFile(iop);
        return this.io.exists(lockFile);
    }

    public void deleteLock(final IOPath iop) {
        try {
            this.io.remove(lockFile(iop));
        } catch (final IOException e) {}
    }

    public String lockedByHost(final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        final IOObject ioo = readLockFile(lockFile);
        try {
            return ioo.getJSONObject().getString("host");
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public String lockedByIP(final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        final IOObject ioo = readLockFile(lockFile);
        try {
            return ioo.getJSONObject().getString("ip");
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public long lockedByTime(final IOPath iop) throws IOException {
        final IOPath lockFile = lockFile(iop);
        final IOObject ioo = readLockFile(lockFile);
        try {
            return ioo.getJSONObject().getLong("time");
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

}
