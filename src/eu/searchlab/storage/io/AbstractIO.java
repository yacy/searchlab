/**
 *  AbstractIO
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import eu.searchlab.tools.Logger;

public abstract class AbstractIO implements GenericIO {

    protected final ConcurrentHashMap<IOPath, IODirList> dirListCache = new ConcurrentHashMap<>();

    @Override
    public void writeGZIP(final IOPath iop, final byte[] object) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final GZIPOutputStream zipStream = new GZIPOutputStream(baos, 8192);
        zipStream.write(object);
        zipStream.close();
        baos.close();
        write(iop, baos.toByteArray());
        this.dirListCache.remove(iop.getParent());
    }

    @Override
    public void write(final IOPath iop, final File fromFile) throws IOException {
        // this should be replaced by a streaming version to be able to operate on large files
        write(iop, Files.readAllBytes(fromFile.toPath()));
    }

    @Override
    public void writeGZIP(final IOPath iop, final File fromFile) throws IOException {
        // this should be replaced by a streaming version to be able to operate on large files
        writeGZIP(iop, Files.readAllBytes(fromFile.toPath()));
    }

    @Override
    public InputStream readGZIP(final IOPath iop) throws IOException {
        final byte[] a = readAll(iop);
        final ByteArrayInputStream bais = new ByteArrayInputStream(a);
        final GZIPInputStream gis = new GZIPInputStream(bais);
        return gis;
    }

    public static byte[] readAll(final InputStream is, final int len) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c;
        byte[] b = new byte[16384];
        while ((c = is.read(b, 0, b.length)) != -1) {
            baos.write(b, 0, c);
            if (len > 0 && baos.size() >= len) break;
        }
        b = baos.toByteArray();
        if (len <= 0) return b;
        if (b.length < len) throw new IOException("only " + b.length + " bytes available in stream");
        if (b.length == len) return b;
        final byte[] a = new byte[len];
        System.arraycopy(b, 0, a, 0, len);
        return a;
    }

    @Override
    public byte[] readAll(final IOPath iop) throws IOException {
        return readAll(read(iop), -1);
    }

    @Override
    public byte[] readAll(final IOPath iop, final long offset) throws IOException {
        return readAll(read(iop, offset), -1);
    }

    @Override
    public byte[] readAll(final IOPath iop, final long offset, final long len) throws IOException {
        return readAll(read(iop, offset), (int) len);
    }

    @Override
    public void merge(final IOPath fromIOp0, final IOPath fromIOp1, final IOPath toIOp) throws IOException {
        final long size0 = this.size(fromIOp0);
        final long size1 = this.size(fromIOp1);
        final long size = size0 < 0 || size1 < 0 ? -1 : size0 + size1;
        final PipedOutputStream pos = new PipedOutputStream();
        this.write(toIOp, pos, size);
        InputStream is = this.read(fromIOp0);
        final byte[] buffer = new byte[4096];
        int l;
        try {
            while ((l = is.read(buffer)) > 0) pos.write(buffer, 0, l);
            is.close();
        } catch (final IOException e) {}
        is = this.read(fromIOp1);
        try {
            while ((l = is.read(buffer)) > 0) pos.write(buffer, 0, l);
            is.close();
        } catch (final IOException e) {}
        pos.close();
        this.dirListCache.remove(toIOp.getParent());
    }

    @Override
    public void mergeFrom(final IOPath iop, final IOPath... fromIOps) throws IOException {
        long size = 0;
        for (final IOPath fromIOp: fromIOps) {
            final long sizeN = this.size(fromIOp);
            if (sizeN < 0) {
                size = -1;
                break;
            }
            size += sizeN;
        }
        final PipedOutputStream pos = new PipedOutputStream();
        this.write(iop, pos, size);
        final byte[] buffer = new byte[4096];
        for (final IOPath fromIOp: fromIOps) {
            final InputStream is = this.read(fromIOp);
            int l;
            while ((l = is.read(buffer)) > 0) pos.write(buffer, 0, l);
            is.close();
        }
        pos.close();
        this.dirListCache.remove(iop.getParent());
    }

    @Override
    public void move(final IOPath fromIOp, final IOPath toIOp) throws IOException {
        // there is unfortunately no server-side move
        this.copy(fromIOp, toIOp);
        this.remove(fromIOp);
        this.dirListCache.remove(fromIOp.getParent());
        this.dirListCache.remove(toIOp.getParent());
    }

    @Override
    public List<IOPathMeta> list(final IOPath path) throws IOException {
        final List<IOPathMeta> list = list(path.getBucket(), path.getPath());
        return list;
    }

    @Override
    public IODirList dirList(final IOPath dirpath) throws IOException {

        // try to get the list from the cache
        IODirList list = dirListCache.get(dirpath);
        if (list != null && !list.isStale()) {
            Logger.info("Delivering dirList from Cache: " + dirpath.toString());
            return list;
        }

        // load the new list
        list = new IODirList();
        final Set<String> knownDir = new HashSet<>();
        final String dirpaths = dirpath.getPath();
        try {
            final List<IOPathMeta> dir = this.list(dirpath);
            for (final IOPathMeta meta: dir) {
                final IOPath o = meta.getIOPath();
                final String fullpath = o.getPath();
                // this is the 'full' path 'behind' assetsPath. We must also subtract the path where we are navigating to
                assert fullpath.startsWith(dirpaths);
                final String subpath = fullpath.substring(dirpaths.length());
                // find first element in that path
                final int p = subpath.indexOf('/', 1);
                String name = null;
                boolean isDir = false;
                if (p < 0) {
                    name = subpath.substring(1);
                } else {
                    name = subpath.substring(1, p);
                    isDir = true;
                    if (knownDir.contains(name)) continue;
                    knownDir.add(name);
                }
                if (isDir) {
                    list.add(new IODirList.Entry(name, true, 0, 0));
                } else {
                    list.add(new IODirList.Entry(name, false, meta.getSize(), meta.getLastModified()));
                }
            }
        } catch (final IOException e) {
            Logger.warn("attempt to list " + dirpath.toString(), e);
        }

        Logger.info("Delivering dirList from IO: " + dirpath.toString());
        dirListCache.put(dirpath, list);
        return list;
    }

}
