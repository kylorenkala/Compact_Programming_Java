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

public class PartRequestManager implements Runnable {
    private final Queue<PartRequest> requestQueue;
    private final Inventory inventory;
    private static final Path REQUEST_FILE_PATH = Path.of("pending_requests.txt");
    private final LoggerUtil logger;
    private volatile boolean simulationIsRunning = true;
    private static final long FILE_POLL_INTERVAL_MS = 5000; // 5 seconds

    public PartRequestManager(Inventory inventory) {
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.inventory = inventory;
        this.logger = new LoggerUtil("PartRequestManager");

        // Create the file if it doesn't exist
        try {
            if (Files.notExists(REQUEST_FILE_PATH)) {
                Files.createFile(REQUEST_FILE_PATH);
                logger.log("Created new empty pending_requests.txt");
            }
        } catch (IOException e) {
            logger.log("CRITICAL: Could not create pending_requests.txt: " + e.getMessage());
        }
        logger.log("PartRequestManager initialized.");
    }

    public synchronized void addNewRequest(Part part, int quantity) {
        PartRequest newRequest = PartRequest.create(part, quantity);
        this.requestQueue.add(newRequest); // 'add' is already thread-safe
        logger.log("GUI added new request: " + newRequest);
        notifyAll();
    }

    @Override
    public void run() {
        logger.log("PartRequestManager thread started. Polling file every " + FILE_POLL_INTERVAL_MS + "ms.");
        while (simulationIsRunning) {
            try {
                // Periodically load new requests from the file
                loadRequestsFromFile();
                Thread.sleep(FILE_POLL_INTERVAL_MS);
            } catch (RequestProcessingException e) {
                // Log the failure but continue running
                logger.log("CRITICAL: Failed to process part requests file: " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                // This is the signal to shut down
                simulationIsRunning = false;
                // Preserve the interrupt status for the thread
                Thread.currentThread().interrupt();
            }
        }
        logger.log("PartRequestManager thread stopped.");
    }
    public void loadRequestsFromFile() throws RequestProcessingException {
        if (Files.notExists(REQUEST_FILE_PATH)) {
            return; // File doesn't exist, nothing to do
        }

        List<PartRequest> newTasks = new ArrayList<>();

        // --- 1. Read file and parse tasks (NO LOCK) ---
        // This is the slow part that we do outside the lock.
        try (BufferedReader reader = Files.newBufferedReader(REQUEST_FILE_PATH)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",");
                if (parts.length != 2) {
                    logger.log("Invalid request format in file: " + line);
                    continue;
                }

                String partID = parts[0].trim();
                int quantity = Integer.parseInt(parts[1].trim());
                Part part = inventory.findPartById(partID);

                if (part != null) {
                    PartRequest newRequest = PartRequest.create(part, quantity);
                    newTasks.add(newRequest); // Add to local temp list
                    logger.log("Read new request from file: " + newRequest);
                } else {
                    logger.log("Unknown partID from file: " + partID);
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new RequestProcessingException("Error reading request file: " + REQUEST_FILE_PATH, e);
        }

        // Only bother to lock and clear if we found new tasks
        if (!newTasks.isEmpty()) {
            // --- 2. Clear the file (NO LOCK) ---
            try {
                // Overwrite the file with an empty string
                Files.writeString(REQUEST_FILE_PATH, "", StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RequestProcessingException("Could not clear request file: " + REQUEST_FILE_PATH, e);
            }

            synchronized (this) {
                this.requestQueue.addAll(newTasks);
                // Wake up waiting robots since we added tasks
                notifyAll();
            }
        }
    }

    public synchronized PartRequest getNextRequest() {
        return this.requestQueue.poll();
    }

    public boolean hasRequests() {
        return !this.requestQueue.isEmpty();
    }

    public void stop() {
        this.simulationIsRunning = false;
    }

    public List<PartRequest> getQueuedRequests() {
        // Return an immutable list snapshot of the current queue
        return List.copyOf(requestQueue);
    }
}