/**
 *  GenericIOStorageFactory
 *  Copyright 28.1.2017 by Michael Peter Christen, @orbiterlab
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

package net.yacy.grid.io.assets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.io.S3IO;

public class GenericIOStorageFactory implements StorageFactory<byte[]> {

    private URL url;
    private String endpointURL, bucketName;
    private GenericIO io;

    public GenericIOStorageFactory(final String endpointURL, final String bucketName, final String accessKey, final String secretKey) {
        try {
            this.url = new URL(endpointURL);
        } catch (MalformedURLException e) {
            this.url = null;
        }
        this.endpointURL = endpointURL;
        this.bucketName = bucketName;
        this.io = new S3IO(endpointURL, accessKey, secretKey);
    }

    @Override
    public String getSystem() {
        return "s3";
    }

    @Override
    public String getHost() {
        return this.url.getHost();
    }

    @Override
    public boolean hasDefaultPort() {
        return this.url.getPort() == 9000;
    }

    @Override
    public int getPort() {
        return this.url.getPort();
    }

    @Override
    public String getConnectionURL() {
        return this.endpointURL;
    }

    @Override
    public Storage<byte[]> getStorage() throws IOException {
        // TODO Auto-generated method stub
        return new Storage<byte[]>() {

            @Override
            public void checkConnection() throws IOException {
                if (!GenericIOStorageFactory.this.io.bucketExists(GenericIOStorageFactory.this.bucketName))
                    throw new IOException("bucket " + GenericIOStorageFactory.this.bucketName + " does not exist");
            }

            @Override
            public StorageFactory<byte[]> store(String path, byte[] asset) throws IOException {
                IOPath iop = new IOPath(GenericIOStorageFactory.this.bucketName, path);
                GenericIOStorageFactory.this.io.write(iop, asset);
                return GenericIOStorageFactory.this;
            }

            @Override
            public Asset<byte[]> load(String path) throws IOException {
                IOPath iop = new IOPath(GenericIOStorageFactory.this.bucketName, path);
                byte[] b = GenericIOStorageFactory.this.io.readAll(iop);
                return new Asset<byte[]>(GenericIOStorageFactory.this, b);
            }

            @Override
            public void close() {
                // do nothing
            }

        };
    }

    @Override
    public void close() {
        this.io = null;
    }

}
