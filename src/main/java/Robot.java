import java.util.Random;

public class Robot implements Runnable {

    // --- Constants ---
    public static final int MAX_BATTERY = 100;
    public static final int LOW_BATTERY_THRESHOLD = 25;
    public static final int TASK_TIME_MS = 3000; // 3 seconds to "do" a task
    public static final int BATTERY_DRAIN_BASE = 30;
    public static final int BATTERY_DRAIN_RANDOM = 20; // Drain will be 30-50
    public static final int CHARGE_RATE_PER_TICK = 10;
    public static final int CHARGE_TICK_MS = 500; // 0.5 seconds per charge tick

    // --- Attributes ---
    private final String robotID;
    private volatile RobotStatus status; // 'volatile' to be visible across threads
    private int batteryLevel;
    private final LoggerUtil logger;
    private final Random random = new Random();
    private PartRequest currentTask; // The task it is currently working on

    // --- NEW FIELD FOR TIMEOUT ---
    /** Stores the system time (ms) when the robot entered the charging queue. */
    private volatile long timeQueued;

    // --- CONCURRENCY: References to shared resources ---
    private final Warehouse warehouse; // To request charging
    private final PartRequestManager requestManager;
    private final Inventory inventory;

    // --- Getters (Thread-Safe) ---
    public String getRobotID() { return robotID; }
    public RobotStatus getStatus() { return status; }
    public int getBatteryLevel() { return batteryLevel; }

    // --- Setters (Thread-Safe) ---
    public synchronized void setStatus(RobotStatus newStatus) {
        this.status = newStatus;
    }

    public synchronized void chargeBattery(int amount) {
        this.batteryLevel += amount;
        if (this.batteryLevel > MAX_BATTERY) {
            this.batteryLevel = MAX_BATTERY;
        }
    }


    // --- Constructor ---
    public Robot(String robotID, LoggerUtil logger, Warehouse warehouse, PartRequestManager requestManager, Inventory inventory) {
        this.robotID = robotID;
        this.status = RobotStatus.IDLE;
        this.batteryLevel = MAX_BATTERY;
        this.logger = logger;
        this.currentTask = null;

        // This new field doesn't need to be initialized here
        this.timeQueued = 0;

        this.warehouse = warehouse;
        this.requestManager = requestManager;
        this.inventory = inventory;

        logger.log("Robot " + robotID + " initialized. Battery: " + batteryLevel + "%, Status: " + status);
    }


    @Override
    public void run() {
        logger.log("Robot thread " + robotID + " started.");
        try {
            while (true) {
                switch (status) {
                    case IDLE:
                        actIdle();
                        break;
                    case WORKING:
                        actWorking();
                        break;
                    case LOW_BATTERY:
                        actRequestCharge();
                        break;

                    // --- MODIFIED CASE ---
                    // This block now contains the timeout logic
                    case WAITING_FOR_CHARGE:
                        // Check if we have been waiting too long (15 seconds)
                        if (System.currentTimeMillis() - this.timeQueued > 15000) {

                            // Try to remove ourselves from the queue
                            // This requires the new removeChargingRequest method in Warehouse.java
                            if (warehouse.removeChargingRequest(this)) {
                                // Successfully removed
                                logger.log("Robot " + robotID + " left the charging queue (waited > 15s). Will try again.");
                                setStatus(RobotStatus.IDLE);
                                break; // Break from the switch
                            } else {
                                // --- THIS IS THE FIX ---
                                // Remove failed, which means a station JUST grabbed us.
                                // Don't sleep. Continue the loop to re-check status immediately.
                                // The station will have had time to set our status to CHARGING.
                                continue;
                                // --- END OF FIX ---
                            }
                        }

                        Thread.sleep(1000); // Wait 1 sec, then check again
                        break;
                    // --- END OF MODIFICATION ---

                    case CHARGING:
                        Thread.sleep(1000); // Wait to be finished
                        break;
                }
                Thread.sleep(100); // Small pause
            }
        } catch (InterruptedException e) {
            logger.log("Robot thread " + robotID + " interrupted and shutting down.");
            if (status == RobotStatus.WORKING && currentTask != null) {
                warehouse.addCompletedRequest(currentTask.withStatus(RequestStatus.FAILED));
                logger.log("Robot " + robotID + " task " + currentTask.requestID() + " marked as FAILED due to shutdown.");
            }
        }
    }

    private void actIdle() throws InterruptedException {
        if (this.batteryLevel <= LOW_BATTERY_THRESHOLD) {
            logger.log("Robot " + robotID + " has low battery (" + batteryLevel + "%). Needs charging.");
            setStatus(RobotStatus.LOW_BATTERY);
            return;
        }

        PartRequest request = requestManager.getNextRequest();
        if (request != null) {
            logger.log("Robot " + robotID + " found task " + request.requestID() + ". Attempting to get stock.");
            try {
                inventory.removeStock(request.part(), request.neededQuantity());
                assignTask(request); // Success
            } catch (InsufficientStockException e) {
                logger.log("Robot " + robotID + " FAILED task " + request.requestID() + ". Reason: " + e.getMessage());
                warehouse.addCompletedRequest(request.withStatus(RequestStatus.FAILED));
            }
        }
    }

    private void actWorking() throws InterruptedException {
        if (currentTask == null) {
            logger.log("ERROR: Robot " + robotID + " in WORKING state with no task.");
            goIdle();
            return;
        }

        logger.log("Robot " + robotID + " starting work on task " + currentTask.requestID());
        Thread.sleep(TASK_TIME_MS);

        int drain = BATTERY_DRAIN_BASE + random.nextInt(BATTERY_DRAIN_RANDOM);
        this.batteryLevel -= drain;
        if (this.batteryLevel < 0) this.batteryLevel = 0;

        logger.log("Robot " + robotID + " finished task " + currentTask.requestID() + ". Battery now: " + batteryLevel + "%");

        // Report completed task
        warehouse.addCompletedRequest(currentTask.withStatus(RequestStatus.COMPLETED));
        goIdle();
    }

    // --- MODIFIED METHOD ---
    private void actRequestCharge() throws InterruptedException {
        logger.log("Robot " + robotID + " is requesting a charging station.");
        setStatus(RobotStatus.WAITING_FOR_CHARGE);

        // This calls the original method in Warehouse.java
        boolean gotInQueue = warehouse.requestCharging(this);

        if (gotInQueue) {
            logger.log("Robot " + robotID + " is in the queue, waiting for a station.");
            // --- NEW LINE ---
            // Record the time we successfully joined the queue
            this.timeQueued = System.currentTimeMillis();
        } else {
            // This 'else' block is now unlikely to be hit due to the unbounded queue,
            // but we leave the original logic just in case.
            logger.log("Robot " + robotID + " could not be added to charging queue. Will try again.");
            setStatus(RobotStatus.IDLE);
        }
    }
    // --- END OF MODIFICATION ---

    private void assignTask(PartRequest request) {
        this.currentTask = request;
        setStatus(RobotStatus.WORKING);
        logger.logTaskReceived(robotID, request.requestID());
        warehouse.addCompletedRequest(request.withStatus(RequestStatus.IN_PROGRESS));
    }

    private void goIdle() {
        this.currentTask = null;
        setStatus(RobotStatus.IDLE);
    }
}