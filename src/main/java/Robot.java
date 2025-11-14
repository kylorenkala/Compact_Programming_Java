import java.util.Optional;
import java.util.Random;

public class Robot implements Runnable {

    // --- Constants ---
    public static final int MAX_BATTERY = 100;
    public static final int LOW_BATTERY_THRESHOLD = 25;
    private static final int AVG_BATTERY_DRAIN = 40;
    private static final int TASK_DURATION_MS = 10000; // 10 seconds
    private static final long IDLE_POLL_INTERVAL_MS = 1000; // 1 second
    public static final long CHARGE_TICK_MS = 1000; // 1 second
    private static final int CHARGE_PER_TICK = 10;
    private static final long CHARGING_TIMEOUT_MS = 15000; // 15 seconds

    // --- Attributes ---
    private final String robotID;
    private volatile RobotStatus status;
    private volatile int batteryLevel; // VOLATILE fix
    private PartRequest currentTask;
    private final LoggerUtil logger;
    private final Random random = new Random();

    // --- Conductor ---
    private final Warehouse warehouse;

    // --- Constructor ---
    public Robot(String robotID, Warehouse warehouse) {
        this.robotID = robotID;
        this.warehouse = warehouse;
        this.logger = new LoggerUtil("Robot-" + robotID);
        this.status = RobotStatus.IDLE;
        this.batteryLevel = MAX_BATTERY;
        this.currentTask = null;
        logger.log("Robot " + robotID + " initialized. Battery: " + batteryLevel + "%");
    }

    // --- Getters (for GUI) ---
    public String getRobotID() { return robotID; }
    public RobotStatus getStatus() { return status; }
    public int getBatteryLevel() { return batteryLevel; }
    public PartRequest getCurrentTask() { return currentTask; }

    @Override
    public void run() {
        logger.log("Robot " + robotID + " thread started.");
        try {
            while (warehouse.isSimulationRunning()) {
                // This is the robot's main "brain" or state machine.
                switch (status) {
                    case IDLE:
                        handleIdleState();
                        break;
                    case WORKING:
                        handleWorkingState();
                        break;
                    case LOW_BATTERY:
                        handleChargingRequest();
                        break;
                    case WAITING_FOR_CHARGE, CHARGING:
                        Thread.sleep(IDLE_POLL_INTERVAL_MS);
                        break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.log("Robot " + robotID + " thread stopped. Final status: " + status);
    }
    private void handleIdleState() throws InterruptedException {
        // 1. Priority check: Do I need to charge?
        if (batteryLevel <= LOW_BATTERY_THRESHOLD) {
            status = RobotStatus.LOW_BATTERY;
            return;
        }

        // 2. Try to find a new task and secure the stock for it.
        Optional<PartRequest> newTask = findAndSecureTask();

        // 3. If we got a task, set state to WORKING.
        //    If not, we remain IDLE and the loop will repeat.
        if (newTask.isPresent()) {
            this.currentTask = newTask.get();
            this.status = RobotStatus.WORKING;
            logger.log("Stock secured. Starting work on task " + currentTask.requestID());
            // Add to master list as IN_PROGRESS
            warehouse.addCompletedRequest(this.currentTask);
        }
    }

    private Optional<PartRequest> findAndSecureTask() throws InterruptedException {
        synchronized (warehouse.getRequestManager()) {

            // 1. Check for a task
            PartRequest newTask = warehouse.getRequestManager().getNextRequest();

            if (newTask == null) {
                warehouse.getRequestManager().wait(IDLE_POLL_INTERVAL_MS);
                return Optional.empty(); // No task found
            }

            // --- 3. A task was found! ---
            logger.log("Found task " + newTask.requestID() + ". Attempting to get stock.");
            try {
                warehouse.getInventory().removeStock(newTask.part(), newTask.neededQuantity());

                // 5. Stock secured! Return the task to the 'handleIdleState'.
                return Optional.of(newTask.withStatus(RequestStatus.IN_PROGRESS));

            } catch (InsufficientStockException e) {
                // 6. Stock check failed. Log the failure and return empty.
                logger.log("FAILED task " + newTask.requestID() + ": " + e.getMessage());
                warehouse.addCompletedRequest(newTask.withStatus(RequestStatus.FAILED));
                return Optional.empty(); // Stock failed
            }
            // No need to notify, we just consumed.
        }
    }
    private void handleWorkingState() throws InterruptedException {
        if (currentTask == null) {
            // This should not happen, but it's a good defensive check.
            logger.log("Working, but currentTask is null. Returning to IDLE.");
            status = RobotStatus.IDLE;
            return;
        }

        logger.log("Performing task " + currentTask.requestID() + "...");
        Thread.sleep(TASK_DURATION_MS);

        // Task complete, drain battery
        int drain = AVG_BATTERY_DRAIN + random.nextInt(10) - 5; // e.g. 45-55
        this.batteryLevel -= drain;
        if (this.batteryLevel < 0) this.batteryLevel = 0;

        logger.log("Task " + currentTask.requestID() + " complete. Battery: " + batteryLevel + "%");

        // Update task status and add to report
        this.currentTask = this.currentTask.withStatus(RequestStatus.COMPLETED);
        warehouse.addCompletedRequest(this.currentTask);

        // Go idle
        this.currentTask = null;
        // Set next state based on new battery level
        status = (batteryLevel <= LOW_BATTERY_THRESHOLD) ? RobotStatus.LOW_BATTERY : RobotStatus.IDLE;
    }
    private void handleChargingRequest() {
        status = RobotStatus.WAITING_FOR_CHARGE;
        logger.log("Battery low (" + batteryLevel + "%). Queuing for charge.");

        boolean accepted = warehouse.queueForCharging(this, CHARGING_TIMEOUT_MS);

        if (!accepted) {
            // Timed out or was interrupted
            logger.log("Left charging queue (timeout or shutdown). Will try again.");
            // If still low, go back to LOW_BATTERY. If not (e.g., a
            // simulation glitch), go IDLE.
            status = (batteryLevel <= LOW_BATTERY_THRESHOLD) ? RobotStatus.LOW_BATTERY : RobotStatus.IDLE;
        }
    }


    // --- Methods called BY ChargingStation thread ---
    // These methods are how the ChargingStation "controls" us.
    public void startCharging() {
        this.status = RobotStatus.CHARGING;
    }

    public boolean isFullyCharged() {
        return this.batteryLevel >= MAX_BATTERY;
    }

    public void charge() {
        this.batteryLevel += CHARGE_PER_TICK;
        if (this.batteryLevel > MAX_BATTERY) {
            this.batteryLevel = MAX_BATTERY;
        }
    }

    public void finishCharging() {
        this.status = RobotStatus.IDLE;
        this.currentTask = null;
    }
}