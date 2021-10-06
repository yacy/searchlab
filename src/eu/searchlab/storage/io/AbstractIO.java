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

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;

public abstract class AbstractIO implements GenericIO {

    /**
     * client-side merge of two objects into a new object
     * @param fromBucketName0
     * @param fromObjectName0
     * @param fromBucketName1
     * @param fromObjectName1
     * @param toBucketName
     * @param toObjectName
     * @throws IOException
     */
    @Override
    public void merge(
            final String fromBucketName0, final String fromObjectName0,
            final String fromBucketName1, final String fromObjectName1,
            final String toBucketName, final String toObjectName) throws IOException {
        final long size0 = this.size(fromBucketName0, fromObjectName0);
        final long size1 = this.size(fromBucketName1, fromObjectName1);
        final long size = size0 < 0 || size1 < 0 ? -1 : size0 + size1;
        final PipedOutputStream pos = new PipedOutputStream();
        this.write(toBucketName, toObjectName, pos, size);
        InputStream is = this.read(fromBucketName0, fromObjectName0);
        final byte[] buffer = new byte[4096];
        int l;
        while ((l = is.read(buffer)) > 0) pos.write(buffer, 0, l);
        is.close();
        is = this.read(fromBucketName1, fromObjectName1);
        while ((l = is.read(buffer)) > 0) pos.write(buffer, 0, l);
        is.close();
        pos.close();
    }

    /**
     * merge an arbitrary number of objects into one target object
     * @param bucketName
     * @param toObjectName
     * @param fromObjectNames
     * @throws IOException
     */
    @Override
    public void mergeFrom(final String bucketName, final String toObjectName, final String... fromObjectNames) throws IOException {
        long size = 0;
        for (final String fromObjectName: fromObjectNames) {
            final long sizeN = this.size(bucketName, fromObjectName);
            if (sizeN < 0) {
                size = -1;
                break;
            }
            size += sizeN;
        }
        final PipedOutputStream pos = new PipedOutputStream();
        this.write(bucketName, toObjectName, pos, size);
        final byte[] buffer = new byte[4096];
        for (final String fromObjectName: fromObjectNames) {
            final InputStream is = this.read(bucketName, fromObjectName);
            int l;
            while ((l = is.read(buffer)) > 0) pos.write(buffer, 0, l);
            is.close();
        }
        pos.close();
    }

    /**
     * renaming/moving of one object into another. This is done using client-side object duplication
     * with deletion of the original because S3 does not support renaming/moving.
     * @param fromBucketName
     * @param fromObjectName
     * @param toBucketName
     * @param toObjectName
     * @throws IOException
     */
    @Override
    public void move(final String fromBucketName, final String fromObjectName, final String toBucketName, final String toObjectName) throws IOException {
        // there is unfortunately no server-side move
        this.copy(fromBucketName, fromObjectName, toBucketName, toObjectName);
        this.remove(fromBucketName, fromObjectName);
    }

}
