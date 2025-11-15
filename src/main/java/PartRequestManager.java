import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PartRequestManager {
    private final Queue<PartRequest> requestQueue;
    private final LoggerUtil logger;

    public PartRequestManager(Inventory inventory) {
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.logger = new LoggerUtil("PartRequestManager");

        logger.log("PartRequestManager initialized (GUI mode only).");
    }

    public synchronized void addNewRequest(Part part, int quantity) {
        PartRequest newRequest = PartRequest.create(part, quantity);
        this.requestQueue.add(newRequest);
        logger.log("GUI added new request: " + newRequest);
        notifyAll();
    }


    public synchronized PartRequest getNextRequest() {
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }

    public List<PartRequest> getQueuedRequests() {
        return List.copyOf(requestQueue);
    }
}