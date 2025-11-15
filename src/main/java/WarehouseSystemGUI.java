import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class WarehouseSystemGUI extends JFrame {

    // --- Simulation Backend ---
    private Warehouse warehouse;
    private SwingWorker<Void, Void> simulationWorker;

    // --- UI Components ---
    private final JButton startButton;
    private final JButton stopButton;
    private final JComboBox<Part> partComboBox;
    private final JSpinner quantitySpinner;
    private final JButton addTaskButton;
    private final JComboBox<String> logFileComboBox;
    private final JTextArea logTextArea;
    private final JButton refreshLogButton;

    // --- Simulation config spinners ---
    private final JSpinner robotCountSpinner;
    private final JSpinner stationCountSpinner;

    // --- Table Models ---
    private final DefaultTableModel robotTableModel;
    private final DefaultTableModel inventoryTableModel;
    private final DefaultTableModel stationTableModel;
    private final DefaultTableModel taskQueueTableModel;
    private final JTable robotTable;
    private final JTable inventoryTable;
    private final JTable stationTable;
    private final JTable taskQueueTable;

    // --- UI Update Timer ---
    private final Timer updateTimer;


    public WarehouseSystemGUI() {
        setTitle("Warehouse Control System");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- Initialize Models (must be done before panels) ---
        // --- Column Names ---
        String[] robotColumnNames = {"Robot ID", "Status", "Task ID", "Battery"};
        robotTableModel = new DefaultTableModel(robotColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        robotTable = new JTable(robotTableModel);

        String[] inventoryColumnNames = {"Part ID", "Part Name", "Stock"};
        inventoryTableModel = new DefaultTableModel(inventoryColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        inventoryTable = new JTable(inventoryTableModel);

        String[] stationColumnNames = {"Station ID", "Status", "Charging Robot"};
        stationTableModel = new DefaultTableModel(stationColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        stationTable = new JTable(stationTableModel);

        // --- NEW: Initialize Task Queue Table ---
        // <-- ADDED
        String[] taskQueueColumnNames = {"Task ID", "Part", "Qty", "Status"};
        taskQueueTableModel = new DefaultTableModel(taskQueueColumnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        taskQueueTable = new JTable(taskQueueTableModel);
        // --- END NEW ---

        // --- Initialize Components for Panels ---
        startButton = new JButton("Start Simulation");
        stopButton = new JButton("Stop Simulation");
        partComboBox = new JComboBox<>(new Vector<>(PartDefinitions.createSampleParts()));
        quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        addTaskButton = new JButton("Add Task");
        logFileComboBox = new JComboBox<>();
        logTextArea = new JTextArea();
        refreshLogButton = new JButton("Refresh Log");

        robotCountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 50, 1)); // Default 10
        stationCountSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1)); // Default 5

        // --- Build Panels ---
        JPanel controlPanel = initControlPanel();
        JPanel statusPanel = initStatusPanel();
        JPanel taskPanel = initTaskPanel();

        // --- Layout ---
        add(controlPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.CENTER);
        add(taskPanel, BorderLayout.EAST);

        // --- UI Update Timer (polls every 500ms) ---
        updateTimer = new Timer(500, e -> updateStatusPanels());

        // --- Initial Component State ---
        stopButton.setEnabled(false);
        addTaskButton.setEnabled(false);
    }

    private JPanel initControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        controlPanel.add(new JLabel("Robots:"));
        controlPanel.add(robotCountSpinner);
        controlPanel.add(new JLabel("Stations:"));
        controlPanel.add(stationCountSpinner);

        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        return controlPanel;
    }


    private JPanel initStatusPanel() {
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // --- Robot Panel ---
        statusPanel.add(new JLabel("Robot Status (Live)"));
        statusPanel.add(initTablePanel(robotTable, new int[]{80, 120, 80, 100}));

        robotTable.getColumnModel().getColumn(1).setCellRenderer(new StatusColorRenderer());
        robotTable.getColumnModel().getColumn(3).setCellRenderer(new BatteryCellRenderer());

        // --- Station Panel ---
        statusPanel.add(Box.createVerticalStrut(10));
        statusPanel.add(new JLabel("Charging Station Status (Live)"));
        statusPanel.add(initTablePanel(stationTable, new int[]{80, 100, 120}));

        // --- Inventory Panel ---
        statusPanel.add(Box.createVerticalStrut(10));
        statusPanel.add(new JLabel("Inventory (Live)"));
        statusPanel.add(initTablePanel(inventoryTable, new int[]{80, 200, 60}));

        return statusPanel;
    }


    private JScrollPane initTablePanel(JTable table, int[] widths) {
        table.setFillsViewportHeight(true);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Monospaced", Font.BOLD, 12));
        table.setRowHeight(20);

        TableColumnModel columnModel = table.getColumnModel();
        for (int i = 0; i < widths.length; i++) {
            columnModel.getColumn(i).setPreferredWidth(widths[i]);
        }
        return new JScrollPane(table);
    }



    private JPanel initTaskPanel() {
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        sidePanel.setPreferredSize(new Dimension(350, 150));

        // --- Add Task Panel ---
        JPanel addTaskPanel = new JPanel(new GridBagLayout());
        addTaskPanel.setBorder(BorderFactory.createTitledBorder("Add New Task"));

        // --- THIS LINE IS IMPORTANT ---
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        addTaskPanel.add(new JLabel("Part:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        partComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Part part) {
                    setText(part.name() + " (" + part.partID() + ")");
                }
                return this;
            }
        });
        addTaskPanel.add(partComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        addTaskPanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        addTaskPanel.add(quantitySpinner, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        addTaskPanel.add(addTaskButton, gbc);
        addTaskButton.addActionListener(e -> addNewTask());

        // --- NEW: Task Queue Panel ---
        JPanel taskQueuePanel = new JPanel(new BorderLayout());
        taskQueuePanel.setBorder(BorderFactory.createTitledBorder("Task Queue (Live)"));
        // Give this a max height so it doesn't expand forever
        taskQueuePanel.setMaximumSize(new Dimension(350, 150));
        // Use the existing table helper. Widths are {Task ID, Part, Qty, Status}
        taskQueuePanel.add(initTablePanel(taskQueueTable, new int[]{80, 120, 40, 80}), BorderLayout.CENTER);
        // --- END NEW ---

        // --- Log Viewer Panel ---
        JPanel logViewerPanel = new JPanel(new BorderLayout(5, 5));
        logViewerPanel.setBorder(BorderFactory.createTitledBorder("Log Viewer"));

        JPanel logControlPanel = new JPanel(new BorderLayout(5, 0));
        logControlPanel.add(logFileComboBox, BorderLayout.CENTER);
        logControlPanel.add(refreshLogButton, BorderLayout.EAST);

        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScrollPane = new JScrollPane(logTextArea);

        logViewerPanel.add(logControlPanel, BorderLayout.NORTH);
        logViewerPanel.add(logScrollPane, BorderLayout.CENTER);

        refreshLogButton.addActionListener(e -> refreshLogFileList());
        logFileComboBox.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                loadSelectedLog();
            }
        });
        refreshLogFileList();
        sidePanel.add(addTaskPanel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(taskQueuePanel); //
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(logViewerPanel);

        return sidePanel;
    }

    // --- Simulation Control ---

    private void startSimulation() {
        int robotCount = (int) robotCountSpinner.getValue();
        int stationCount = (int) stationCountSpinner.getValue();

        warehouse = new Warehouse(robotCount, stationCount);

        simulationWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                warehouse.startSimulation();
                return null;
            }
            @Override
            protected void done() {
                stopSimulation();
            }
        };
        simulationWorker.execute();

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        addTaskButton.setEnabled(true);
        robotCountSpinner.setEnabled(false);
        stationCountSpinner.setEnabled(false);

        refreshLogFileList();
        updateTimer.start();
        System.out.println("Simulation started with " + robotCount + " robots and " + stationCount + " stations.");
    }

    private void stopSimulation() {
        if (warehouse != null) {
            warehouse.stopSimulation();
        }
        if (simulationWorker != null && !simulationWorker.isDone()) {
            simulationWorker.cancel(true);
        }

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        addTaskButton.setEnabled(false);
        robotCountSpinner.setEnabled(true);
        stationCountSpinner.setEnabled(true);

        if (updateTimer.isRunning()) {
            updateTimer.stop();
        }
        System.out.println("Simulation stopped.");
        updateStatusPanels();
    }

    // --- UI Update Methods ---

    private void updateStatusPanels() {
        if (warehouse == null) return;

        // --- Update Robot Table ---
        robotTableModel.setRowCount(0);
        List<Robot> robots = warehouse.getRobots();
        robots.stream()
                .sorted(Comparator.comparing(Robot::getRobotID))
                .forEach(robot -> {
                    Vector<Object> row = new Vector<>();
                    row.add(robot.getRobotID());
                    row.add(robot.getStatus());
                    PartRequest task = robot.getCurrentTask();
                    row.add((task == null) ? "---" : task.getRequestID());
                    row.add(robot.getBatteryLevel());
                    robotTableModel.addRow(row);
                });

        // --- Update Station Table ---
        stationTableModel.setRowCount(0);
        List<ChargingStation> stations = warehouse.getStations();
        stations.stream()
                .sorted(Comparator.comparing(ChargingStation::getStationID))
                .forEach(station -> {
                    Vector<Object> row = new Vector<>();
                    row.add(station.getStationID());
                    Robot chargingRobot = station.getCurrentRobot();
                    if (chargingRobot != null) {
                        row.add("CHARGING");
                        row.add(chargingRobot.getRobotID());
                    } else {
                        row.add("IDLE");
                        row.add("---");
                    }
                    stationTableModel.addRow(row);
                });


        // --- Update Inventory Table ---
        inventoryTableModel.setRowCount(0);
        Map<Part, Integer> stockMap = warehouse.getInventory().getStockMap();
        stockMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Part::partID)))
                .forEach(entry -> {
                    Vector<Object> row = new Vector<>();
                    row.add(entry.getKey().partID());
                    row.add(entry.getKey().name());
                    row.add(entry.getValue());
                    inventoryTableModel.addRow(row);
                });

        // --- NEW: Update Task Queue Table ---
        // NOTE: This assumes your backend has these methods.
        taskQueueTableModel.setRowCount(0);

        // 1. Create a new list to hold all active tasks

        // 2. Add all tasks that are PENDING (waiting in the queue)
        List<PartRequest> allActiveTasks = new java.util.ArrayList<>(warehouse.getRequestManager().getQueuedRequests());

        // 3. Add all tasks that are IN_PROGRESS (being worked on by robots)
        for (Robot robot : warehouse.getRobots()) {
            PartRequest currentTask = robot.getCurrentTask();
            if (currentTask != null) {
                allActiveTasks.add(currentTask);
            }
        }

        // 4. Now populate the table with this combined list
        allActiveTasks.stream()
                .sorted(Comparator.comparing(PartRequest::getRequestID)) // Sort by ID
                .forEach(task -> {
                    Vector<Object> row = new Vector<>();
                    row.add(task.getRequestID());
                    row.add(task.part().name()); // <-- WAS: task.getPart().name()
                    row.add(task.neededQuantity()); // <-- WAS: task.getQuantity()
                    row.add(task.status()); // <-- WAS: task.getStatus()
                    taskQueueTableModel.addRow(row);
                });
        // --- END NEW ---
    }

    private void addNewTask() {
        Part selectedPart = (Part) partComboBox.getSelectedItem();
        int quantity = (int) quantitySpinner.getValue();

        if (selectedPart != null && warehouse != null) {
            warehouse.getRequestManager().addNewRequest(selectedPart, quantity);
        }
    }

    private void refreshLogFileList() {
        refreshLogButton.setEnabled(false);
        logFileComboBox.setEnabled(false);
        logTextArea.setText("Refreshing log file list...");

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return LoggerUtil.getLogFiles();
            }

            @Override
            protected void done() {
                try {
                    List<String> logFiles = get();
                    logFileComboBox.removeAllItems();
                    for (String logFile : logFiles) {
                        logFileComboBox.addItem(logFile);
                    }
                    logTextArea.setText("Log list refreshed. Select a file to view.");
                } catch (Exception e) {
                    logTextArea.setText("Error reading log directory: \n" + e.getMessage());
                }
                refreshLogButton.setEnabled(true);
                logFileComboBox.setEnabled(true);
            }
        };
        worker.execute();
    }

    private void loadSelectedLog() {
        String selectedFile = (String) logFileComboBox.getSelectedItem();
        if (selectedFile == null) {
            logTextArea.setText("No log file selected.");
            return;
        }

        refreshLogButton.setEnabled(false);
        logFileComboBox.setEnabled(false);
        logTextArea.setText("Loading " + selectedFile + "...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return LoggerUtil.getLogContent(selectedFile);
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    logTextArea.setText(content);
                    logTextArea.setCaretPosition(0); // Scroll to top
                } catch (Exception e) {
                    logTextArea.setText("Error reading log file: \n" + e.getMessage());
                }
                refreshLogButton.setEnabled(true);
                logFileComboBox.setEnabled(true);
            }
        };
        worker.execute();
    }


    // --- Main Method ---
    public static void main(String[] args) {
        // Run the GUI on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            WarehouseSystemGUI gui = new WarehouseSystemGUI();
            gui.setVisible(true);
        });
    }

    // --- Custom Inner Class for Battery Bar Renderer ---
    static class BatteryCellRenderer extends JProgressBar implements TableCellRenderer {

        public BatteryCellRenderer() {
            super(0, 100);
            setStringPainted(true);
            setFont(new Font("Monospaced", Font.PLAIN, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            int batteryLevel = (Integer) value;
            setValue(batteryLevel);

            if (batteryLevel < Robot.LOW_BATTERY_THRESHOLD) {
                setForeground(Color.RED);
            } else {
                setForeground(new Color(0, 128, 0)); // Dark Green
            }

            return this;
        }
    }

    // --- Custom Inner Class for Status Color Renderer ---
    static class StatusColorRenderer extends DefaultTableCellRenderer {

        private final Color COLOR_CHARGING = new Color(144, 238, 144); // Light Green
        private final Color COLOR_LOW_BATTERY = new Color(255, 210, 120); // Light Orange
        private final Color COLOR_WORKING = new Color(173, 216, 230); // Light Blue

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus,
                    row, column);

            // This assumes your Robot.getStatus() returns an enum RobotStatus
            RobotStatus status = (RobotStatus) value;

            if (!isSelected) {
                switch (status) {
                    case CHARGING:
                        c.setBackground(COLOR_CHARGING);
                        break;
                    case LOW_BATTERY:
                    case WAITING_FOR_CHARGE:
                        c.setBackground(COLOR_LOW_BATTERY);
                        break;
                    case WORKING:
                        c.setBackground(COLOR_WORKING);
                        break;
                    case IDLE:
                    default:
                        c.setBackground(table.getBackground());
                }
            }
            return c;
        }
    }
}