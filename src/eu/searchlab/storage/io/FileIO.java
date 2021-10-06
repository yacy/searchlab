/**
 *  FileIO
 *  Copyright 06.10.2021 by Michael Peter Christen, @orbiterlab
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FileIO extends AbstractIO implements GenericIO {

    private final File basePath;

    public FileIO(final File basePath) throws IOException {
        if (!basePath.exists()) throw new IOException("base path " + basePath.toString() + " does not exist");
        if (!basePath.isDirectory()) throw new IOException("base path " + basePath.toString() + " is not a directory");
        this.basePath = basePath;
    }

    public File getBasePath() {
        return this.basePath;
    }

    private File getBucketFile(final String bucket) {
        final File f = new File(this.basePath, bucket);
        return f;
    }

    private File getObjectFile(final String bucket, final String objectName) {
        File f = getBucketFile(bucket);
        final String[] paths = objectName.split("/");
        for (final String p: paths) f = new File(f, p);
        return f;
    }

    @Override
    public void makeBucket(final String bucketName) throws IOException {
        final File f = getBucketFile(bucketName);
        if (f.exists()) {
            if (f.isDirectory()) return;
            throw new IOException("bucket path " + f.toString() + " is a file, not a directory");
        }
        f.mkdirs();
    }

    @Override
    public boolean bucketExists(final String bucketName) throws IOException {
        final File f = getBucketFile(bucketName);
        return f.exists() && f.isDirectory();
    }

    @Override
    public List<String> listBuckets() throws IOException {
        final String[] b = this.basePath.list();
        final ArrayList<String> l = new ArrayList<>();
        for (final String s: b) {
            if (s.length() == 0) continue;
            if (s.charAt(0) == '.') continue;
            if (new File(this.basePath, s).isDirectory()) l.add(s);
        }
        return l;
    }

    @Override
    public long bucketCreation(final String bucketName) throws IOException {
        final File b = getBucketFile(bucketName);
        return b.lastModified();
    }

    @Override
    public void removeBucket(final String bucketName) throws IOException {
        final File b = getBucketFile(bucketName);
        if (b.exists()) b.delete();
    }

    @Override
    public void write(final String bucketName, final String objectName, final byte[] object) throws IOException {
        final File f = getObjectFile(bucketName, objectName);
        final FileOutputStream fos = new FileOutputStream(f);
        fos.write(object);
        fos.close();
    }

    @Override
    public void write(final String bucketName, final String objectName, final PipedOutputStream pos, final long len) throws IOException {
        final File f = getObjectFile(bucketName, objectName);
        final FileOutputStream fos = new FileOutputStream(f);
        final InputStream is = new PipedInputStream(pos, 4096);
        final IOException[] ea = new IOException[1];
        ea[0] = null;
        final AtomicLong ai = new AtomicLong(len);
        final Thread t = new Thread() {
            @Override
            public void run() {
                this.setName("FileIO writer for " + bucketName + "/" + objectName);
                final byte[] buffer = new byte[4096];
                int l;
                try {
                    while ((l = is.read(buffer)) > 0) {
                        if (len >= 0) {
                            if (l > ai.get()) {
                                fos.write(buffer, 0, (int) ai.get());
                                break;
                            } else {
                                fos.write(buffer, 0, l);
                                ai.addAndGet((int) -l);
                            }
                        } else {
                            fos.write(buffer, 0, l);
                            ai.addAndGet((int) -l);
                        }
                    }
                    fos.close();
                    is.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                    ea[0] = e;
                    try {pos.close();} catch (final IOException e1) {}
                    try {is.close();} catch (final IOException e1) {}
                }
            }
        };
        t.start();
        if (ea[0] != null) throw ea[0];
    }

    @Override
    public void copy(
            final String fromBucketName, final String fromObjectName,
            final String toBucketName, final String toObjectName) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void move(
            final String fromBucketName, final String fromObjectName,
            final String toBucketName, final String toObjectName) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public InputStream read(final String bucketName, final String objectName) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InputStream read(final String bucketName, final String objectName, final long offset)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InputStream read(final String bucketName, final String objectName, final long offset, final long len) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void remove(final String bucketName, final String objectName) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<String> list(final String bucketName, final String prefix) throws IOException {
        final File f = getObjectFile(bucketName, prefix);
        final String[] u = f.list();
        final List<String> list = new ArrayList<>(u.length);
        for (final String objectName: u) {
            list.add(objectName);
        }
        return list;
    }

    @Override
    public long diskUsage(final String bucketName, final String prefix) throws IOException {
        final File f = getObjectFile(bucketName, prefix);
        long du = 0;
        for (final String objectName: f.list()) {
            du += new File(f, objectName).length();
        }
        return du;
    }

    @Override
    public long lastModified(final String bucketName, final String objectName) {
        final File f = getObjectFile(bucketName, objectName);
        return f.lastModified();
    }

    @Override
    public long size(final String bucketName, final String objectName) throws IOException {
        final File f = getObjectFile(bucketName, objectName);
        return f.length();
    }

}
