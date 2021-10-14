package net.yacy.grid.io.messages;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import eu.searchlab.storage.io.FileIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.io.S3IO;
import eu.searchlab.storage.json.PersistentCord;

public class CordQueueFactory extends PersistentCord implements QueueFactory<byte[]> {

    private URL url;
    private String endpointURL;
    IOPath iop;
    GenericIO io;

    public CordQueueFactory(GenericIO io, IOPath iop) {
        super(io, iop);
        this.endpointURL = (io instanceof S3IO) ? ((S3IO) io).getEndpointURL() : null;
        try {
            this.url = new URL(this.endpointURL);
        } catch (MalformedURLException e) {
            this.url = null;
        }
        try {
            this.io = (io instanceof S3IO) ? new S3IO(this.endpointURL, ((S3IO) io).getAccessKey(), ((S3IO) io).getSecretKey()) : new FileIO(new File("data"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.iop = iop;
    }

    @Override
    public String getHost() {
        return this.url == null ? null : this.url.getHost();
    }

    @Override
    public boolean hasDefaultPort() {
        return this.url == null ? true : this.url.getPort() == 9000;
    }

    @Override
    public int getPort() {
        return this.url == null ? -1 : this.url.getPort();
    }

    @Override
    public String getConnectionURL() {
        return this.endpointURL;
    }

    @Override
    public Queue<byte[]> getQueue(String queueName) throws IOException {
        return new Queue<byte[]>() {

            @Override
            public void checkConnection() throws IOException {
                if (!CordQueueFactory.this.io.bucketExists(CordQueueFactory.this.iop.getBucket()))
                    throw new IOException("bucket " + CordQueueFactory.this.iop.getBucket() + " does not exist");
            }

            @Override
            public Queue<byte[]> send(byte[] message) throws IOException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public MessageContainer<byte[]> receive(long timeout, boolean autoAck) throws IOException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void acknowledge(long deliveryTag) throws IOException {
                // TODO Auto-generated method stub

            }

            @Override
            public void reject(long deliveryTag) throws IOException {
                // TODO Auto-generated method stub

            }

            @Override
            public void recover() throws IOException {
                // TODO Auto-generated method stub

            }

            @Override
            public long available() throws IOException {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public void clear() throws IOException {
                // TODO Auto-generated method stub

            }

        };
    }

    @Override
    public void close() {
    }

}
