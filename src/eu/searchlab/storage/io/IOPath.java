/**
 *  IOPath
 *  Copyright 07.10.2021 by Michael Peter Christen, @orbiterlab
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

import java.util.Comparator;

/**
 * IOPath
 * An IOPath denotes an object stored with a GenericIO endpoint.
 * It is used to bundle bucket and path together while leaving the information
 * which endpoint is storing the object aside. IOPaths are used for higher level
 * data structures which require a single object as denominator.
 * This object can be compared with "File" for file storage.
 * By conventional definition:
 * - an IOPath is a file if the last element of the path
 *   after the last "/" contains an extension separator, a ".".
 * - a path always starts with a "/".
 * - a path does never end with a "/".
 */
public final class IOPath implements Comparable<IOPath>, Comparator<IOPath> {

    private final String bucket, objectPath;

    /**
     * Compute a canonical path from the given path.
     * A canonical path is a string where
     * - the first character is always a slash "/"
     * - the last character is not a slash "/"
     * @param path
     * @return
     */
    public final static String canonicalPath(String path) {
        if (path.length() == 0) return "";
        if (path.charAt(0) != '/') path = "/" + path;
        if (path.length() > 1 && path.charAt(path.length() - 1) == '/') path = path.substring(0, path.length() - 1);

        int p;
        while (path.length() > 3 && (p = path.indexOf("/..")) >= 0) {
            final String h = path.substring(0, p);
            final int q = h.lastIndexOf('/');
            path = q < 0 ? "" : h.substring(0, q) + path.substring(p + 3);
        }
        return path;
    }

    /**
     * Create a new IOPath with a given bucket and object path.
     * The object path shall be normalized in the following way: no leading and no ending "/".
     * @param bucket name of the bucket
     * @param objectPath "/"-separated list of path elements, where the last element may be the file
     */
    public IOPath(final String bucket, final String objectPath) {
        this.bucket = bucket;
        this.objectPath = canonicalPath(objectPath);
    }

    /**
     * an IOPath is a folder if the last element of the path after the latest "/" does not contains a "."
     * or the length of the extension is greater than 8
     * @return
     */
    public final boolean isFolder() {
        int p = this.objectPath.lastIndexOf('/');
        if (p < 0) p = 0; // no '/' inside, thats not normal but ok for this test
        final int q = this.objectPath.indexOf('.', p);
        if (q < 0) return true; // no dot in last path -> not a file, therefore a dir
        // q is now the length of the head part
        final int extlength = this.objectPath.length() - q - 1; // length of the extension
        return extlength > 8;
    }

    /**
     * a folder is a root folder it can not be truncated
     * @return
     */
    public final boolean isRootFolder() {
        final int p = this.objectPath.lastIndexOf('/');
        assert p != 0;
        return p > 0;
    }

    public final boolean isInRootFolder() {
        final int p = this.objectPath.indexOf('/');
        return p <= 0;
    }

    /**
     * construct the full access path, which is a combination of bucket + "/" + objectPath
     * @return the full access path
     */
    public String getAccessPath() {
        return this.bucket + this.objectPath;
    }

    /**
     * get the bucket name
     * @return the bucket name
     */
    public final String getBucket() {
        return this.bucket;
    }

    /**
     * get the path inside the bucket
     * The path starts with "/" but does never and with "/"
     * @return the path inside the bucket
     */
    public final String getObjectPath() {
        return this.objectPath;
    }

    /**
     * get the name of an object. This is the last parth of the path after the last "/".
     * @return a name of an object without the path to it.
     */
    public String getObjectName() {
        final int p = this.objectPath.lastIndexOf('/');
        if (p < 0) throw new RuntimeException("IOPath must have at least one folder to truncate: " + this.toString());
        return this.objectPath.substring(p + 1);
    }

    /**
     * get the name of the object denoted by this IOPath
     * @return the last element in the objectPath
     */
    public final IOPath getParent() {
        final int pp = this.objectPath.lastIndexOf('/');
        final String p = pp < 0 ? this.objectPath : this.objectPath.substring(0, pp);
        return new IOPath(this.bucket, p);
    }

    /**
     * Append a subpath to the existing path.
     * The new path should not have a beginnt "/" and no ending "/" but if it exist, it is cutted away.
     * New subpaths are appended with a "/" in between, so IOPaths where you are appending anything
     * must be considered as a folder before.
     * @param spath
     * @return
     */
    public IOPath append(String spath) {
        assert this.isFolder();
        if (!isFolder()) throw new RuntimeException("IOPath must be a folder to append a path: " + this.toString());
        spath = canonicalPath(spath);
        return new IOPath(this.bucket, this.objectPath + spath); // can be appended directly becaue now spath starts with "/"
    }

    /**
     * Truncate the path: remove the last path element after the latest "/".
     * The resulting path must be considered as a folder.
     * @return the tuncated path
     */
    public IOPath truncate() {
        if (this.objectPath.length() <= 1) return new IOPath(this.bucket, ""); // should be an error
        final int p = this.objectPath.lastIndexOf('/');
        if (p < 0) throw new RuntimeException("IOPath must have at leas one folder to truncate: " + this.toString());
        final IOPath t = new IOPath(this.bucket, this.objectPath.substring(0, p));
        assert t.isFolder();
        return t;
    }

    /**
     * the head of a path is the first part of it
     * @return
     */
    public String head() {
        final int p = this.objectPath.indexOf('/', 1);
        if (p < 0) throw new RuntimeException("IOPath must have at least one folder to truncate: " + this.toString());
        return this.objectPath.substring(1, p);
    }

    @Override
    public String toString() {
        return this.bucket + this.objectPath;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public int compareTo(final IOPath o) {
        return this.toString().compareTo(o.toString());
    }

    @Override
    public int compare(final IOPath o1, final IOPath o2) {
        return o1.compareTo(o2);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof IOPath)) return false;
        return this.toString().equals(((IOPath) obj).toString());
    }
}
