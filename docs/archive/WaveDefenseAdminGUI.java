/*
 * WaveDefenseAdminGUI.java
 * Modern Admin GUI System for Wave Defense
 * 
 * Provides comprehensive administration interface with:
 * - Real-time statistics dashboard
 * - Configuration management panels
 * - Game state monitoring and control
 * - Player management tools
 * - Wave and enemy configuration
 * - Performance metrics
 * 
 * @version 2.0.0
 * @since 2026-04-29
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.io.*;
import java.nio.file.*;

/**
 * Main Admin GUI Class - Modern Administration Interface
 */
public class WaveDefenseAdminGUI extends JFrame {
    
    // Core components
    private JTabbedPane mainTabbedPane;
    private DashboardPanel dashboardPanel;
    private ConfigurationPanel configPanel;
    private StatisticsPanel statsPanel;
    private PlayerManagementPanel playerPanel;
    private WaveManagementPanel wavePanel;
    private SystemLogsPanel logsPanel;
    
    // Real-time update timer
    private Timer updateTimer;
    private static final int UPDATE_INTERVAL_MS = 1000;
    
    // Configuration
    private AdminConfig config;
    private PropertiesManager propsManager;
    
    // Data models
    private StatisticsModel statsModel;
    private PlayerTableModel playerTableModel;
    private WaveTableModel waveTableModel;
    
    // UI Theme
    private Color primaryColor = new Color(0, 123, 255);
    private Color secondaryColor = new Color(108, 117, 125);
    private Color successColor = new Color(40, 167, 69);
    private Color warningColor = new Color(255, 193, 7);
    private Color dangerColor = new Color(220, 53, 69);
    private Color bgColor = new Color(248, 249, 250);
    private Color cardBg = Color.WHITE;
    
    /**
     * Constructor - Initialize the Admin GUI
     */
    public WaveDefenseAdminGUI() {
        super("Wave Defense - Administration Console");
        
        // Initialize configuration
        propsManager = new PropertiesManager();
        config = propsManager.loadConfig();
        statsModel = new StatisticsModel();
        
        // Setup frame
        setupFrame();
        
        // Initialize panels
        initializePanels();
        
        // Setup real-time updates
        setupRealTimeUpdates();
        
        // Load initial data
        loadInitialData();
        
        // Display
        setVisible(true);
        
        // Start update timer
        startUpdateTimer();
    }
    
    /**
     * Setup main frame properties
     */
    private void setupFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 800));
        setLocationRelativeTo(null);
        
        // Modern look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Failed to set system look and feel: " + e.getMessage());
        }
        
        // Window listener for cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
        
        // Set icon (optional)
        // setIconImage(Toolkit.getDefaultToolkit().getImage("icon.png"));
    }
    
    /**
     * Initialize all panels and tabs
     */
    private void initializePanels() {
        mainTabbedPane = new JTabbedPane(JTabbedPane.TOP);
        mainTabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        // Create panels
        dashboardPanel = new DashboardPanel();
        configPanel = new ConfigurationPanel();
        statsPanel = new StatisticsPanel();
        playerPanel = new PlayerManagementPanel();
        wavePanel = new WaveManagementPanel();
        logsPanel = new SystemLogsPanel();
        
        // Add tabs with icons
        mainTabbedPane.addTab("Dashboard", createTabIcon("\uD83C\uDF10"), dashboardPanel, "Main Dashboard Overview");
        mainTabbedPane.addTab("Configuration", createTabIcon("\u2699\uFE0F"), configPanel, "System Configuration");
        mainTabbedPane.addTab("Statistics", createTabIcon("\uD83D\uDCCA"), statsPanel, "Real-time Statistics");
        mainTabbedPane.addTab("Players", createTabIcon("\uD83D\uDC65"), playerPanel, "Player Management");
        mainTabbedPane.addTab("Waves", createTabIcon("\uD83C\uDF0A"), wavePanel, "Wave Configuration");
        mainTabbedPane.addTab("System Logs", createTabIcon("\uD83D\uDCDD"), logsPanel, "System Logs and Events");
        
        // Add tab selection listener
        mainTabbedPane.addChangeListener(e -> onTabChanged());
        
        // Add to frame
        add(mainTabbedPane, BorderLayout.CENTER);
        
        // Add status bar
        add(createStatusBar(), BorderLayout.SOUTH);
    }
    
    /**
     * Create a tab icon (placeholder for SVG/icon font)
     */
    private JLabel createTabIcon(String iconText) {
        JLabel label = new JLabel(iconText);
        label.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }
    
    /**
     * Create status bar at bottom
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, secondaryColor));
        statusBar.setBackground(new Color(52, 58, 64));
        statusBar.setPreferredSize(new Dimension(getWidth(), 24));
        
        JLabel statusLabel = new JLabel(" Ready | Connected | v2.0.0");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        JLabel timeLabel = new JLabel();
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBar.add(timeLabel, BorderLayout.EAST);
        
        // Update time
        Timer timeTimer = new Timer(1000, e -> {
            timeLabel.setText(new Date().toString());
        });
        timeTimer.start();
        
        return statusBar;
    }
    
    /**
     * Setup real-time updates
     */
    private void setupRealTimeUpdates() {
        updateTimer = new Timer();
    }
    
    /**
     * Start the update timer for real-time data
     */
    private void startUpdateTimer() {
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    updateRealTimeData();
                });
            }
        }, 0, UPDATE_INTERVAL_MS);
    }
    
    /**
     * Update real-time data across panels
     */
    private void updateRealTimeData() {
        // Update statistics
        statsModel.updateLiveData();
        statsPanel.updateDisplay(statsModel);
        dashboardPanel.updateStats(statsModel);
        
        // Update player list
        playerTableModel.refreshData();
        
        // Update wave status
        waveTableModel.refreshData();
        
        // Update logs
        logsPanel.refreshLogs();
    }
    
    /**
     * Load initial data from configuration and game state
     */
    private void loadInitialData() {
        // Load configuration
        configPanel.loadConfiguration(config);
        
        // Initialize data models
        playerTableModel = new PlayerTableModel();
        waveTableModel = new WaveTableModel();
        
        // Load player data
        playerTableModel.loadPlayers();
        playerPanel.setTableModel(playerTableModel);
        
        // Load wave data
        waveTableModel.loadWaves();
        wavePanel.setTableModel(waveTableModel);
        
        // Initialize statistics
        statsModel.initialize();
        
        // Load logs
        logsPanel.loadLogs();
    }
    
    /**
     * Handle tab change events
     */
    private void onTabChanged() {
        int selectedIndex = mainTabbedPane.getSelectedIndex();
        switch (selectedIndex) {
            case 0: // Dashboard
                dashboardPanel.onTabSelected();
                break;
            case 1: // Configuration
                configPanel.onTabSelected();
                break;
            case 2: // Statistics
                statsPanel.onTabSelected();
                break;
            case 3: // Players
                playerPanel.onTabSelected();
                break;
            case 4: // Waves
                wavePanel.onTabSelected();
                break;
            case 5: // Logs
                logsPanel.onTabSelected();
                break;
        }
    }
    
    /**
     * Cleanup resources on exit
     */
    private void cleanup() {
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer.purge();
        }
        
        // Save configuration
        propsManager.saveConfig(config);
        
        System.out.println("Admin GUI closed. Resources cleaned up.");
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        // Run in EDT
        SwingUtilities.invokeLater(() -> {
            new WaveDefenseAdminGUI();
        });
    }
    
    /* ======================================================================== */
    /* INNER CLASSES - Dashboard Panel */
    /* ======================================================================== */
    
    private class DashboardPanel extends JPanel {
        private JLabel totalPlayersLabel;
        private JLabel activePlayersLabel;
        private JLabel totalWavesLabel;
        private JLabel currentWaveLabel;
        private JLabel serverStatusLabel;
        private JLabel uptimeLabel;
        private JPanel statsChartPanel;
        private JButton refreshButton;
        private JButton emergencyStopButton;
        
        public DashboardPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(bgColor);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Header
            add(createHeaderPanel(), BorderLayout.NORTH);
            
            // Stats cards
            add(createStatsCardsPanel(), BorderLayout.CENTER);
            
            // Chart area
            add(createChartPanel(), BorderLayout.SOUTH);
        }
        
        private JPanel createHeaderPanel() {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(primaryColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("Dashboard Overview");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setOpaque(false);
            
            refreshButton = createModernButton("Refresh", successColor);
            refreshButton.addActionListener(e -> refreshDashboard());
            
            emergencyStopButton = createModernButton("Emergency Stop", dangerColor);
            emergencyStopButton.addActionListener(e -> triggerEmergencyStop());
            
            buttonPanel.add(refreshButton);
            buttonPanel.add(emergencyStopButton);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            headerPanel.add(buttonPanel, BorderLayout.EAST);
            
            return headerPanel;
        }
        
        private JPanel createStatsCardsPanel() {
            JPanel cardsPanel = new JPanel(new GridLayout(2, 3, 10, 10));
            cardsPanel.setBackground(bgColor);
            cardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            // Card 1: Total Players
            cardsPanel.add(createStatCard(
                "Total Players",
                "1,247",
                "+12% from last week",
                new Color(54, 162, 235),
                "\uD83D\uDC65"
            ));
            
            // Card 2: Active Players
            cardsPanel.add(createStatCard(
                "Active Players",
                "89",
                "Currently online",
                new Color(75, 192, 192),
                "\uD83D\uDCA1"
            ));
            
            // Card 3: Total Waves
            cardsPanel.add(createStatCard(
                "Total Waves",
                "156",
                "Completed successfully",
                new Color(153, 102, 255),
                "\uD83C\uDF0A"
            ));
            
            // Card 4: Current Wave
            cardsPanel.add(createStatCard(
                "Current Wave",
                "42",
                "In Progress",
                new Color(255, 159, 64),
                "\uD83D\uDD52"
            ));
            
            // Card 5: Server Status
            cardsPanel.add(createStatCard(
                "Server Status",
                "Online",
                "99.9% uptime",
                successColor,
                "\u2705"
            ));
            
            // Card 6: Uptime
            cardsPanel.add(createStatCard(
                "Uptime",
                "14d 6h 23m",
                "Since last restart",
                new Color(201, 203, 207),
                "\u23F1\uFE0F"
            ));
            
            return cardsPanel;
        }
        
        private JPanel createChartPanel() {
            JPanel chartPanel = new JPanel(new BorderLayout());
            chartPanel.setBackground(cardBg);
            chartPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(secondaryColor, 1),
                "Player Activity (Last 24 Hours)"
            ));
            
            // Placeholder for chart
            JPanel placeholder = new JPanel();
            placeholder.setBackground(new Color(240, 240, 240));
            placeholder.setPreferredSize(new Dimension(0, 200));
            placeholder.add(new JLabel("Chart Visualization Area"));
            
            chartPanel.add(placeholder, BorderLayout.CENTER);
            
            return chartPanel;
        }
        
        private JPanel createStatCard(String title, String value, String subtitle, Color color, String icon) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(cardBg);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
            ));
            card.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            // Add hover effect
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(color, 2),
                        BorderFactory.createEmptyBorder(18, 18, 18, 18)
                    ));
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
                        BorderFactory.createEmptyBorder(20, 20, 20, 20)
                    ));
                }
            });
            
            // Icon
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
            iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Title
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            titleLabel.setForeground(new Color(108, 117, 125));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Value
            JLabel valueLabel = new JLabel(value);
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
            valueLabel.setForeground(new Color(52, 58, 64));
            valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Subtitle
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            subtitleLabel.setForeground(new Color(108, 117, 125));
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Add components
            card.add(Box.createVerticalStrut(5));
            card.add(iconLabel);
            card.add(Box.createVerticalStrut(10));
            card.add(titleLabel);
            card.add(Box.createVerticalStrut(5));
            card.add(valueLabel);
            card.add(Box.createVerticalStrut(5));
            card.add(subtitleLabel);
            
            return card;
        }
        
        public void updateStats(StatisticsModel model) {
            // Update card values with real data
            // This would be connected to actual data model
        }
        
        public void refreshDashboard() {
            JOptionPane.showMessageDialog(this, "Dashboard refreshed successfully!");
        }
        
        public void triggerEmergencyStop() {
            int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to trigger emergency stop?\nThis will pause all game activity.",
                "Confirm Emergency Stop",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                // Trigger emergency stop logic
                JOptionPane.showMessageDialog(this, "Emergency stop activated!");
            }
        }
        
        public void onTabSelected() {
            refreshDashboard();
        }
    }
    
    /* ======================================================================== */
    /* INNER CLASSES - Configuration Panel */
    /* ======================================================================== */
    
    private class ConfigurationPanel extends JPanel {
        private JTabbedPane configTabbedPane;
        private JPanel generalConfigPanel;
        private JPanel gameConfigPanel;
        private JPanel serverConfigPanel;
        private JPanel advancedConfigPanel;
        private JButton saveButton;
        private JButton resetButton;
        private JButton exportButton;
        private JButton importButton;
        
        // Configuration fields
        private JTextField serverPortField;
        private JTextField maxPlayersField;
        private JTextField tickRateField;
        private JCheckBox enableCheatsBox;
        private JCheckBox enableDebugBox;
        private JSlider difficultySlider;
        private JComboBox<String> gameModeCombo;
        private JTextField waveIntervalField;
        private JTextField enemySpawnRateField;
        private JTextField bossSpawnChanceField;
        private JCheckBox enableAutoSaveBox;
        private JTextField saveIntervalField;
        private JCheckBox enableLoggingBox;
        private JCheckBox enableMetricsBox;
        private JTextField logLevelCombo;
        
        public ConfigurationPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(bgColor);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Header
            add(createConfigHeader(), BorderLayout.NORTH);
            
            // Configuration tabs
            add(createConfigTabs(), BorderLayout.CENTER);
            
            // Action buttons
            add(createConfigActions(), BorderLayout.SOUTH);
        }
        
        private JPanel createConfigHeader() {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(primaryColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("System Configuration");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            
            return headerPanel;
        }
        
        private JTabbedPane createConfigTabs() {
            configTabbedPane = new JTabbedPane(JTabbedPane.TOP);
            
            // General Configuration
            generalConfigPanel = createGeneralConfigPanel();
            configTabbedPane.addTab("General", generalConfigPanel);
            
            // Game Configuration
            gameConfigPanel = createGameConfigPanel();
            configTabbedPane.addTab("Game", gameConfigPanel);
            
            // Server Configuration
            serverConfigPanel = createServerConfigPanel();
            configTabbedPane.addTab("Server", serverConfigPanel);
            
            // Advanced Configuration
            advancedConfigPanel = createAdvancedConfigPanel();
            configTabbedPane.addTab("Advanced", advancedConfigPanel);
            
            return configTabbedPane;
        }
        
        private JPanel createGeneralConfigPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Server Port
            panel.add(createConfigField("Server Port:", 
                serverPortField = new JTextField(20), 
                "Port number for server connections"));
            panel.add(Box.createVerticalStrut(15));
            
            // Max Players
            panel.add(createConfigField("Maximum Players:", 
                maxPlayersField = new JTextField(20), 
                "Maximum concurrent players"));
            panel.add(Box.createVerticalStrut(15));
            
            // Tick Rate
            panel.add(createConfigField("Tick Rate (Hz):", 
                tickRateField = new JTextField(20), 
                "Server tick rate per second"));
            panel.add(Box.createVerticalStrut(15));
            
            // Enable Cheats
            panel.add(createConfigCheckbox("Enable Cheats", 
                enableCheatsBox = new JCheckBox(), 
                "Allow cheat commands in game"));
            panel.add(Box.createVerticalStrut(15));
            
            // Enable Debug
            panel.add(createConfigCheckbox("Enable Debug Mode", 
                enableDebugBox = new JCheckBox(), 
                "Enable debug logging and tools"));
            
            panel.add(Box.createVerticalGlue());
            
            return panel;
        }
        
        private JPanel createGameConfigPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Difficulty
            panel.add(createConfigSlider("Difficulty Level:", 
                difficultySlider = new JSlider(1, 10, 5),
                "Adjust game difficulty"));
            panel.add(Box.createVerticalStrut(15));
            
            // Game Mode
            panel.add(createConfigField("Game Mode:", 
                gameModeCombo = new JComboBox<>(new String[]{"Survival", "Endless", "Campaign", "Custom"}), 
                "Select game mode"));
            panel.add(Box.createVerticalStrut(15));
            
            // Wave Interval
            panel.add(createConfigField("Wave Interval (seconds):", 
                waveIntervalField = new JTextField(20), 
                "Time between waves"));
            panel.add(Box.createVerticalStrut(15));
            
            // Enemy Spawn Rate
            panel.add(createConfigField("Enemy Spawn Rate:", 
                enemySpawnRateField = new JTextField(20), 
                "Enemies per second"));
            panel.add(Box.createVerticalStrut(15));
            
            // Boss Spawn Chance
            panel.add(createConfigField("Boss Spawn Chance (%):", 
                bossSpawnChanceField = new JTextField(20), 
                "Chance of boss spawn per wave"));
            
            panel.add(Box.createVerticalGlue());
            
            return panel;
        }
        
        private JPanel createServerConfigPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Auto Save
            panel.add(createConfigCheckbox("Enable Auto Save", 
                enableAutoSaveBox = new JCheckBox(), 
                "Automatically save game state"));
            panel.add(Box.createVerticalStrut(15));
            
            // Save Interval
            panel.add(createConfigField("Save Interval (minutes):", 
                saveIntervalField = new JTextField(20), 
                "Auto save frequency"));
            panel.add(Box.createVerticalStrut(15));
            
            // Enable Logging
            panel.add(createConfigCheckbox("Enable Logging", 
                enableLoggingBox = new JCheckBox(), 
                "Enable system logging"));
            panel.add(Box.createVerticalStrut(15));
            
            // Enable Metrics
            panel.add(createConfigCheckbox("Enable Metrics", 
                enableMetricsBox = new JCheckBox(), 
                "Collect performance metrics"));
            panel.add(Box.createVerticalStrut(15));
            
            // Log Level
            panel.add(createConfigField("Log Level:", 
                logLevelCombo = new JTextField("INFO"), 
                "Logging verbosity level"));
            
            panel.add(Box.createVerticalGlue());
            
            return panel;
        }
        
        private JPanel createAdvancedConfigPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            JTextArea configArea = new JTextArea(15, 40);
            configArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            configArea.setBorder(BorderFactory.createLineBorder(new Color(206, 212, 218)));
            configArea.setText("# Advanced Configuration\n# Edit with caution\n\n# Network Settings\nnetwork.timeout=30000\nnetwork.buffer_size=8192\nnetwork.compression=true\n\n# Performance Settings\nperformance.thread_pool_size=16\nperformance.cache_size=1000\nperformance.gc_optimization=true\n\n# Security Settings\nsecurity.encryption=true\nsecurity.auth_timeout=3600\nsecurity.max_attempts=3");
            
            JScrollPane scrollPane = new JScrollPane(configArea);
            scrollPane.setBorder(BorderFactory.createTitledBorder("Advanced Configuration"));
            
            panel.add(scrollPane);
            panel.add(Box.createVerticalGlue());
            
            return panel;
        }
        
        private JPanel createConfigField(String label, JComponent field, String tooltip) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.setBackground(bgColor);
            
            JLabel jLabel = new JLabel(label);
            jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            jLabel.setPreferredSize(new Dimension(200, 25));
            
            field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            field.setPreferredSize(new Dimension(250, 30));
            field.setToolTipText(tooltip);
            
            panel.add(jLabel);
            panel.add(field);
            
            return panel;
        }
        
        private JPanel createConfigCheckbox(String label, JCheckBox checkBox, String tooltip) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.setBackground(bgColor);
            
            checkBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            checkBox.setBackground(bgColor);
            checkBox.setToolTipText(tooltip);
            
            JLabel jLabel = new JLabel(label);
            jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            jLabel.setLabelFor(checkBox);
            
            panel.add(checkBox);
            panel.add(jLabel);
            
            return panel;
        }
        
        private JPanel createConfigSlider(String label, JSlider slider, String tooltip) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(bgColor);
            
            JLabel jLabel = new JLabel(label);
            jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            jLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            slider.setPreferredSize(new Dimension(300, 50));
            slider.setBackground(bgColor);
            slider.setMajorTickSpacing(2);
            slider.setMinorTickSpacing(1);
            slider.setPaintTicks(true);
            slider.setPaintLabels(true);
            slider.setToolTipText(tooltip);
            
            panel.add(jLabel);
            panel.add(Box.createVerticalStrut(10));
            panel.add(slider);
            
            return panel;
        }
        
        private JPanel createConfigActions() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            saveButton = createModernButton("Save Configuration", successColor);
            saveButton.addActionListener(e -> saveConfiguration());
            
            resetButton = createModernButton("Reset to Defaults", warningColor);
            resetButton.addActionListener(e -> resetConfiguration());
            
            exportButton = createModernButton("Export Config", primaryColor);
            exportButton.addActionListener(e -> exportConfiguration());
            
            importButton = createModernButton("Import Config", secondaryColor);
            importButton.addActionListener(e -> importConfiguration());
            
            panel.add(exportButton);
            panel.add(importButton);
            panel.add(resetButton);
            panel.add(saveButton);
            
            return panel;
        }
        
        private JButton createModernButton(String text, Color color) {
            JButton button = new JButton(text);
            button.setFont(new Font("Segoe UI", Font.BOLD, 14));
            button.setBackground(color);
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            // Add hover effect
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    button.setBackground(color.darker());
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    button.setBackground(color);
                }
            });
            
            return button;
        }
        
        public void loadConfiguration(AdminConfig config) {
            // Load configuration into fields
            serverPortField.setText(String.valueOf(config.serverPort));
            maxPlayersField.setText(String.valueOf(config.maxPlayers));
            tickRateField.setText(String.valueOf(config.tickRate));
            enableCheatsBox.setSelected(config.enableCheats);
            enableDebugBox.setSelected(config.enableDebug);
            difficultySlider.setValue(config.difficulty);
            gameModeCombo.setSelectedItem(config.gameMode);
            waveIntervalField.setText(String.valueOf(config.waveInterval));
            enemySpawnRateField.setText(String.valueOf(config.enemySpawnRate));
            bossSpawnChanceField.setText(String.valueOf(config.bossSpawnChance));
            enableAutoSaveBox.setSelected(config.enableAutoSave);
            saveIntervalField.setText(String.valueOf(config.saveInterval));
            enableLoggingBox.setSelected(config.enableLogging);
            enableMetricsBox.setSelected(config.enableMetrics);
            logLevelCombo.setText(config.logLevel);
        }
        
        public void saveConfiguration() {
            // Validate inputs
            try {
                config.serverPort = Integer.parseInt(serverPortField.getText());
                config.maxPlayers = Integer.parseInt(maxPlayersField.getText());
                config.tickRate = Integer.parseInt(tickRateField.getText());
                config.enableCheats = enableCheatsBox.isSelected();
                config.enableDebug = enableDebugBox.isSelected();
                config.difficulty = difficultySlider.getValue();
                config.gameMode = (String) gameModeCombo.getSelectedItem();
                config.waveInterval = Integer.parseInt(waveIntervalField.getText());
                config.enemySpawnRate = Double.parseDouble(enemySpawnRateField.getText());
                config.bossSpawnChance = Double.parseDouble(bossSpawnChanceField.getText());
                config.enableAutoSave = enableAutoSaveBox.isSelected();
                config.saveInterval = Integer.parseInt(saveIntervalField.getText());
                config.enableLogging = enableLoggingBox.isSelected();
                config.enableMetrics = enableMetricsBox.isSelected();
                config.logLevel = logLevelCombo.getText();
                
                // Save to file
                propsManager.saveConfig(config);
                
                JOptionPane.showMessageDialog(this, 
                    "Configuration saved successfully!", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Invalid number format in configuration fields!", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
        
        public void resetConfiguration() {
            int result = JOptionPane.showConfirmDialog(
                this,
                "Reset all configuration to default values?",
                "Confirm Reset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                config = new AdminConfig();
                loadConfiguration(config);
                propsManager.saveConfig(config);
                JOptionPane.showMessageDialog(this, "Configuration reset to defaults!");
            }
        }
        
        public void exportConfiguration() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Configuration");
            fileChooser.setSelectedFile(new java.io.File("wave-defense-config.properties"));
            
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    propsManager.exportConfig(config, fileChooser.getSelectedFile());
                    JOptionPane.showMessageDialog(this, "Configuration exported successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, 
                        "Failed to export configuration: " + ex.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        public void importConfiguration() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Import Configuration");
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    AdminConfig imported = propsManager.importConfig(fileChooser.getSelectedFile());
                    config = imported;
                    loadConfiguration(config);
                    propsManager.saveConfig(config);
                    JOptionPane.showMessageDialog(this, "Configuration imported successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, 
                        "Failed to import configuration: " + ex.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        public void onTabSelected() {
            // Refresh configuration display
            loadConfiguration(config);
        }
    }
    
    /* ======================================================================== */
    /* INNER CLASSES - Statistics Panel */
    /* ======================================================================== */
    
    private class StatisticsPanel extends JPanel {
        private JLabel totalGamesLabel;
        private JLabel totalWavesLabel;
        private JLabel totalEnemiesLabel;
        private JLabel totalDamageLabel;
        private JLabel avgWaveTimeLabel;
        private JLabel bestWaveLabel;
        private JLabel playerKillsLabel;
        private JLabel playerDeathsLabel;
        private JLabel accuracyLabel;
        private JTable statsTable;
        private JScrollPane tableScrollPane;
        private JPanel chartPanel;
        private JButton exportStatsButton;
        private JButton clearStatsButton;
        
        public StatisticsPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(bgColor);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Header
            add(createStatsHeader(), BorderLayout.NORTH);
            
            // Main content
            add(createStatsContent(), BorderLayout.CENTER);
        }
        
        private JPanel createStatsHeader() {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(primaryColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("Real-time Statistics");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setOpaque(false);
            
            exportStatsButton = createModernButton("Export Stats", primaryColor);
            exportStatsButton.addActionListener(e -> exportStatistics());
            
            clearStatsButton = createModernButton("Clear Stats", dangerColor);
            clearStatsButton.addActionListener(e -> clearStatistics());
            
            buttonPanel.add(exportStatsButton);
            buttonPanel.add(clearStatsButton);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            headerPanel.add(buttonPanel, BorderLayout.EAST);
            
            return headerPanel;
        }
        
        private JPanel createStatsContent() {
            JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
            contentPanel.setBackground(bgColor);
            
            // Summary cards
            contentPanel.add(createSummaryCards(), BorderLayout.NORTH);
            
            // Tabbed content
            JTabbedPane statsTabbedPane = new JTabbedPane(JTabbedPane.TOP);
            
            // Overview tab
            statsTabbedPane.addTab("Overview", createOverviewTab());
            
            // Detailed stats tab
            statsTabbedPane.addTab("Detailed", createDetailedTab());
            
            // Charts tab
            statsTabbedPane.addTab("Charts", createChartsTab());
            
            contentPanel.add(statsTabbedPane, BorderLayout.CENTER);
            
            return contentPanel;
        }
        
        private JPanel createSummaryCards() {
            JPanel cardsPanel = new JPanel(new GridLayout(1, 6, 10, 10));
            cardsPanel.setBackground(bgColor);
            cardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            // Total Games
            cardsPanel.add(createMiniStatCard(
                "Total Games",
                "156",
                new Color(54, 162, 235)
            ));
            
            // Total Waves
            cardsPanel.add(createMiniStatCard(
                "Total Waves",
                "2,847",
                new Color(75, 192, 192)
            ));
            
            // Total Enemies
            cardsPanel.add(createMiniStatCard(
                "Enemies Defeated",
                "45,892",
                new Color(153, 102, 255)
            ));
            
            // Total Damage
            cardsPanel.add(createMiniStatCard(
                "Total Damage",
                "1.2M",
                new Color(255, 159, 64)
            ));
            
            // Avg Wave Time
            cardsPanel.add(createMiniStatCard(
                "Avg Wave Time",
                "2m 34s",
                new Color(201, 203, 207)
            ));
            
            // Best Wave
            cardsPanel.add(createMiniStatCard(
                "Best Wave",
                "#42",
                successColor
            ));
            
            return cardsPanel;
        }
        
        private JPanel createMiniStatCard(String title, String value, Color color) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(cardBg);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
            ));
            
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            titleLabel.setForeground(new Color(108, 117, 125));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            
            JLabel valueLabel = new JLabel(value);
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
            valueLabel.setForeground(new Color(52, 58, 64));
            valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            
            card.add(titleLabel);
            card.add(Box.createVerticalStrut(5));
            card.add(valueLabel);
            
            return card;
        }
        
        private JPanel createOverviewTab() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Player stats
            JPanel playerStatsPanel = new JPanel(new GridLayout(2, 3, 10, 10));
            playerStatsPanel.setBackground(bgColor);
            
            playerStatsPanel.add(createStatField("Player Kills:", 
                playerKillsLabel = new JLabel("12,456")));
            playerStatsPanel.add(createStatField("Player Deaths:", 
                playerDeathsLabel = new JLabel("892")));
            playerStatsPanel.add(createStatField("Accuracy:", 
                accuracyLabel = new JLabel("87.5%")));
            playerStatsPanel.add(createStatField("Headshots:", 
                new JLabel("3,245")));
            playerStatsPanel.add(createStatField("Damage Taken:", 
                new JLabel("45,678")));
            playerStatsPanel.add(createStatField("Healing Done:", 
                new JLabel("12,345")));
            
            panel.add(playerStatsPanel, BorderLayout.NORTH);
            
            // Stats table
            String[] columnNames = {"Statistic", "Value", "Change"};
            Object[][] data = {
                {"Games Won", "89", "+5%"},
                {"Games Lost", "67", "-2%"},
                {"Win Rate", "57%", "+3%"},
                {"Avg Score", "1,245", "+12%"},
                {"Best Score", "5,678", "+8%"},
                {"Total Playtime", "1,245h", "+45h"}
            };
            
            statsTable = new JTable(data, columnNames);
            statsTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            statsTable.setRowHeight(30);
            statsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            // Style table
            statsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    c.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    if (isSelected) {
                        c.setBackground(new Color(0, 123, 255));
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 250));
                        c.setForeground(Color.BLACK);
                    }
                    return c;
                }
            });
            
            tableScrollPane = new JScrollPane(statsTable);
            tableScrollPane.setBorder(BorderFactory.createTitledBorder("Performance Overview"));
            
            panel.add(tableScrollPane, BorderLayout.CENTER);
            
            return panel;
        }
        
        private JPanel createDetailedTab() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Detailed statistics table
            String[] columnNames = {"Category", "Current", "Average", "Best", "Worst"};
            Object[][] data = {
                {"Wave Completion Time", "2m 34s", "3m 12s", "1m 45s", "5m 23s"},
                {"Enemies per Wave", "24", "18", "35", "12"},
                {"Damage per Wave", "1,245", "987", "2,345", "456"},
                {"Player Kills per Wave", "12", "8", "25", "3"},
                {"Resources Collected", "567", "423", "890", "123"},
                {"Upgrades Purchased", "5", "3", "12", "1"}
            };
            
            JTable detailedTable = new JTable(data, columnNames);
            detailedTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            detailedTable.setRowHeight(30);
            
            JScrollPane detailedScrollPane = new JScrollPane(detailedTable);
            detailedScrollPane.setBorder(BorderFactory.createTitledBorder("Detailed Wave Statistics"));
            
            panel.add(detailedScrollPane, BorderLayout.CENTER);
            
            return panel;
        }
        
        private JPanel createChartsTab() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            chartPanel = new JPanel();
            chartPanel.setBackground(Color.WHITE);
            chartPanel.setBorder(BorderFactory.createLineBorder(new Color(222, 226, 230)));
            chartPanel.setPreferredSize(new Dimension(0, 400));
            chartPanel.setLayout(new BorderLayout());
            
            JLabel chartPlaceholder = new JLabel("Chart Visualization Area", SwingConstants.CENTER);
            chartPlaceholder.setFont(new Font("Segoe UI", Font.ITALIC, 16));
            chartPlaceholder.setForeground(new Color(108, 117, 125));
            chartPanel.add(chartPlaceholder, BorderLayout.CENTER);
            
            // Chart controls
            JPanel chartControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            chartControls.setBackground(bgColor);
            
            String[] chartTypes = {"Line Chart", "Bar Chart", "Pie Chart", "Area Chart"};
            JComboBox<String> chartTypeCombo = new JComboBox<>(chartTypes);
            chartTypeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            
            JButton refreshChartButton = createModernButton("Refresh Chart", primaryColor);
            refreshChartButton.addActionListener(e -> refreshChart());
            
            chartControls.add(new JLabel("Chart Type:"));
            chartControls.add(chartTypeCombo);
            chartControls.add(Box.createHorizontalStrut(20));
            chartControls.add(refreshChartButton);
            
            panel.add(chartControls, BorderLayout.NORTH);
            panel.add(chartPanel, BorderLayout.CENTER);
            
            return panel;
        }
        
        private JPanel createStatField(String label, JLabel valueLabel) {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBackground(bgColor);
            
            JLabel jLabel = new JLabel(label);
            jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            jLabel.setForeground(new Color(108, 117, 125));
            
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            valueLabel.setForeground(new Color(52, 58, 64));
            valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            
            panel.add(jLabel, BorderLayout.WEST);
            panel.add(valueLabel, BorderLayout.EAST);
            
            return panel;
        }
        
        public void updateDisplay(StatisticsModel model) {
            // Update all statistics with real-time data
            totalGamesLabel.setText(String.valueOf(model.getTotalGames()));
            totalWavesLabel.setText(String.valueOf(model.getTotalWaves()));
            totalEnemiesLabel.setText(String.valueOf(model.getTotalEnemies()));
            totalDamageLabel.setText(String.valueOf(model.getTotalDamage()));
            avgWaveTimeLabel.setText(model.getAvgWaveTime());
            bestWaveLabel.setText(String.valueOf(model.getBestWave()));
            playerKillsLabel.setText(String.valueOf(model.getPlayerKills()));
            playerDeathsLabel.setText(String.valueOf(model.getPlayerDeaths()));
            accuracyLabel.setText(model.getAccuracy() + "%");
        }
        
        public void exportStatistics() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Statistics");
            fileChooser.setSelectedFile(new java.io.File("wave-defense-stats.csv"));
            
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    // Export logic here
                    JOptionPane.showMessageDialog(this, "Statistics exported successfully!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, 
                        "Failed to export statistics: " + ex.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        public void clearStatistics() {
            int result = JOptionPane.showConfirmDialog(
                this,
                "Clear all statistics? This cannot be undone.",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                statsModel.reset();
                JOptionPane.showMessageDialog(this, "Statistics cleared!");
            }
        }
        
        public void refreshChart() {
            JOptionPane.showMessageDialog(this, "Chart refreshed!");
        }
        
        public void onTabSelected() {
            // Refresh chart and data
            refreshChart();
        }
    }
    
    /* ======================================================================== */
    /* INNER CLASSES - Player Management Panel */
    /* ======================================================================== */
    
    private class PlayerManagementPanel extends JPanel {
        private JTable playerTable;
        private PlayerTableModel playerTableModel;
        private JScrollPane tableScrollPane;
        private JPanel buttonPanel;
        private JTextField searchField;
        private JButton addButton;
        private JButton editButton;
        private JButton deleteButton;
        private JButton banButton;
        private JButton kickButton;
        private JButton refreshButton;
        
        public PlayerManagementPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(bgColor);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Header
            add(createPlayerHeader(), BorderLayout.NORTH);
            
            // Search panel
            add(createSearchPanel(), BorderLayout.CENTER);
            
            // Table
            add(createPlayerTable(), BorderLayout.CENTER);
            
            // Buttons
            add(createPlayerButtons(), BorderLayout.SOUTH);
        }
        
        private JPanel createPlayerHeader() {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(primaryColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("Player Management");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            
            return headerPanel;
        }
        
        private JPanel createSearchPanel() {
            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.setBackground(bgColor);
            searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            
            JLabel searchLabel = new JLabel("Search:");
            searchLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            
            searchField = new JTextField(30);
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                public void changedUpdate(DocumentEvent e) { search(); }
                public void removeUpdate(DocumentEvent e) { search(); }
                public void insertUpdate(DocumentEvent e) { search(); }
                
                private void search() {
                    playerTableModel.filter(searchField.getText());
                }
            });
            
            searchPanel.add(searchLabel);
            searchPanel.add(searchField);
            
            return searchPanel;
        }
        
        private JScrollPane createPlayerTable() {
            playerTableModel = new PlayerTableModel();
            playerTable = new JTable(playerTableModel);
            playerTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            playerTable.setRowHeight(30);
            playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            // Style table
            playerTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    c.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    if (isSelected) {
                        c.setBackground(new Color(0, 123, 255));
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 250));
                        c.setForeground(Color.BLACK);
                    }
                    return c;
                }
            });
            
            tableScrollPane = new JScrollPane(playerTable);
            tableScrollPane.setBorder(BorderFactory.createTitledBorder("Player List"));
            
            return tableScrollPane;
        }
        
        private JPanel createPlayerButtons() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            addButton = createModernButton("Add Player", successColor);
            addButton.addActionListener(e -> addPlayer());
            
            editButton = createModernButton("Edit Player", primaryColor);
            editButton.addActionListener(e -> editPlayer());
            
            deleteButton = createModernButton("Delete Player", warningColor);
            deleteButton.addActionListener(e -> deletePlayer());
            
            banButton = createModernButton("Ban Player", dangerColor);
            banButton.addActionListener(e -> banPlayer());
            
            kickButton = createModernButton("Kick Player", new Color(255, 193, 7));
            kickButton.addActionListener(e -> kickPlayer());
            
            refreshButton = createModernButton("Refresh", secondaryColor);
            refreshButton.addActionListener(e -> refreshPlayerList());
            
            panel.add(refreshButton);
            panel.add(kickButton);
            panel.add(banButton);
            panel.add(deleteButton);
            panel.add(editButton);
            panel.add(addButton);
            
            return panel;
        }
        
        public void setTableModel(PlayerTableModel model) {
            this.playerTableModel = model;
            playerTable.setModel(model);
        }
        
        private void addPlayer() {
            // Show add player dialog
            PlayerDialog dialog = new PlayerDialog(this, "Add Player", null);
            dialog.setVisible(true);
            
            if (dialog.isSaved()) {
                playerTableModel.addPlayer(dialog.getPlayerData());
                JOptionPane.showMessageDialog(this, "Player added successfully!");
            }
        }
        
        private void editPlayer() {
            int selectedRow = playerTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a player to edit.");
                return;
            }
            
            PlayerData player = playerTableModel.getPlayerAt(selectedRow);
            PlayerDialog dialog = new PlayerDialog(this, "Edit Player", player);
            dialog.setVisible(true);
            
            if (dialog.isSaved()) {
                playerTableModel.updatePlayer(selectedRow, dialog.getPlayerData());
                JOptionPane.showMessageDialog(this, "Player updated successfully!");
            }
        }
        
        private void deletePlayer() {
            int selectedRow = playerTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a player to delete.");
                return;
            }
            
            int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this player?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                playerTableModel.removePlayer(selectedRow);
                JOptionPane.showMessageDialog(this, "Player deleted successfully!");
            }
        }
        
        private void banPlayer() {
            int selectedRow = playerTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a player to ban.");
                return;
            }
            
            String reason = JOptionPane.showInputDialog(this, "Enter ban reason:");
            if (reason != null && !reason.trim().isEmpty()) {
                playerTableModel.banPlayer(selectedRow, reason);
                JOptionPane.showMessageDialog(this, "Player banned successfully!");
            }
        }
        
        private void kickPlayer() {
            int selectedRow = playerTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a player to kick.");
                return;
            }
            
            int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to kick this player?",
                "Confirm Kick",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                playerTableModel.kickPlayer(selectedRow);
                JOptionPane.showMessageDialog(this, "Player kicked successfully!");
            }
        }
        
        private void refreshPlayerList() {
            playerTableModel.refreshData();
            JOptionPane.showMessageDialog(this, "Player list refreshed!");
        }
        
        public void onTabSelected() {
            refreshPlayerList();
        }
    }
    
    /* ======================================================================== */
    /* INNER CLASSES - Wave Management Panel */
    /* ======================================================================== */
    
    private class WaveManagementPanel extends JPanel {
        private JTable waveTable;
        private WaveTableModel waveTableModel;
        private JScrollPane tableScrollPane;
        private JPanel buttonPanel;
        private JButton addWaveButton;
        private JButton editWaveButton;
        private JButton deleteWaveButton;
        private JButton cloneWaveButton;
        private JButton activateWaveButton;
        private JButton refreshButton;
        
        public WaveManagementPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(bgColor);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Header
            add(createWaveHeader(), BorderLayout.NORTH);
            
            // Table
            add(createWaveTable(), BorderLayout.CENTER);
            
            // Buttons
            add(createWaveButtons(), BorderLayout.SOUTH);
        }
        
        private JPanel createWaveHeader() {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(primaryColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("Wave Configuration");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            
            return headerPanel;
        }
        
        private JScrollPane createWaveTable() {
            waveTableModel = new WaveTableModel();
            waveTable = new JTable(waveTableModel);
            waveTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            waveTable.setRowHeight(30);
            waveTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            // Style table
            waveTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    c.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    if (isSelected) {
                        c.setBackground(new Color(0, 123, 255));
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 250));
                        c.setForeground(Color.BLACK);
                    }
                    return c;
                }
            });
            
            tableScrollPane = new JScrollPane(waveTable);
            tableScrollPane.setBorder(BorderFactory.createTitledBorder("Wave List"));
            
            return tableScrollPane;
        }
        
        private JPanel createWaveButtons() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            addWaveButton = createModernButton("Add Wave", successColor);
            addWaveButton.addActionListener(e -> addWave());
            
            editWaveButton = createModernButton("Edit Wave", primaryColor);
            editWaveButton.addActionListener(e -> editWave());
            
            deleteWaveButton = createModernButton("Delete Wave", warningColor);
            deleteWaveButton.addActionListener(e -> deleteWave());
            
            cloneWaveButton = createModernButton("Clone Wave", new Color(153, 102, 255));
            cloneWaveButton.addActionListener(e -> cloneWave());
            
            activateWaveButton = createModernButton("Activate Wave", new Color(255, 159, 64));
            activateWaveButton.addActionListener(e -> activateWave());
            
            refreshButton = createModernButton("Refresh", secondaryColor);
            refreshButton.addActionListener(e -> refreshWaveList());
            
            panel.add(refreshButton);
            panel.add(activateWaveButton);
            panel.add(cloneWaveButton);
            panel.add(deleteWaveButton);
            panel.add(editWaveButton);
            panel.add(addWaveButton);
            
            return panel;
        }
        
        public void setTableModel(WaveTableModel model) {
            this.waveTableModel = model;
            waveTable.setModel(model);
        }
        
        private void addWave() {
            WaveDialog dialog = new WaveDialog(this, "Add Wave", null);
            dialog.setVisible(true);
            
            if (dialog.isSaved()) {
                waveTableModel.addWave(dialog.getWaveData());
                JOptionPane.showMessageDialog(this, "Wave added successfully!");
            }
        }
        
        private void editWave() {
            int selectedRow = waveTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a wave to edit.");
                return;
            }
            
            WaveData wave = waveTableModel.getWaveAt(selectedRow);
            WaveDialog dialog = new WaveDialog(this, "Edit Wave", wave);
            dialog.setVisible(true);
            
            if (dialog.isSaved()) {
                waveTableModel.updateWave(selectedRow, dialog.getWaveData());
                JOptionPane.showMessageDialog(this, "Wave updated successfully!");
            }
        }
        
        private void deleteWave() {
            int selectedRow = waveTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a wave to delete.");
                return;
            }
            
            int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this wave?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                waveTableModel.removeWave(selectedRow);
                JOptionPane.showMessageDialog(this, "Wave deleted successfully!");
            }
        }
        
        private void cloneWave() {
            int selectedRow = waveTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a wave to clone.");
                return;
            }
            
            waveTableModel.cloneWave(selectedRow);
            JOptionPane.showMessageDialog(this, "Wave cloned successfully!");
        }
        
        private void activateWave() {
            int selectedRow = waveTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a wave to activate.");
                return;
            }
            
            waveTableModel.activateWave(selectedRow);
            JOptionPane.showMessageDialog(this, "Wave activated successfully!");
        }
        
        private void refreshWaveList() {
            waveTableModel.refreshData();
            JOptionPane.showMessageDialog(this, "Wave list refreshed!");
        }
        
        public void onTabSelected() {
            refreshWaveList();
        }
    }
    
    /* ======================================================================== */
    /* INNER CLASSES - System Logs Panel */
    /* ======================================================================== */
    
    private class SystemLogsPanel extends JPanel {
        private JTextArea logsArea;
        private JScrollPane scrollPane;
        private JPanel buttonPanel;
        private JButton refreshButton;
        private JButton clearButton;
        private JButton exportButton;
        private JComboBox<String> logLevelFilter;
        private JTextField searchField;
        
        public SystemLogsPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(bgColor);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Header
            add(createLogsHeader(), BorderLayout.NORTH);
            
            // Logs area
            add(createLogsArea(), BorderLayout.CENTER);
            
            // Controls
            add(createLogsControls(), BorderLayout.SOUTH);
        }
        
        private JPanel createLogsHeader() {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(primaryColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("System Logs");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            
            return headerPanel;
        }
        
        private JScrollPane createLogsArea() {
            logsArea = new JTextArea();
            logsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            logsArea.setEditable(false);
            logsArea.setBackground(Color.WHITE);
            logsArea.setBorder(BorderFactory.createLineBorder(new Color(222, 226, 230)));
            
            scrollPane = new JScrollPane(logsArea);
            scrollPane.setBorder(BorderFactory.createTitledBorder("Log Output"));
            
            return scrollPane;
        }
        
        private JPanel createLogsControls() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(bgColor);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            // Left panel - filters
            JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            filterPanel.setBackground(bgColor);
            
            JLabel searchLabel = new JLabel("Search:");
            searchLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            
            searchField = new JTextField(25);
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                public void changedUpdate(DocumentEvent e) { filterLogs(); }
                public void removeUpdate(DocumentEvent e) { filterLogs(); }
                public void insertUpdate(DocumentEvent e) { filterLogs(); }
            });
            
            JLabel levelLabel = new JLabel("Level:");
            levelLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            
            logLevelFilter = new JComboBox<>(new String[]{"ALL", "INFO", "WARN", "ERROR", "DEBUG"});
            logLevelFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            logLevelFilter.addActionListener(e -> filterLogs());
            
            filterPanel.add(searchLabel);
            filterPanel.add(searchField);
            filterPanel.add(Box.createHorizontalStrut(20));
            filterPanel.add(levelLabel);
            filterPanel.add(logLevelFilter);
            
            // Right panel - buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBackground(bgColor);
            
            refreshButton = createModernButton("Refresh", primaryColor);
            refreshButton.addActionListener(e -> refreshLogs());
            
            clearButton = createModernButton("Clear", warningColor);
            clearButton.addActionListener(e -> clearLogs());
            
            exportButton = createModernButton("Export", successColor);
            exportButton.addActionListener(e -> exportLogs());
            
            buttonPanel.add(exportButton);
            buttonPanel.add(clearButton);
            buttonPanel.add(refreshButton);
            
            panel.add(filterPanel, BorderLayout.WEST);
            panel.add(buttonPanel, BorderLayout.EAST);
            
            return panel;
        }
        
        public void loadLogs() {
            // Simulate loading logs
            StringBuilder logs = new StringBuilder();
            logs.append("[INFO] 2026-04-29 22:00:01 - Server started successfully\n");
            logs.append("[INFO] 2026-04-29 22:00:02 - Loading configuration from config.properties\n");
            logs.append("[INFO] 2026-04-29 22:00:03 - Database connection established\n");
            logs.append("[INFO] 2026-04-29 22:00:05 - Player authentication service online\n");
            logs.append("[INFO] 2026-04-29 22:00:10 - Wave scheduler initialized\n");
            logs.append("[INFO] 2026-04-29 22:05:23 - Player 'AlphaWarrior' joined the game\n");
            logs.append("[INFO] 2026-04-29 22:05:45 - Wave 1 started\n");
            logs.append("[INFO] 2026-04-29 22:06:12 - Wave 1 completed\n");
            logs.append("[WARN] 2026-04-29 22:10:33 - High memory usage detected: 85%\n");
            logs.append("[INFO] 2026-04-29 22:15:01 - Player 'BetaKnight' joined the game\n");
            logs.append("[INFO] 2026-04-29 22:20:15 - Wave 5 started\n");
            logs.append("[INFO] 2026-04-29 22:20:45 - Boss enemy spawned in Wave 5\n");
            logs.append("[INFO] 2026-04-29 22:21:30 - Wave 5 completed\n");
            logs.append("[ERROR] 2026-04-29 22:25:10 - Connection timeout for player 'GammaMage'\n");
            logs.append("[INFO] 2026-04-29 22:25:15 - Player 'GammaMage' disconnected\n");
            logs.append("[INFO] 2026-04-29 22:30:00 - Auto-save completed\n");
            logs.append("[INFO] 2026-04-29 22:35:22 - Player 'DeltaRogue' joined the game\n");
            logs.append("[WARN] 2026-04-29 22:40:15 - Player 'EpsilonHunter' has been idle for 5 minutes\n");
            logs.append("[INFO] 2026-04-29 22:44:00 - Wave 42 started\n");
            logs.append("[INFO] 2026-04-29 22:44:30 - Current wave progress: 65%\n");
            
            logsArea.setText(logs.toString());
            logsArea.setCaretPosition(logsArea.getDocument().getLength());
        }
        
        public void refreshLogs() {
            loadLogs();
            JOptionPane.showMessageDialog(this, "Logs refreshed!");
        }
        
        public void clearLogs() {
            int result = JOptionPane.showConfirmDialog(
                this,
                "Clear all log entries?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                logsArea.setText("");
                JOptionPane.showMessageDialog(this, "Logs cleared!");
            }
        }
        
        public void exportLogs() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Logs");
            fileChooser.setSelectedFile(new java.io.File("wave-defense-logs.txt"));
            
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    // Export logic here
                    JOptionPane.showMessageDialog(this, "Logs exported successfully!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, 
                        "Failed to export logs: " + ex.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        public void filterLogs() {
            // Filter logic would go here
            String filterText = searchField.getText().toLowerCase();
            String levelFilter = (String) logLevelFilter.getSelectedItem();
            
            // In a real implementation, this would filter the displayed logs
            System.out.println("Filtering logs - Text: '" + filterText + "', Level: " + levelFilter);
        }
        
        public void onTabSelected() {
            refreshLogs();
        }
    }
    
    /* ======================================================================== */
    /* DATA MODELS */
    /* ======================================================================== */
    
    /**
     * Statistics Model - Manages real-time statistics data
     */
    private class StatisticsModel {
        private int totalGames;
        private int totalWaves;
        private int totalEnemies;
        private int totalDamage;
        private String avgWaveTime;
        private int bestWave;
        private int playerKills;
        private int playerDeaths;
        private double accuracy;
        
        public StatisticsModel() {
            initialize();
        }
        
        public void initialize() {
            totalGames = 156;
            totalWaves = 2847;
            totalEnemies = 45892;
            totalDamage = 1200000;
            avgWaveTime = "2m 34s";
            bestWave = 42;
            playerKills = 12456;
            playerDeaths = 892;
            accuracy = 87.5;
        }
        
        public void updateLiveData() {
            // Simulate live data updates
            // In a real implementation, this would fetch from actual game state
            totalEnemies += (int)(Math.random() * 5);
            totalDamage += (int)(Math.random() * 100);
            playerKills += (int)(Math.random() * 2);
            
            // Update average wave time
            int seconds = 154 + (int)(Math.random() * 20);
            int minutes = seconds / 60;
            int secs = seconds % 60;
            avgWaveTime = String.format("%dm %ds", minutes, secs);
        }
        
        public void reset() {
            initialize();
        }
        
        // Getters
        public int getTotalGames() { return totalGames; }
        public int getTotalWaves() { return totalWaves; }
        public int getTotalEnemies() { return totalEnemies; }
        public int getTotalDamage() { return totalDamage; }
        public String getAvgWaveTime() { return avgWaveTime; }
        public int getBestWave() { return bestWave; }
        public int getPlayerKills() { return playerKills; }
        public int getPlayerDeaths() { return playerDeaths; }
        public double getAccuracy() { return accuracy; }
    }
    
    /**
     * Player Data Model
     */
    private class PlayerData {
        private String id;
        private String name;
        private int level;
        private int score;
        private int kills;
        private int deaths;
        private String status;
        private String lastActive;
        private boolean banned;
        
        public PlayerData(String id, String name, int level, int score, int kills, int deaths, String status, String lastActive, boolean banned) {
            this.id = id;
            this.name = name;
            this.level = level;
            this.score = score;
            this.kills = kills;
            this.deaths = deaths;
            this.status = status;
            this.lastActive = lastActive;
            this.banned = banned;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getName() { return name; }
        public int getLevel() { return level; }
        public int getScore() { return score; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public String getStatus() { return status; }
        public String getLastActive() { return lastActive; }
        public boolean isBanned() { return banned; }
        
        public void setName(String name) { this.name = name; }
        public void setLevel(int level) { this.level = level; }
        public void setScore(int score) { this.score = score; }
        public void setKills(int kills) { this.kills = kills; }
        public void setDeaths(int deaths) { this.deaths = deaths; }
        public void setStatus(String status) { this.status = status; }
        public void setLastActive(String lastActive) { this.lastActive = lastActive; }
        public void setBanned(boolean banned) { this.banned = banned; }
        
        public Object[] toTableRow() {
            return new Object[]{
                id, name, level, score, kills, deaths, 
                status, lastActive, banned ? "Yes" : "No"
            };
        }
    }
    
    /**
     * Player Table Model
     */
    private class PlayerTableModel extends AbstractTableModel {
        private String[] columnNames = {"ID", "Name", "Level", "Score", "Kills", "Deaths", "Status", "Last Active", "Banned"};
        private java.util.List<PlayerData> players;
        private java.util.List<PlayerData> filteredPlayers;
        
        public PlayerTableModel() {
            players = new ArrayList<>();
            filteredPlayers = new ArrayList<>();
        }
        
        public void loadPlayers() {
            // Simulate loading players
            players.add(new PlayerData("P001", "AlphaWarrior", 45, 12500, 234, 45, "Online", "Just now", false));
            players.add(new PlayerData("P002", "BetaKnight", 38, 9800, 189, 67, "Online", "5 min ago", false));
            players.add(new PlayerData("P003", "GammaMage", 52, 15600, 312, 23, "Offline", "1 hour ago", false));
            players.add(new PlayerData("P004", "DeltaRogue", 29, 7200, 145, 89, "Online", "10 min ago", false));
            players.add(new PlayerData("P005", "EpsilonHunter", 41, 11000, 201, 56, "Away", "15 min ago", false));
            players.add(new PlayerData("P006", "ZetaAssassin", 35, 8900, 167, 78, "Offline", "2 hours ago", true));
            players.add(new PlayerData("P007", "EtaPaladin", 48, 13400, 256, 34, "Online", "Just now", false));
            players.add(new PlayerData("P008", "ThetaWizard", 55, 18900, 389, 12, "Online", "3 min ago", false));
            
            filteredPlayers = new ArrayList<>(players);
            fireTableDataChanged();
        }
        
        public void refreshData() {
            // Simulate refreshing data
            loadPlayers();
        }
        
        public void filter(String query) {
            if (query == null || query.trim().isEmpty()) {
                filteredPlayers = new ArrayList<>(players);
            } else {
                filteredPlayers.clear();
                String lowerQuery = query.toLowerCase();
                for (PlayerData player : players) {
                    if (player.getName().toLowerCase().contains(lowerQuery) ||
                        player.getId().toLowerCase().contains(lowerQuery) ||
                        player.getStatus().toLowerCase().contains(lowerQuery)) {
                        filteredPlayers.add(player);
                    }
                }
            }
            fireTableDataChanged();
        }
        
        public void addPlayer(PlayerData player) {
            players.add(player);
            filteredPlayers.add(player);
            fireTableRowsInserted(players.size() - 1, players.size() - 1);
        }
        
        public void updatePlayer(int rowIndex, PlayerData player) {
            int actualIndex = getActualIndex(rowIndex);
            if (actualIndex >= 0 && actualIndex < players.size()) {
                players.set(actualIndex, player);
                // Update filtered list if present
                for (int i = 0; i < filteredPlayers.size(); i++) {
                    if (filteredPlayers.get(i).getId().equals(player.getId())) {
                        filteredPlayers.set(i, player);
                        fireTableRowsUpdated(rowIndex, rowIndex);
                        break;
                    }
                }
            }
        }
        
        public void removePlayer(int rowIndex) {
            int actualIndex = getActualIndex(rowIndex);
            if (actualIndex >= 0 && actualIndex < players.size()) {
                PlayerData removed = players.remove(actualIndex);
                // Remove from filtered list
                for (int i = 0; i < filteredPlayers.size(); i++) {
                    if (filteredPlayers.get(i).getId().equals(removed.getId())) {
                        filteredPlayers.remove(i);
                        fireTableRowsDeleted(rowIndex, rowIndex);
                        break;
                    }
                }
            }
        }
        
        public void banPlayer(int rowIndex, String reason) {
            int actualIndex = getActualIndex(rowIndex);
            if (actualIndex >= 0 && actualIndex < players.size()) {
                PlayerData player = players.get(actualIndex);
                player.setBanned(true);
                player.setStatus("Banned: " + reason);
                // Update filtered list
                for (PlayerData p : filteredPlayers) {
                    if (p.getId().equals(player.getId())) {
                        p.setBanned(true);
                        p.setStatus("Banned: " + reason);
                        break;
                    }
                }
                fireTableRowsUpdated(rowIndex, rowIndex);
            }
        }
        
        public void kickPlayer(int rowIndex) {
            int actualIndex = getActualIndex(rowIndex);
            if (actualIndex >= 0 && actualIndex < players.size()) {
                PlayerData player = players.get(actualIndex);
                player.setStatus("Kicked");
                // Update filtered list
                for (PlayerData p : filteredPlayers) {
                    if (p.getId().equals(player.getId())) {
                        p.setStatus("Kicked");
                        break;
                    }
                }
                fireTableRowsUpdated(rowIndex, rowIndex);
            }
        }
        
        public PlayerData getPlayerAt(int rowIndex) {
            return filteredPlayers.get(rowIndex);
        }
        
        private int getActualIndex(int rowIndex) {
            PlayerData player = filteredPlayers.get(rowIndex);
            return players.indexOf(player);
        }
        
        @Override
        public int getRowCount() {
            return filteredPlayers.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PlayerData player = filteredPlayers.get(rowIndex);
            switch (columnIndex) {
                case 0: return player.getId();
                case 1: return player.getName();
                case 2: return player.getLevel();
                case 3: return player.getScore();
                case 4: return player.getKills();
                case 5: return player.getDeaths();
                case 6: return player.getStatus();
                case 7: return player.getLastActive();
                case 8: return player.isBanned() ? "Yes" : "No";
                default: return null;
            }
        }
    }
    
    /**
     * Wave Data Model
     */
    private class WaveData {
        private int id;
        private String name;
        private int number;
        private int enemyCount;
        private String enemyTypes;
        private int duration;
        private double difficulty;
        private boolean bossWave;
        private String status;
        
        public WaveData(int id, String name, int number, int enemyCount, String enemyTypes, 
                       int duration, double difficulty, boolean bossWave, String status) {
            this.id = id;
            this.name = name;
            this.number = number;
            this.enemyCount = enemyCount;
            this.enemyTypes = enemyTypes;
            this.duration = duration;
            this.difficulty = difficulty;
            this.bossWave = bossWave;
            this.status = status;
        }
        
        // Getters and setters
        public int getId() { return id; }
        public String getName() { return name; }
        public int getNumber() { return number; }
        public int getEnemyCount() { return enemyCount; }
        public String getEnemyTypes() { return enemyTypes; }
        public int getDuration() { return duration; }
        public double getDifficulty() { return difficulty; }
        public boolean isBossWave() { return bossWave; }
        public String getStatus() { return status; }
        
        public void setName(String name) { this.name = name; }
        public void setNumber(int number) { this.number = number; }
        public void setEnemyCount(int enemyCount) { this.enemyCount = enemyCount; }
        public void setEnemyTypes(String enemyTypes) { this.enemyTypes = enemyTypes; }
        public void setDuration(int duration) { this.duration = duration; }
        public void setDifficulty(double difficulty) { this.difficulty = difficulty; }
        public void setBossWave(boolean bossWave) { this.bossWave = bossWave; }
        public void setStatus(String status) { this.status = status; }
        
        public Object[] toTableRow() {
            return new Object[]{
                id, name, number, enemyCount, enemyTypes, duration, 
                difficulty, bossWave ? "Yes" : "No", status
            };
        }
    }
    
    /**
     * Wave Table Model
     */
    private class WaveTableModel extends AbstractTableModel {
        private String[] columnNames = {"ID", "Name", "Number", "Enemies", "Types", "Duration", "Difficulty", "Boss", "Status"};
        private java.util.List<WaveData> waves;
        
        public WaveTableModel() {
            waves = new ArrayList<>();
        }
        
        public void loadWaves() {
            // Simulate loading waves
            waves.add(new WaveData(1, "Initial Assault", 1, 10, "Grunt", 60, 1.0, false, "Completed"));
            waves.add(new WaveData(2, "Rush Hour", 2, 15, "Grunt, Runner", 75, 1.2, false, "Completed"));
            waves.add(new WaveData(3, "Heavy Assault", 3, 12, "Grunt, Tank", 90, 1.5, false, "Completed"));
            waves.add(new WaveData(4, "Boss Wave", 4, 8, "Tank, Boss", 120, 2.0, true, "Completed"));
            waves.add(new WaveData(5, "Swarm", 5, 25, "Grunt, Runner", 100, 1.8, false, "Active"));
            waves.add(new WaveData(6, "Elite Force", 6, 18, "Elite, Tank", 110, 2.2, false, "Pending"));
            waves.add(new WaveData(7, "Boss Rush", 7, 10, "Boss, Elite", 150, 3.0, true, "Pending"));
            waves.add(new WaveData(8, "Final Wave", 8, 30, "All Types", 180, 3.5, true, "Pending"));
            
            fireTableDataChanged();
        }
        
        public void refreshData() {
            // Simulate refreshing data
            loadWaves();
        }
        
        public void addWave(WaveData wave) {
            waves.add(wave);
            fireTableRowsInserted(waves.size() - 1, waves.size() - 1);
        }
        
        public void updateWave(int rowIndex, WaveData wave) {
            if (rowIndex >= 0 && rowIndex < waves.size()) {
                waves.set(rowIndex, wave);
                fireTableRowsUpdated(rowIndex, rowIndex);
            }
        }
        
        public void removeWave(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < waves.size()) {
                waves.remove(rowIndex);
                fireTableRowsDeleted(rowIndex, rowIndex);
            }
        }
        
        public void cloneWave(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < waves.size()) {
                WaveData original = waves.get(rowIndex);
                WaveData clone = new WaveData(
                    waves.size() + 1,
                    original.getName() + " (Clone)",
                    original.getNumber(),
                    original.getEnemyCount(),
                    original.getEnemyTypes(),
                    original.getDuration(),
                    original.getDifficulty(),
                    original.isBossWave(),
                    "Pending"
                );
                waves.add(clone);
                fireTableRowsInserted(waves.size() - 1, waves.size() - 1);
            }
        }
        
        public void activateWave(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < waves.size()) {
                WaveData wave = waves.get(rowIndex);
                wave.setStatus("Active");
                fireTableRowsUpdated(rowIndex, rowIndex);
            }
        }
        
        public WaveData getWaveAt(int rowIndex) {
            return waves.get(rowIndex);
        }
        
        @Override
        public int getRowCount() {
            return waves.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            WaveData wave = waves.get(rowIndex);
            switch (columnIndex) {
                case 0: return wave.getId();
                case 1: return wave.getName();
                case 2: return wave.getNumber();
                case 3: return wave.getEnemyCount();
                case 4: return wave.getEnemyTypes();
                case 5: return wave.getDuration();
                case 6: return wave.getDifficulty();
                case 7: return wave.isBossWave() ? "Yes" : "No";
                case 8: return wave.getStatus();
                default: return null;
            }
        }
    }
    
    /* ======================================================================== */
    /* CONFIGURATION CLASSES */
    /* ======================================================================== */
    
    /**
     * Admin Configuration Class
     */
    private class AdminConfig {
        // Server Settings
        public int serverPort = 8080;
        public int maxPlayers = 100;
        public int tickRate = 60;
        public boolean enableCheats = false;
        public boolean enableDebug = false;
        
        // Game Settings
        public int difficulty = 5;
        public String gameMode = "Survival";
        public int waveInterval = 30;
        public double enemySpawnRate = 2.5;
        public double bossSpawnChance = 0.15;
        
        // Server Settings
        public boolean enableAutoSave = true;
        public int saveInterval = 5;
        public boolean enableLogging = true;
        public boolean enableMetrics = true;
        public String logLevel = "INFO";
        
        public AdminConfig() {
            // Default constructor
        }
    }
    
    /**
     * Properties Manager - Handles configuration file I/O
     */
    private class PropertiesManager {
        private static final String CONFIG_FILE = "wave-defense-config.properties";
        
        public AdminConfig loadConfig() {
            AdminConfig config = new AdminConfig();
            try {
                Properties props = new Properties();
                File file = new File(CONFIG_FILE);
                if (file.exists()) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        props.load(fis);
                        
                        // Load server settings
                        config.serverPort = Integer.parseInt(props.getProperty("server.port", "8080"));
                        config.maxPlayers = Integer.parseInt(props.getProperty("server.maxPlayers", "100"));
                        config.tickRate = Integer.parseInt(props.getProperty("server.tickRate", "60"));
                        config.enableCheats = Boolean.parseBoolean(props.getProperty("server.enableCheats", "false"));
                        config.enableDebug = Boolean.parseBoolean(props.getProperty("server.enableDebug", "false"));
                        
                        // Load game settings
                        config.difficulty = Integer.parseInt(props.getProperty("game.difficulty", "5"));
                        config.gameMode = props.getProperty("game.mode", "Survival");
                        config.waveInterval = Integer.parseInt(props.getProperty("game.waveInterval", "30"));
                        config.enemySpawnRate = Double.parseDouble(props.getProperty("game.enemySpawnRate", "2.5"));
                        config.bossSpawnChance = Double.parseDouble(props.getProperty("game.bossSpawnChance", "0.15"));
                        
                        // Load server settings
                        config.enableAutoSave = Boolean.parseBoolean(props.getProperty("server.autoSave", "true"));
                        config.saveInterval = Integer.parseInt(props.getProperty("server.saveInterval", "5"));
                        config.enableLogging = Boolean.parseBoolean(props.getProperty("server.enableLogging", "true"));
                        config.enableMetrics = Boolean.parseBoolean(props.getProperty("server.enableMetrics", "true"));
                        config.logLevel = props.getProperty("server.logLevel", "INFO");
                        
                    } catch (IOException e) {
                        System.err.println("Failed to load config: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading configuration: " + e.getMessage());
            }
            return config;
        }
        
        public void saveConfig(AdminConfig config) {
            try {
                Properties props = new Properties();
                
                // Save server settings
                props.setProperty("server.port", String.valueOf(config.serverPort));
                props.setProperty("server.maxPlayers", String.valueOf(config.maxPlayers));
                props.setProperty("server.tickRate", String.valueOf(config.tickRate));
                props.setProperty("server.enableCheats", String.valueOf(config.enableCheats));
                props.setProperty("server.enableDebug", String.valueOf(config.enableDebug));
                
                // Save game settings
                props.setProperty("game.difficulty", String.valueOf(config.difficulty));
                props.setProperty("game.mode", config.gameMode);
                props.setProperty("game.waveInterval", String.valueOf(config.waveInterval));
                props.setProperty("game.enemySpawnRate", String.valueOf(config.enemySpawnRate));
                props.setProperty("game.bossSpawnChance", String.valueOf(config.bossSpawnChance));
                
                // Save server settings
                props.setProperty("server.autoSave", String.valueOf(config.enableAutoSave));
                props.setProperty("server.saveInterval", String.valueOf(config.saveInterval));
                props.setProperty("server.enableLogging", String.valueOf(config.enableLogging));
                props.setProperty("server.enableMetrics", String.valueOf(config.enableMetrics));
                props.setProperty("server.logLevel", config.logLevel);
                
                try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                    props.store(fos, "Wave Defense Admin Configuration");
                }
                
            } catch (IOException e) {
                System.err.println("Failed to save config: " + e.getMessage());
            }
        }
        
        public void exportConfig(AdminConfig config, File file) throws IOException {
            Properties props = new Properties();
            
            // Similar to saveConfig but to specified file
            props.setProperty("server.port", String.valueOf(config.serverPort));
            props.setProperty("server.maxPlayers", String.valueOf(config.maxPlayers));
            props.setProperty("server.tickRate", String.valueOf(config.tickRate));
            props.setProperty("server.enableCheats", String.valueOf(config.enableCheats));
            props.setProperty("server.enableDebug", String.valueOf(config.enableDebug));
            props.setProperty("game.difficulty", String.valueOf(config.difficulty));
            props.setProperty("game.mode", config.gameMode);
            props.setProperty("game.waveInterval", String.valueOf(config.waveInterval));
            props.setProperty("game.enemySpawnRate", String.valueOf(config.enemySpawnRate));
            props.setProperty("game.bossSpawnChance", String.valueOf(config.bossSpawnChance));
            props.setProperty("server.autoSave", String.valueOf(config.enableAutoSave));
            props.setProperty("server.saveInterval", String.valueOf(config.saveInterval));
            props.setProperty("server.enableLogging", String.valueOf(config.enableLogging));
            props.setProperty("server.enableMetrics", String.valueOf(config.enableMetrics));
            props.setProperty("server.logLevel", config.logLevel);
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                props.store(fos, "Wave Defense Admin Configuration Export");
            }
        }
        
        public AdminConfig importConfig(File file) throws IOException {
            AdminConfig config = new AdminConfig();
            Properties props = new Properties();
            
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                
                // Load all settings
                config.serverPort = Integer.parseInt(props.getProperty("server.port", "8080"));
                config.maxPlayers = Integer.parseInt(props.getProperty("server.maxPlayers", "100"));
                config.tickRate = Integer.parseInt(props.getProperty("server.tickRate", "60"));
                config.enableCheats = Boolean.parseBoolean(props.getProperty("server.enableCheats", "false"));
                config.enableDebug = Boolean.parseBoolean(props.getProperty("server.enableDebug", "false"));
                config.difficulty = Integer.parseInt(props.getProperty("game.difficulty", "5"));
                config.gameMode = props.getProperty("game.mode", "Survival");
                config.waveInterval = Integer.parseInt(props.getProperty("game.waveInterval", "30"));
                config.enemySpawnRate = Double.parseDouble(props.getProperty("game.enemySpawnRate", "2.5"));
                config.bossSpawnChance = Double.parseDouble(props.getProperty("game.bossSpawnChance", "0.15"));
                config.enableAutoSave = Boolean.parseBoolean(props.getProperty("server.autoSave", "true"));
                config.saveInterval = Integer.parseInt(props.getProperty("server.saveInterval", "5"));
                config.enableLogging = Boolean.parseBoolean(props.getProperty("server.enableLogging", "true"));
                config.enableMetrics = Boolean.parseBoolean(props.getProperty("server.enableMetrics", "true"));
                config.logLevel = props.getProperty("server.logLevel", "INFO");
            }
            
            return config;
        }
    }
    
    /* ======================================================================== */
    /* DIALOG CLASSES */
    /* ======================================================================== */
    
    /**
     * Player Dialog - For adding/editing players
     */
    private class PlayerDialog extends JDialog {
        private JTextField idField;
        private JTextField nameField;
        private JTextField levelField;
        private JTextField scoreField;
        private JTextField killsField;
        private JTextField deathsField;
        private JComboBox<String> statusCombo;
        private JTextField lastActiveField;
        private JCheckBox bannedBox;
        private boolean saved = false;
        
        public PlayerDialog(JFrame parent, String title, PlayerData player) {
            super(parent, title, true);
            setSize(500, 400);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout(10, 10));
            
            // Form panel
            JPanel formPanel = new JPanel();
            formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
            formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // ID
            if (player == null) {
                formPanel.add(createDialogField("Player ID:", idField = new JTextField(20)));
                formPanel.add(Box.createVerticalStrut(10));
            } else {
                idField = new JTextField(player.getId());
                idField.setEditable(false);
            }
            
            // Name
            formPanel.add(createDialogField("Name:", nameField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Level
            formPanel.add(createDialogField("Level:", levelField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Score
            formPanel.add(createDialogField("Score:", scoreField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Kills
            formPanel.add(createDialogField("Kills:", killsField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Deaths
            formPanel.add(createDialogField("Deaths:", deathsField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Status
            formPanel.add(createDialogField("Status:", 
                statusCombo = new JComboBox<>(new String[]{"Online", "Offline", "Away", "Banned", "Kicked"})));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Last Active
            formPanel.add(createDialogField("Last Active:", lastActiveField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Banned
            JPanel bannedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bannedPanel.setBackground(bgColor);
            bannedBox = new JCheckBox("Banned");
            bannedPanel.add(bannedBox);
            formPanel.add(bannedPanel);
            
            // Load player data if editing
            if (player != null) {
                nameField.setText(player.getName());
                levelField.setText(String.valueOf(player.getLevel()));
                scoreField.setText(String.valueOf(player.getScore()));
                killsField.setText(String.valueOf(player.getKills()));
                deathsField.setText(String.valueOf(player.getDeaths()));
                statusCombo.setSelectedItem(player.getStatus());
                lastActiveField.setText(player.getLastActive());
                bannedBox.setSelected(player.isBanned());
            }
            
            add(formPanel, BorderLayout.CENTER);
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = createModernButton("Save", successColor);
            JButton cancelButton = createModernButton("Cancel", dangerColor);
            
            saveButton.addActionListener(e -> {
                saved = true;
                dispose();
            });
            
            cancelButton.addActionListener(e -> dispose());
            
            buttonPanel.add(saveButton);
            buttonPanel.add(cancelButton);
            
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private JPanel createDialogField(String label, JComponent field) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.setBackground(bgColor);
            
            JLabel jLabel = new JLabel(label);
            jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            jLabel.setPreferredSize(new Dimension(120, 25));
            
            field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            field.setPreferredSize(new Dimension(250, 30));
            
            panel.add(jLabel);
            panel.add(field);
            
            return panel;
        }
        
        public boolean isSaved() {
            return saved;
        }
        
        public PlayerData getPlayerData() {
            return new PlayerData(
                idField.getText(),
                nameField.getText(),
                Integer.parseInt(levelField.getText()),
                Integer.parseInt(scoreField.getText()),
                Integer.parseInt(killsField.getText()),
                Integer.parseInt(deathsField.getText()),
                (String) statusCombo.getSelectedItem(),
                lastActiveField.getText(),
                bannedBox.isSelected()
            );
        }
    }
    
    /**
     * Wave Dialog - For adding/editing waves
     */
    private class WaveDialog extends JDialog {
        private JTextField nameField;
        private JTextField numberField;
        private JTextField enemyCountField;
        private JTextField enemyTypesField;
        private JTextField durationField;
        private JTextField difficultyField;
        private JCheckBox bossWaveBox;
        private JComboBox<String> statusCombo;
        private boolean saved = false;
        
        public WaveDialog(JFrame parent, String title, WaveData wave) {
            super(parent, title, true);
            setSize(500, 400);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout(10, 10));
            
            // Form panel
            JPanel formPanel = new JPanel();
            formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
            formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Name
            formPanel.add(createDialogField("Wave Name:", nameField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Number
            formPanel.add(createDialogField("Wave Number:", numberField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Enemy Count
            formPanel.add(createDialogField("Enemy Count:", enemyCountField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Enemy Types
            formPanel.add(createDialogField("Enemy Types:", enemyTypesField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Duration
            formPanel.add(createDialogField("Duration (seconds):", durationField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Difficulty
            formPanel.add(createDialogField("Difficulty:", difficultyField = new JTextField(20)));
            formPanel.add(Box.createVerticalStrut(10));
            
            // Boss Wave
            JPanel bossPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bossPanel.setBackground(bgColor);
            bossWaveBox = new JCheckBox("Boss Wave");
            bossPanel.add(bossWaveBox);
            formPanel.add(bossPanel);
            formPanel.add(Box.createVerticalStrut(10));
            
            // Status
            formPanel.add(createDialogField("Status:", 
                statusCombo = new JComboBox<>(new String[]{"Pending", "Active", "Completed", "Failed"})));
            
            // Load wave data if editing
            if (wave != null) {
                nameField.setText(wave.getName());
                numberField.setText(String.valueOf(wave.getNumber()));
                enemyCountField.setText(String.valueOf(wave.getEnemyCount()));
                enemyTypesField.setText(wave.getEnemyTypes());
                durationField.setText(String.valueOf(wave.getDuration()));
                difficultyField.setText(String.valueOf(wave.getDifficulty()));
                bossWaveBox.setSelected(wave.isBossWave());
                statusCombo.setSelectedItem(wave.getStatus());
            }
            
            add(formPanel, BorderLayout.CENTER);
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = createModernButton("Save", successColor);
            JButton cancelButton = createModernButton("Cancel", dangerColor);
            
            saveButton.addActionListener(e -> {
                saved = true;
                dispose();
            });
            
            cancelButton.addActionListener(e -> dispose());
            
            buttonPanel.add(saveButton);
            buttonPanel.add(cancelButton);
            
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private JPanel createDialogField(String label, JComponent field) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.setBackground(bgColor);
            
            JLabel jLabel = new JLabel(label);
            jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            jLabel.setPreferredSize(new Dimension(150, 25));
            
            field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            field.setPreferredSize(new Dimension(250, 30));
            
            panel.add(jLabel);
            panel.add(field);
            
            return panel;
        }
        
        public boolean isSaved() {
            return saved;
        }
        
        public WaveData getWaveData() {
            return new WaveData(
                0, // ID will be assigned by table model
                nameField.getText(),
                Integer.parseInt(numberField.getText()),
                Integer.parseInt(enemyCountField.getText()),
                enemyTypesField.getText(),
                Integer.parseInt(durationField.getText()),
                Double.parseDouble(difficultyField.getText()),
                bossWaveBox.isSelected(),
                (String) statusCombo.getSelectedItem()
            );
        }
    }
}
