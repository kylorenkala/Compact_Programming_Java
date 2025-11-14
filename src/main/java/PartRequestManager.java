import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PartRequestManager {
    private final Queue<PartRequest> requestQueue;
    private final Inventory inventory;
    private final LoggerUtil logger;

    public PartRequestManager(Inventory inventory) {
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.inventory = inventory;
        this.logger = new LoggerUtil("PartRequestManager");

        // --- REMOVED ---
        // All file creation logic has been removed.

        logger.log("PartRequestManager initialized (GUI mode only).");
    }

    public synchronized void addNewRequest(Part part, int quantity) {
        PartRequest newRequest = PartRequest.create(part, quantity);
        this.requestQueue.add(newRequest); // 'add' is already thread-safe
        logger.log("GUI added new request: " + newRequest);
        notifyAll(); // Wake up any robots waiting in getNextRequest()
    }

    // --- REMOVED ---
    // The run() method is no longer needed.

    // --- REMOVED ---
    // The loadRequestsFromFile() method is no longer needed.


    public synchronized PartRequest getNextRequest() {
        // .poll() retrieves and removes the head of the queue,
        // or returns null if the queue is empty.
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }

    // --- REMOVED ---
    // The stop() method is no longer needed.

    public List<PartRequest> getQueuedRequests() {
        // Return an immutable list snapshot of the current queue
        return List.copyOf(requestQueue);
    }
}