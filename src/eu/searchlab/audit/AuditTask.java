package eu.searchlab.audit;

public interface AuditTask {

    /**
     * the check method is called every time an audit taks shall perform a timed action
     */
    public void check();

}
