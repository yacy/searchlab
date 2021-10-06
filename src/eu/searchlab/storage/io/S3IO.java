/**
 *  S3IO
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
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;

public class S3IO extends AbstractIO implements GenericIO {

    // one "proper" part size
    private final long partSize = 10 * 1024 * 1024; // proper is a number between 5MB and 5GB

    // caches
    private final Map<String, Bucket> bucketListCache = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashMap<String, Item>> objectListCache = new ConcurrentHashMap<>();

    // the connection
    private final MinioClient mc;

    public S3IO(final String endpointURL, final String accessKey, final String secretKey) {
        this.mc =
                MinioClient.builder()
                .endpoint(endpointURL)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public void makeBucket(final String bucketName) throws IOException {
        try {
            this.mc.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public boolean bucketExists(final String bucketName) throws IOException {
        try {
            return this.mc.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public List<String> listBuckets() throws IOException {
        try {
            final List<Bucket> bucketList = this.mc.listBuckets();
            final List<String> buckets = new ArrayList<>(bucketList.size());
            for (final Bucket bucket : bucketList) {
                buckets.add(bucket.name());
                this.bucketListCache.put(bucket.name(), bucket);
                //System.out.println(bucket.creationDate().toEpochSecond() + ", " + bucket.name());
            }
            return buckets;
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public long bucketCreation(final String bucketName) throws IOException {
        Bucket bucket = this.bucketListCache.get(bucketName);
        if (bucket == null) {
            this.listBuckets();
            bucket = this.bucketListCache.get(bucketName);
        }
        if (bucket == null) throw new IOException("bucket " + bucketName + " not found");
        return bucket.creationDate().toEpochSecond() * 1000;
    }

    @Override
    public void removeBucket(final String bucketName) throws IOException {
        try {
            this.mc.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * write to an object until given PipedOutputStream is closed
     * @param bucketName
     * @param objectName
     * @param pos
     * @param len
     * @throws IOException
     */
    @Override
    public void write(final String bucketName, final String objectName, final PipedOutputStream pos, final long len) throws IOException {
        final InputStream is = new PipedInputStream(pos, 4096);
        final IOException[] ea = new IOException[1];
        ea[0] = null;
        final Thread t = new Thread() {
            @Override
            public void run() {
                this.setName("S3IO writer for " + bucketName + "/" + objectName);
                try {
                    S3IO.this.write(bucketName, objectName, is, len);
                } catch (final IOException e) {
                    e.printStackTrace();
                    ea[0] = e;
                    try {pos.close();} catch (final IOException e1) {} // this kills the calling process on purpose; write will throw an exception
                }
            }
        };
        t.start();
        if (ea[0] != null) throw ea[0];
    }

    /**
     * write a stream with known size (len >= 0) or unknown size (len < 0)
     * @param bucketName
     * @param objectName
     * @param stream
     * @param len
     * @throws IOException
     */
    public void write(final String bucketName, String objectName, final InputStream stream, final long len) throws IOException {
        if (objectName.startsWith("/")) objectName = objectName.substring(1);
        try {
            if (len < 0) {
                this.mc.putObject(
                        PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, -1, this.partSize)
                            .contentType("application/octet-stream")
                            .build());
            } else {
                this.mc.putObject(
                        PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, len, -1)
                            .contentType("application/octet-stream")
                            .build());
            }
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public void write(final String bucketName, final String objectName, final byte[] object) throws IOException {
        this.write(bucketName, objectName, new ByteArrayInputStream(object), object.length);
    }

    /**
     * server-side copy of an object to another object
     * @param fromBucketName
     * @param fromObjectName
     * @param toBucketName
     * @param toObjectName
     * @throws IOException
     */
    @Override
    public void copy(
            final String fromBucketName, String fromObjectName,
            final String toBucketName, String toObjectName) throws IOException {
        if (fromObjectName.startsWith("/")) fromObjectName = fromObjectName.substring(1);
        if (toObjectName.startsWith("/")) toObjectName = toObjectName.substring(1);
        try {
            this.mc.copyObject(
                    CopyObjectArgs.builder()
                        .bucket(toBucketName)
                        .object(toObjectName)
                        .source(CopySource.builder().bucket(fromBucketName).object(fromObjectName).build())
                        .build());
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * reading of an object into a stream
     * @param bucketName
     * @param objectName
     * @return
     * @throws IOException
     */
    @Override
    public InputStream read(final String bucketName, String objectName) throws IOException {
        if (objectName.startsWith("/")) objectName = objectName.substring(1);
        try {
            final InputStream stream = this.mc.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
            return stream;
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * reading of an object beginning with an offset
     * @param bucketName
     * @param objectName
     * @param offset
     * @return
     * @throws IOException
     */
    @Override
    public InputStream read(final String bucketName, String objectName, final long offset) throws IOException {
        if (objectName.startsWith("/")) objectName = objectName.substring(1);
        try {
            final InputStream stream = this.mc.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(offset)
                        .build());
            return stream;
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * reading of an object from an offset with given length
     * @param bucketName
     * @param objectName
     * @param offset
     * @param len
     * @return
     * @throws IOException
     */
    @Override
    public InputStream read(final String bucketName, String objectName, final long offset, final long len) throws IOException {
        if (objectName.startsWith("/")) objectName = objectName.substring(1);
        try {
            final InputStream stream = this.mc.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(offset)
                        .length(len)
                        .build());
            return stream;
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * removal of an object
     * @param bucketName
     * @param objectName
     * @throws IOException
     */
    @Override
    public void remove(final String bucketName, String objectName) throws IOException {
        if (objectName.startsWith("/")) objectName = objectName.substring(1);
        try {
            this.mc.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * listing of object names in a given prefix path
     * @param bucketName
     * @param prefix
     * @return
     * @throws IOException
     */
    @Override
    public List<String> list(final String bucketName, String prefix) throws IOException {
        if (prefix.startsWith("/")) prefix = prefix.substring(1);
        try {
            final Iterable<Result<Item>> results = this.mc.listObjects(
                    ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .recursive(true)            // if recursive(false) the output is flat and does not list subpaths
                    .prefix(prefix)
                    .startAfter(prefix)         // can have leading "/" or not; only methd to limit output to a folder
                    .build());

            final ArrayList<String> objectNames = new ArrayList<>();
            final String cacheKey = bucketName + "/" + prefix;
            LinkedHashMap<String, Item> cache = this.objectListCache.get(cacheKey);
            if (cache == null) {
                cache = new LinkedHashMap<>();
                this.objectListCache.put(cacheKey, cache);
            }

            for (final Result<Item> result: results) {
                final Item item = result.get();
                if (!item.isDir()) {
                    cache.put(item.objectName(), item);
                    objectNames.add(item.objectName());
                }
            }

            return objectNames;
        } catch (InvalidKeyException | ErrorResponseException
                | InsufficientDataException | InternalException
                | InvalidResponseException | NoSuchAlgorithmException
                | ServerException | XmlParserException
                | IllegalArgumentException | IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    private LinkedHashMap<String, Item> getItems(final String bucketName, String prefix) throws IOException {
        if (prefix.startsWith("/")) prefix = prefix.substring(1);
        final String cacheKey = bucketName + "/" + prefix;
        LinkedHashMap<String, Item> cache = this.objectListCache.get(cacheKey);
        if (cache == null) {
            this.list(bucketName, prefix);
            cache = this.objectListCache.get(cacheKey);
        }
        if (cache == null) throw new IOException("prefix " + prefix + " in bucket " + bucketName + " does not exist");
        return cache;
    }

    private Item getItem(final String bucketName, String objectName) throws IOException {
        if (objectName.startsWith("/")) objectName = objectName.substring(1);
        final int p = objectName.lastIndexOf("/");
        final String prefix = p < 0 ? "" : objectName.substring(0, p);
        final LinkedHashMap<String, Item> cache = this.getItems(bucketName, prefix);
        final Item item = cache.get(objectName);
        if (item == null) throw new IOException("object " + objectName + " in bucket " + bucketName + " does not exist (2)");
        return item;
    }

    /**
     * calculate the disk usage in a given path
     * @param bucketName
     * @param prefix
     * @return
     * @throws IOException
     */
    @Override
    public long diskUsage(final String bucketName, final String prefix) throws IOException {
        final Collection<Item> items = this.getItems(bucketName, prefix).values();
        long du = 0;
        for (final Item item: items) du += item.size();
        return du;
    }

    /**
     * last-modified date of an object
     * @param bucketName
     * @param objectName
     * @return
     * @throws IOException
     */
    @Override
    public long lastModified(final String bucketName, final String objectName) throws IOException {
        final Item item = this.getItem(bucketName, objectName);
        return item.lastModified().toEpochSecond() * 1000;
    }

    /**
     * size of an object
     * @param bucketName
     * @param objectName
     * @return
     * @throws IOException
     */
    @Override
    public long size(final String bucketName, final String objectName) throws IOException {
        final Item item = this.getItem(bucketName, objectName);
        return item.size();
    }

}
