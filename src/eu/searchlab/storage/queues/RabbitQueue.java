/**
 *  RabbitQueue
 *  Copyright 14.01.2017 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.queues;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;

import eu.searchlab.tools.Logger;

public class RabbitQueue extends AbstractQueue implements Queue {

    private final RabbitQueueFactory factory;
    private final String queueName;
    private final SortedMap<Long, BlockingQueue<Boolean>> unconfirmedSet;
    private Channel channel;

    protected RabbitQueue(final RabbitQueueFactory factory, final String queueName) throws IOException {
        this.factory = factory;
        this.queueName = queueName;
        this.unconfirmedSet = Collections.synchronizedSortedMap(new TreeMap<>());
        connect();
    }

    private void connect() throws IOException {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-queue-mode", this.factory.lazy.get() ? "lazy" : "default"); // we want to minimize memory usage; see http://www.rabbitmq.com/lazy-queues.html
        if (this.factory.queueLimit.get() > 0) {
            arguments.put("x-max-length", this.factory.queueLimit.get());
            arguments.put("x-overflow", "reject-publish");
        }
        this.channel = this.factory.getChannel();
        try {
            this.channel.queueDeclare(this.queueName, true, false, false, arguments);
        } catch (final Throwable e) {
            // we first try to delete the old queue, but only if it is not used and if empty
            try {
                this.channel = this.factory.connection.createChannel();
                this.channel.queueDelete(this.queueName, true, true);
            } catch (final Throwable ee) {}

            // try again
            try {
                this.channel = this.factory.connection.createChannel();
                this.channel.queueDeclare(this.queueName, true, false, false, arguments);
            } catch (final Throwable ee) {
                // that did not work. Try to modify the call to match with the previous queueDeclare
                final String ec = ee.getCause() == null ? ee.getMessage() : ee.getCause().getMessage();
                if (ec != null && ec.contains("'signedint' but current is none")) {
                    arguments.remove("x-max-length");
                    arguments.remove("x-overflow");
                }
                //arguments.put("x-queue-mode", lazy.get() ? "default" : "lazy");
                try {
                    this.channel = this.factory.connection.createChannel();
                    this.channel.queueDeclare(this.queueName, true, false, false, arguments);
                } catch (final Throwable eee) {
                    throw new IOException(eee.getMessage());
                }
            }
        }
        this.channel.confirmSelect(); // declare that the channel sends confirmations
        this.channel.addConfirmListener(
                new ConfirmCallback() { // ack
                    @Override
                    public void handle(final long seqNo, final boolean multiple) throws IOException {
                        if (multiple) {
                            final Map<Long, BlockingQueue<Boolean>> m = RabbitQueue.this.unconfirmedSet.headMap(seqNo + 1);
                            m.forEach((s, b) -> b.add(Boolean.TRUE));
                            m.clear();
                        } else {
                            final BlockingQueue<Boolean> b = RabbitQueue.this.unconfirmedSet.remove(seqNo);
                            assert b != null;
                            if (b != null) b.add(Boolean.TRUE);
                        }
                    }},
                new ConfirmCallback() { // nack
                        @Override
                        public void handle(final long seqNo, final boolean multiple) throws IOException {
                            if (multiple) {
                                final Map<Long, BlockingQueue<Boolean>> m = RabbitQueue.this.unconfirmedSet.headMap(seqNo + 1);
                                m.forEach((s, b) -> b.add(Boolean.FALSE));
                                m.clear();
                            } else {
                                final BlockingQueue<Boolean> b = RabbitQueue.this.unconfirmedSet.remove(seqNo);
                                assert b != null;
                                if (b != null) b.add(Boolean.FALSE);
                            }
                        }}
                );
    }

    @Override
    public void checkConnection() throws IOException {
        available();
    }

    @Override
    public void send(final byte[] message) throws IOException {
        try {
            sendInternal(message);
        } catch (final IOException e) {
            if (e.getMessage().equals(RabbitQueueFactory.TARGET_LIMIT_MESSAGE)) throw e;
            // try again
            Logger.warn(this.getClass(), "RabbitQueueFactory.send: re-connecting broker");
            connect() ;
            sendInternal(message);
        }
    }
    private void sendInternal(final byte[] message) throws IOException {
        final BlockingQueue<Boolean> semaphore = new ArrayBlockingQueue<>(1);
        final long seqNo = this.channel.getNextPublishSeqNo();
        this.unconfirmedSet.put(seqNo, semaphore);
        this.channel.basicPublish(RabbitQueueFactory.DEFAULT_EXCHANGE, this.queueName, MessageProperties.PERSISTENT_BASIC, message);
        // wait for confirmation
        try {
            final Boolean delivered = semaphore.poll(10, TimeUnit.SECONDS);
            if (delivered == null) throw new IOException("message sending timeout");
            if (delivered) return;
            throw new IOException(RabbitQueueFactory.TARGET_LIMIT_MESSAGE);
        } catch (final InterruptedException x) {
            this.unconfirmedSet.remove(seqNo); // prevent a memory leak
            throw new IOException("message sending interrupted");
        }
    }

    @Override
    public MessageContainer receive(long timeout, final boolean autoAck) throws IOException {
        if (timeout <= 0) timeout = Long.MAX_VALUE;
        final long termination = timeout <= 0 || timeout == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;
        Throwable ee = null;
        while (System.currentTimeMillis() < termination) {
            ee = null;
            try {
                final GetResponse response = this.channel.basicGet(this.queueName, autoAck);
                if (response != null) {
                    final Envelope envelope = response.getEnvelope();
                    final long deliveryTag = envelope.getDeliveryTag();
                    //channel.basicAck(deliveryTag, false);
                    return new MessageContainer(response.getBody(), deliveryTag);
                }
                //Logger.warn(this.getClass(), "receive failed: response empty");
            } catch (final Throwable e) {
                Logger.warn(this.getClass(), "receive failed: " + e.getMessage(), e);
                connect() ;
                ee = e;
                //autoAck = ! autoAck;
            }
            try {Thread.sleep(1000);} catch (final InterruptedException e) {return null;}
        }
        if (ee == null) return null;
        throw new IOException(ee.getMessage());
    }

    @Override
    public void acknowledge(final long deliveryTag) throws IOException {
        try {
            this.channel.basicAck(deliveryTag, false);
        } catch (IOException | AlreadyClosedException e) {
            // try again
            Logger.warn(this.getClass(), "RabbitQueueFactory.acknowledge: re-connecting broker");
            connect() ;
            this.channel.basicAck(deliveryTag, false);
        }
    }

    @Override
    public void reject(final long deliveryTag) throws IOException {
        try {
            this.channel.basicReject(deliveryTag, true);
        } catch (IOException | AlreadyClosedException e) {
            // try again
            Logger.warn(this.getClass(), "RabbitQueueFactory.reject: re-connecting broker");
            connect() ;
            this.channel.basicReject(deliveryTag, false);
        }
    }

    @Override
    public void recover() throws IOException {
        try {
            this.channel.basicRecover(true);
        } catch (IOException | AlreadyClosedException e) {
            // try again
            Logger.warn(this.getClass(), "RabbitQueueFactory.recover: re-connecting broker");
            connect() ;
            this.channel.basicRecover(true);
        }
    }

    @Override
    public long available() throws IOException {
        try {
            return availableInternal();
        } catch (IOException | AlreadyClosedException e) {
            // try again
            Logger.warn(this.getClass(), "RabbitQueueFactory.available: re-connecting broker");
            connect() ;
            return availableInternal();
        }
    }
    private int availableInternal() throws IOException {
        //int a = channel.queueDeclarePassive(this.queueName).getMessageCount();
        final int b = (int) this.channel.messageCount(this.queueName);
        //assert a == b;
        return b;
    }

    @Override
    public void close() throws IOException {
        if (this.channel != null) try {
            this.channel.close();
        } catch (IOException | TimeoutException e) {
        }
    }
}
