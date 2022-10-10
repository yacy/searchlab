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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.tools.Domains;
import eu.searchlab.tools.Logger;

/**
 * Use lock files to get exclusive access to shared files.
 */
public final class ConcurrentIO {

    private final GenericIO io;
    private final long waitingtime;

    /**
     * ConcurrentIO
     * @param io
     */
    public ConcurrentIO(final GenericIO io, final long waitingtime) {
        this.io = io;
        this.waitingtime = waitingtime;
    }

    public final GenericIO getIO() {
        return this.io;
    }

    private final static IOPath lockFile(final IOPath iop) {
        if (iop.isFolder()) throw new RuntimeException("IOPath must not be a folder: " + iop.toString());
        final IOPath lockFile = new IOPath(iop.getBucket(), iop.getPath() + ".lock");
        return lockFile;
    }

    private final static IOPath[] lockFiles(final IOPath... iops) {
        final IOPath[] lockFiles = new IOPath[iops.length];
        for (int i = 0; i < iops.length; i++) {
            final IOPath iop = iops[i];
            if (iop.isFolder()) throw new RuntimeException("IOPath must not be a folder: " + iop.toString());
            lockFiles[i] = new IOPath(iop.getBucket(), iop.getPath() + ".lock");
        }
        return lockFiles;
    }

    private final static IOPath lockFile(final IOObject ioo) {
        final IOPath iop = ioo.getPath();
        return lockFile(iop);
    }

    private final static IOPath[] lockFiles(final IOObject... ioos) {
        final IOPath[] iops = new IOPath[ioos.length];
        for (int i = 0; i < ioos.length; i++) iops[i] = ioos[i].getPath();
        return lockFiles(iops);
    }

    private final IOObject readLockFile(final IOPath lockFile) throws IOException {
        assert this.io.exists(lockFile);
        final byte[] a = this.io.readAll(lockFile);
        return new IOObject(lockFile, a);
    }

    private final void writeLockFiles(final IOPath... lockFiles) throws IOException {
        for (int i = 0; i < lockFiles.length; i++) {
            final IOPath lockFile = lockFiles[i];
            assert !this.io.exists(lockFile);
            final InetAddress localhost = Domains.myLocalhostIP(); // InetAddress.getLocalHost();
            final long time = System.currentTimeMillis();
            final JSONObject json = new JSONObject(true)
                    .put("host", localhost.getCanonicalHostName())
                    .put("ip", localhost.getHostAddress())
                    .put("time", time);
            this.io.write(lockFile, json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private final void releaseLockFiles(final IOPath... lockFiles) throws IOException {
        for (int i = 0; i < lockFiles.length; i++) {
            final IOPath lockFile = lockFiles[i];
            assert this.io.exists(lockFile);
            this.io.remove(lockFile);
        }
    }

    private final boolean waitUntilUnlock(final IOPath lockFile) {
        if (this.waitingtime <= 0) return true;
        final long timeout = System.currentTimeMillis() + this.waitingtime;
        waitloop: while (System.currentTimeMillis() < timeout) {
            if (this.io.exists(lockFile)) {
                try {Thread.sleep(1000);} catch (final InterruptedException e) {}
                continue waitloop;
            }
            // lock file does not exist, this is a success!
            return true;
        }
        return false;
    }

    private final boolean waitUntilUnlocks(final IOPath... lockFiles) {
        if (this.waitingtime <= 0) return true;
        final long timeout = System.currentTimeMillis() + this.waitingtime;
        waitloop: while (System.currentTimeMillis() < timeout) {
            for (int i = 0; i < lockFiles.length; i++) {
                if (this.io.exists(lockFiles[i])) {
                    try {Thread.sleep(1000);} catch (final InterruptedException e) {}
                    continue waitloop;
                }
            }
            // none of the lock files exist, this is a success!
            return true;
        }
        return false;
    }

    public final boolean exists(final IOPath iop) {
        return this.io.exists(iop);
    }

    public final void write(final IOObject... ioos) throws IOException {
        final IOPath[] lockFiles = lockFiles(ioos);
        if (waitUntilUnlocks(lockFiles)) {
            writeLockFiles(lockFiles);
            for (int i = 0; i < ioos.length; i++) {
                this.io.write(ioos[i].getPath(), ioos[i].getObject());
            }
            releaseLockFiles(lockFiles);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public final void writeForced(final IOObject... ioos) throws IOException {
        try {
            write(ioos);
        } catch (final IOException e) {
            for (int i = 0; i < ioos.length; i++) deleteLock(ioos[i].getPath());
            write(ioos);
        }
    }

    public void writeGZIPForced(final IOPath iopgz, final byte[] object) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final GZIPOutputStream zipStream = new GZIPOutputStream(baos);
        zipStream.write(object);
        zipStream.close();
        baos.close();
        writeForced(new IOObject(iopgz, baos.toByteArray()));
    }

    public final IOObject[] read(final IOPath... iops) throws IOException {
        final IOPath[] lockFiles = lockFiles(iops);
        final IOObject[] as = new IOObject[iops.length];
        if (waitUntilUnlocks(lockFiles)) {
            writeLockFiles(lockFiles);
            for (int i = 0; i < iops.length; i++) {
                final byte[] a = this.io.readAll(iops[i]);
                as[i] = new IOObject(iops[i], a);
            }
            releaseLockFiles(lockFiles);
            return as;
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public final IOObject[] readForced(final IOPath... iops) throws IOException {
        try {
            return read(iops);
        } catch (final IOException e) {
            deleteLock(iops);
            try {
                return read(iops);
            } catch (final IOException e1) {
                // if this fails again, we must remove the lock files
                deleteLock(iops);
                throw e1;
            }
        }
    }

    public final void append(final IOPath iop, final byte[] b) throws IOException {
        final IOPath lockFile = lockFile(iop);
        if (waitUntilUnlock(lockFile)) {
            writeLockFiles(lockFile);
            if (this.io.exists(iop)) {
                final byte[] a = this.io.readAll(iop);
                final byte[] ab = new byte[a.length + b.length];
                System.arraycopy(a, 0, ab, 0, a.length);
                System.arraycopy(b, 0, ab, a.length, b.length);
                this.io.write(iop, ab);
            } else {
                this.io.write(iop, b);
            }
            releaseLockFiles(lockFile);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public final void appendForced(final IOPath iop, final byte[] b) throws IOException {
        try {
            append(iop, b);
        } catch (final IOException e) {
            deleteLock(iop);
            append(iop, b);
        }
    }

    public final void remove(final IOPath... iops) throws IOException {
        final IOPath[] lockFiles = lockFiles(iops);
        if (waitUntilUnlocks(lockFiles)) {
            writeLockFiles(lockFiles);
            for (int i = 0; i < iops.length; i++) {
                this.io.remove(iops[i]);
            }
            releaseLockFiles(lockFiles);
        } else {
            throw new IOException("timeout waiting for lock disappearance");
        }
    }

    public final void removeForced(final IOPath... iops) throws IOException {
        try {
            remove(iops);
        } catch (final IOException e) {
            deleteLock(iops);
            remove(iops);
        }
    }

    public final boolean isLocked(final IOPath... iop) {
        final IOPath[] lockFiles = lockFiles(iop);
        for (int i = 0; i < lockFiles.length; i++) {
            if (this.io.exists(lockFiles[i])) return true;
        }
        return false;
    }

    public final void deleteLock(final IOPath... iop) {
        final IOPath[] lockFiles = lockFiles(iop);
        try {
            for (int i = 0; i < lockFiles.length; i++) {
                this.io.remove(lockFiles[i]);
            }
        } catch (final IOException e) {
            Logger.warn(e);
        }
    }

    public final String lockedByHost(final IOPath iop) throws IOException {
        final IOPath[] lockFiles = lockFiles(iop);
        for (int i = 0; i < lockFiles.length; i++) {
            if (this.io.exists(lockFiles[i])) {
                final IOObject ioo = readLockFile(lockFiles[i]);
                try {
                    return ioo.getJSONObject().getString("host");
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
        }
        throw new IOException("no lockfile exist");
    }

    public final String lockedByIP(final IOPath iop) throws IOException {
        final IOPath[] lockFiles = lockFiles(iop);
        for (int i = 0; i < lockFiles.length; i++) {
            if (this.io.exists(lockFiles[i])) {
                final IOObject ioo = readLockFile(lockFiles[i]);
                try {
                    return ioo.getJSONObject().getString("ip");
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
        }
        throw new IOException("no lockfile exist");
    }

    public final long lockedByTime(final IOPath iop) throws IOException {
        final IOPath[] lockFiles = lockFiles(iop);
        for (int i = 0; i < lockFiles.length; i++) {
            if (this.io.exists(lockFiles[i])) {
                final IOObject ioo = readLockFile(lockFiles[i]);
                try {
                    return ioo.getJSONObject().getLong("time");
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
        }
        throw new IOException("no lockfile exist");
    }

    @Override
    public String toString() {
        return this.io.toString();
    }

}
