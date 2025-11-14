import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Warehouse {

    private final String warehouseID;

    private final PartRequestManager requestManager;
    private final List<Robot> robots;
    private final List<ChargingStation> stations;

    private final Dispatcher dispatcher;
    private final LoggerUtil logger;

    public Warehouse(String warehouseID, String name, Inventory inventory, int robotCount, int stationCount) {
        this.warehouseID = warehouseID;

        // create logger for this warehouse  // important
        this.logger = new LoggerUtil("Warehouse-" + warehouseID);

        this.requestManager = new PartRequestManager(inventory);
        this.robots = new ArrayList<>();
        this.stations = new ArrayList<>();

        // create robots  // important
        createRobots(robotCount);

        // create charging stations  // important
        createStations(stationCount);

        // dispatcher controls robots and stations  // important
        this.dispatcher = new Dispatcher(robots, stations, inventory, requestManager, logger);

        logger.log("warehouse " + warehouseID + " (" + name + ") initialized with "
                + robots.size() + " robots and " + stations.size() + " stations.");
    }

    public void update() {
        // update robots first (their autonomous behavior)  // important
        updateRobots();

        // update charging stations  // important
        updateStations();

        // read new part requests if any exist  // important
        requestManager.update();

        // dispatcher manages charging, task assignment, completion  // important
        dispatcher.dispatchAndManageTasks();

        logger.log("warehouse " + warehouseID + " completed update cycle.");
    }

    public void writeFinalReport() {
        String filename = "completed_report.dat";
        logger.log("writing final binary report to " + filename + "...");

        List<PartRequest> completedRequests = dispatcher.getCompletedRequests();

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filename))) {
            dos.writeInt(completedRequests.size());
            for (PartRequest req : completedRequests) {
                dos.writeUTF(req.requestID());
                dos.writeUTF(req.part().partID());
                dos.writeInt(req.neededQuantity());
                dos.writeUTF(req.status().toString());
            }
            logger.log("final report written. total requests: " + completedRequests.size());
        } catch (IOException e) {
            logger.log("error writing final report: " + e.getMessage());
        }
    }

    // --- component creation methods ---

    private void createRobots(int count) {
        for (int i = 0; i < count; i++) {
            String robotID = "R-" + String.format("%03d", i);
            LoggerUtil robotLogger = new LoggerUtil("Robot-" + robotID);
            Robot robot = new Robot(robotID, robotLogger);
            this.robots.add(robot);
        }
        logger.log("created " + robots.size() + " robots, each with its own logger.");
    }

    private void createStations(int count) {
        for (int i = 0; i < count; i++) {
            String stationID = "CS-" + (char) ('A' + i);
            ChargingStation station = new ChargingStation(stationID);
            this.stations.add(station);
        }
        logger.log("created " + stations.size() + " charging stations.");
    }

    // --- internal update methods ---

    private void updateRobots() {
        for (Robot robot : robots) {
            robot.update();  // robot acts autonomously  // important
        }
    }

    private void updateStations() {
        for (ChargingStation station : stations) {
            station.update();  // station updates charging timers  // important
        }
    }

    // --- getters ---

    public List<Robot> getRobots() {
        return robots;
    }

    public List<ChargingStation> getStations() {
        return stations;
    }

}