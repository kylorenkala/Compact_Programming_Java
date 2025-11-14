import java.util.ArrayList;
import java.util.List;

public class Dispatcher {

    private final List<Robot> robots;
    private final List<ChargingStation> stations;
    private final Inventory inventory;
    private final PartRequestManager requestManager;

    private final List<PartRequest> completedRequests;
    private final LoggerUtil logger;

    public Dispatcher(List<Robot> robots,
                      List<ChargingStation> stations,
                      Inventory inventory,
                      PartRequestManager requestManager,
                      LoggerUtil logger) {

        this.robots = robots;
        this.stations = stations;
        this.inventory = inventory;
        this.requestManager = requestManager;
        this.logger = logger;

        // list of completed or failed requests  // important
        this.completedRequests = new ArrayList<>();
    }

    public void dispatchAndManageTasks() {
        // check robots needing charging  // important
        dispatchRobotsToCharge();

        // check robots working on tasks  // important
        processWorkingRobots();

        // assign new tasks to idle robots  // important
        dispatchNewTasks();
    }

    public List<PartRequest> getCompletedRequests() {
        return this.completedRequests;
    }

    private void dispatchRobotsToCharge() {
        logger.log("dispatcher: checking robots for charging requirements.");

        for (Robot robot : robots) {
            if (robot.getStatus() == RobotStatus.LOW_BATTERY) {
                ChargingStation availableStation = findAvailableStation();
                if (availableStation != null) {
                    logger.log("dispatcher: sending robot " + robot.getRobotID()
                            + " to station " + availableStation.getStationID());
                    availableStation.dockRobot(robot);
                } else {
                    logger.log("dispatcher: robot " + robot.getRobotID()
                            + " needs charge but all stations are busy.");
                }
            }
        }
    }

    private void processWorkingRobots() {
        for (Robot robot : robots) {
            if (robot.getStatus() == RobotStatus.WORKING) {
                PartRequest finished = robot.performTask();

                if (finished != null) {
                    logger.log("dispatcher: robot " + robot.getRobotID()
                            + " completed request " + finished.requestID());

                    completedRequests.add(finished.withStatus(RequestStatus.COMPLETED));
                }
            }
        }
    }

    private void dispatchNewTasks() {
        if (!requestManager.hasRequests()) return;

        Robot idle = findIdleRobot();
        if (idle == null) return;

        PartRequest request = requestManager.getNextRequest();

        boolean ok = inventory.removeStock(request.part(), request.neededQuantity());
        if (ok) {
            logger.log("dispatcher: assigning request " + request.requestID()
                    + " to robot " + idle.getRobotID());

            idle.assignTask(request);
        } else {
            logger.log("dispatcher: failed request " + request.requestID()
                    + " (not enough stock)");

            completedRequests.add(request.withStatus(RequestStatus.FAILED));
        }
    }

    private Robot findIdleRobot() {
        for (Robot r : robots) {
            if (r.getStatus() == RobotStatus.IDLE &&
                    r.getBatteryLevel() > Robot.LOW_BATTERY_THRESHOLD) {
                return r;
            }
        }
        return null;
    }

    private ChargingStation findAvailableStation() {
        for (ChargingStation station : stations) {
            if (station.isAvailable()) {
                return station;
            }
        }
        return null;
    }
}
