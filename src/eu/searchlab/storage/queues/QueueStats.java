package eu.searchlab.storage.queues;

public class QueueStats {

    private long total;          // total number of messages in queue
    private long ready;          // part of the total messages which are available for dequeueing
    private long unacknowledged; // dequeued but not acknowledged messages
    private long idletime;       // number of milliseconds of idle-time of the queue / time where no listener is using the queue. 0 for "not idle"

    public QueueStats() {
        this.total = 0;
        this.ready = 0;
        this.unacknowledged = 0;
        this.idletime = 0;
    }

    public QueueStats(long total, long ready, long unacknowledged, long idletime) {
        this.total = total;
        this.ready = ready;
        this.unacknowledged = unacknowledged;
        this.idletime = idletime;
    }

    public QueueStats setTotal(long total) {
        this.total = total;
        return this;
    }

    public QueueStats setReady(long ready) {
        this.ready = ready;
        return this;
    }

    public QueueStats setUnacknowledged(long unacknowledged) {
        this.unacknowledged = unacknowledged;
        return this;
    }

    public QueueStats setIdletime(long idletime) {
        this.idletime = idletime;
        return this;
    }

    public long getTotal() {
        return this.total;
    }

    public long getReady() {
        return this.ready;
    }

    public long getUnacknowledged() {
        return this.unacknowledged;
    }

    public long getIdletime() {
        return idletime;
    }

    @Override
    public String toString() {
        return this.total + " messages, " +this.ready + " ready, " + this.unacknowledged + " unacknowledged";
    }
}
