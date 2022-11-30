package eu.searchlab.storage.queues;

public class QueueStats {

    private long total;
    private long ready;
    private long unacknowledged;

    public QueueStats() {
        this.total = 0;
        this.ready = 0;
        this.unacknowledged = 0;
    }

    public QueueStats(long total, long ready, long unacknowledged) {
        this.total = total;
        this.ready = ready;
        this.unacknowledged = unacknowledged;
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

    public long getTotal() {
        return this.total;
    }

    public long getReady() {
        return this.ready;
    }

    public long getUnacknowledged() {
        return this.unacknowledged;
    }

    @Override
    public String toString() {
        return this.total + " messages, " +this.ready + " ready, " + this.unacknowledged + " unacknowledged";
    }
}
