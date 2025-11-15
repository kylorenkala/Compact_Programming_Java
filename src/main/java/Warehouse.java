import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Warehouse {

    private final LoggerUtil logger;

    // --- Shared Resources ---
    private final Inventory inventory;
    private final PartRequestManager requestManager;
    private final ConcurrentHashMap<String, PartRequest> allRequests;
    private final BlockingQueue<Robot> chargingQueue;

    // --- Simulation Components (Threads) ---
    private final List<Robot> robots;
    private final List<ChargingStation> stations;

    // --- GUI Control ---
    private ExecutorService executor;
    private volatile boolean simulationRunning = false;

    public Warehouse(int robotCount, int stationCount) {
        String warehouseID = "WH-01";
        String name = "Main Warehouse";
        this.logger = new LoggerUtil("Warehouse-" + warehouseID);

        // 1. Create Parts and Initial Stock
        List<Part> partDefs = PartDefinitions.createSampleParts();
        this.inventory = new Inventory(500, PartDefinitions.getInitialStock(partDefs));

        // 2. Create Shared Resources
        this.allRequests = new ConcurrentHashMap<>();
        this.chargingQueue = new LinkedBlockingQueue<>();
        this.requestManager = new PartRequestManager(inventory);

        // 3. Create Components
        this.robots = new ArrayList<>();
        this.stations = new ArrayList<>();
        createRobots(robotCount);
        createStations(stationCount); // This will now use stationCount correctly

        logger.log("Warehouse " + warehouseID + " (" + name + ") initialized with "
                + robots.size() + " robots and " + stations.size() + " stations.");
    }


    public void startSimulation() {
        if (simulationRunning) {
            logger.log("Simulation already running.");
            return;
        }


        int poolSize = robots.size() + stations.size();
        executor = Executors.newFixedThreadPool(poolSize);
        simulationRunning = true;
        logger.log("=== STARTING WAREHOUSE SIMULATION (Pool size: " + poolSize + ") ===");

        // Start all component threads
        // We use 'forEach' for a cleaner, more modern syntax
        stations.forEach(executor::submit);
        robots.forEach(executor::submit);


        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            logger.log("Simulation main loop interrupted. Shutting down.");
            Thread.currentThread().interrupt();
        }

        logger.log("=== WAREHOUSE SIMULATION STOPPED ===");
        inventory.printInventory();
    }


    public void stopSimulation() {
        if (!simulationRunning) {
            return; // Already stopped
        }
        logger.log("GUI requested simulation stop.");
        this.simulationRunning = false;


        if (executor != null) {
            // This sends an InterruptedException to all running threads
            executor.shutdownNow();
            logger.log("Executor shutdown requested.");
        }
    }


    public boolean queueForCharging(Robot robot, long timeoutMs) {
        if (!simulationRunning) return false;
        try {
            // 'offer' is the correct method for a timed wait.
            boolean accepted = chargingQueue.offer(robot, timeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                logger.log("Robot " + robot.getRobotID() + " timed out waiting for charge. Leaving queue.");
            }
            return accepted;
        } catch (InterruptedException e) {
            logger.log("Robot " + robot.getRobotID() + " interrupted while waiting for charge.");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void addCompletedRequest(PartRequest request) {
        allRequests.put(request.requestID(), request);
    }


    public void writeFinalReport() {
        Path reportPath = Paths.get("completed_report.dat");
        logger.log("Writing final binary report to " + reportPath.toAbsolutePath() + "...");

        // Get a snapshot of the requests. This is thread-safe.
        List<PartRequest> completedRequests = new ArrayList<>(allRequests.values());

        // Use try-with-resources for both the stream and the underlying file
        try (var fos = Files.newOutputStream(reportPath);
             var dos = new DataOutputStream(fos)) {

            dos.writeInt(completedRequests.size());
            for (PartRequest req : completedRequests) {
                dos.writeUTF(req.requestID());
                dos.writeUTF(req.part().partID());
                dos.writeInt(req.neededQuantity());
                dos.writeUTF(req.status().toString());
            }
            logger.log("Final report written successfully. Total requests: " + completedRequests.size());
        } catch (IOException e) {
            logger.log("Error writing final report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Component Creation Methods ---

    private void createRobots(int count) {
        for (int i = 0; i < count; i++) {
            String robotID = "R-" + String.format("%03d", i + 1);
            // LoggerUtil is now handled inside its own constructor
            // Pass the Warehouse (this) to the robot
            Robot robot = new Robot(robotID, this);
            this.robots.add(robot);
        }
        logger.log("Created " + robots.size() + " robots.");
    }

    private void createStations(int count) {

        for (int i = 0; i < count; i++) {
            // --- END FIX ---
            String stationID = "CS-" + (char) ('A' + i);
            LoggerUtil stationLogger = new LoggerUtil("ChargingStation-" + stationID);
            // Pass the shared charging queue to the station
            ChargingStation station = new ChargingStation(stationID, stationLogger, chargingQueue);
            this.stations.add(station);
        }
        logger.log("Created " + stations.size() + " charging stations.");
    }

    // --- Getters for GUI Polling ---

    public boolean isSimulationRunning() {
        return this.simulationRunning;
    }

    public List<Robot> getRobots() {
        return Collections.unmodifiableList(robots);
    }

    public List<ChargingStation> getStations() {
        return Collections.unmodifiableList(stations);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public PartRequestManager getRequestManager() {
        return requestManager;
    }
}