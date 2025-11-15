import java.util.concurrent.BlockingQueue;

public class ChargingStation implements Runnable {

    private final String stationID;
    private final LoggerUtil logger;
    private final BlockingQueue<Robot> chargingQueue;
    private volatile Robot currentRobot = null;

    public ChargingStation(String stationID, LoggerUtil logger, BlockingQueue<Robot> chargingQueue) {
        this.stationID = stationID;
        this.logger = logger;
        this.chargingQueue = chargingQueue;
        logger.log("Charging station " + stationID + " initialized and ready.");
    }

    // --- Getters for GUI ---
    public String getStationID() {
        return stationID;
    }

    public Robot getCurrentRobot() {
        return this.currentRobot;
    }

    @Override
    public void run() {
        logger.log("Station thread " + stationID + " started. Waiting for robots.");
        try {
            // This loop continues until the thread is interrupted
            while (!Thread.currentThread().isInterrupted()) {

                // 1. Wait for a robot to appear in the queue.
                Robot robotToCharge = chargingQueue.take();
                this.currentRobot = robotToCharge;

                // 2. Process the robot..
                try {
                    logger.log("Docked Robot " + robotToCharge.getRobotID() + ". Starting charge.");
                    chargeRobot(robotToCharge);
                    logger.log("Charging complete for Robot " + robotToCharge.getRobotID());
                } finally {
                    logger.log("Releasing Robot " + robotToCharge.getRobotID());
                    robotToCharge.finishCharging();
                    this.currentRobot = null; // Clear robot from GUI
                }
            }
        } catch (InterruptedException e) {
           // logger.log("Station thread " + stationID + " interrupted and shutting down.");
            Thread.currentThread().interrupt(); // Preserve the interrupt status
        }
    }

    private void chargeRobot(Robot robot) throws InterruptedException {
        // 1. Tell the robot it's charging
        robot.startCharging();

        // 2. Charge the robot in a loop
        while (!robot.isFullyCharged()) {
            // Check if simulation was stopped mid-charge
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Charging was interrupted");
            }

            // Simulate one "tick" of charging
            Thread.sleep(Robot.CHARGE_TICK_MS);
            robot.charge(); // This is a thread-safe call
        }
    }
}