/**
 *  GenericIO
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
import java.util.List;

/**
 * Storage Engine which makes an abstraction of the actual storage system, which can be i.e.:
 * - File System
 * - FTP
 * - SMB
 * - S3
 * To map the concept of drives (SMB) and Buckets (S3) we introduce the concept of an IO <project>:
 * - a <project> is the name of a storage tenant
 * - in File Systems, FTP and SMB the <project> becomes another sub-path in front of the given path
 * - paths and <project> are encapsulated into IOPath objetcs.
 */
public interface GenericIO {

    /**
     * make a bucket
     * @param bucketName
     * @throws IOException
     */
    public void makeBucket(String bucketName) throws IOException;

    /**
     * test if a bucket exists
     * @param bucketName
     * @return
     * @throws IOException
     */
    public boolean bucketExists(String bucketName) throws IOException;

    /**
     * list all buckets
     * @return
     * @throws IOException
     */
    public List<String> listBuckets() throws IOException;

    /**
     * return bucket creation time
     * @param bucketName
     * @return
     * @throws IOException
     */
    public long bucketCreation(String bucketName) throws IOException;

    /**
     * remove bucket; that bucket must be empty or the method will fail
     * @param bucketName
     * @throws IOException
     */
    public void removeBucket(String bucketName) throws IOException;

    /**
     * write an object from a byte array
     * @param bucketName
     * @param objectName
     * @param object
     * @throws IOException
     */
    public void write(String bucketName, String objectName, byte[] object) throws IOException;

    /**
     * write to an object until given PipedOutputStream is closed
     * @param bucketName
     * @param objectName
     * @param pos
     * @param len
     * @throws IOException
     */
    public void write(String bucketName, String objectName, PipedOutputStream pos, long len) throws IOException;

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
    public void merge(
            String fromBucketName0, String fromObjectName0,
            String fromBucketName1, String fromObjectName1,
            String toBucketName, String toObjectName) throws IOException;

    /**
     * merge an arbitrary number of objects into one target object
     * @param bucketName
     * @param toObjectName
     * @param fromObjectNames
     * @throws IOException
     */
    public void mergeFrom(String bucketName, String toObjectName, String... fromObjectNames) throws IOException;

    /**
     * server-side copy of an object to another object
     * @param fromBucketName
     * @param fromObjectName
     * @param toBucketName
     * @param toObjectName
     * @throws IOException
     */
    public void copy(
            String fromBucketName, String fromObjectName,
            String toBucketName, String toObjectName) throws IOException;

    /**
     * renaming/moving of one object into another. This is done using client-side object duplication
     * with deletion of the original because S3 does not support renaming/moving.
     * @param fromBucketName
     * @param fromObjectName
     * @param toBucketName
     * @param toObjectName
     * @throws IOException
     */
    public void move(String fromBucketName, String fromObjectName, String toBucketName, String toObjectName) throws IOException;
    /**
     * reading of an object into a stream
     * @param bucketName
     * @param objectName
     * @return
     * @throws IOException
     */
    public InputStream read(String bucketName, String objectName) throws IOException;

    /**
     * reading of an object beginning with an offset
     * @param bucketName
     * @param objectName
     * @param offset
     * @return
     * @throws IOException
     */
    public InputStream read(String bucketName, String objectName, long offset) throws IOException;

    /**
     * reading of an object from an offset with given length
     * @param bucketName
     * @param objectName
     * @param offset
     * @param len
     * @return
     * @throws IOException
     */
    public InputStream read(String bucketName, String objectName, long offset, long len) throws IOException;

    /**
     * removal of an object
     * @param bucketName
     * @param objectName
     * @throws IOException
     */
    public void remove(String bucketName, String objectName) throws IOException;

    /**
     * listing of object names in a given prefix path
     * @param bucketName
     * @param prefix
     * @return
     * @throws IOException
     */
    public List<String> list(String bucketName, String prefix) throws IOException;


    /**
     * calculate the disk usage in a given path
     * @param bucketName
     * @param prefix
     * @return
     * @throws IOException
     */
    public long diskUsage(String bucketName, String prefix) throws IOException;

    /**
     * last-modified date of an object
     * @param bucketName
     * @param objectName
     * @return
     * @throws IOException
     */
    public long lastModified(String bucketName, String objectName) throws IOException;

    /**
     * size of an object
     * @param bucketName
     * @param objectName
     * @return
     * @throws IOException
     */
    public long size(String bucketName, String objectName) throws IOException;



}
