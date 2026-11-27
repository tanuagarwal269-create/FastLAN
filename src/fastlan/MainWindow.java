package fastlan;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class MainWindow extends JFrame implements TransferListener {

    private final JTextField hostField = new JTextField("127.0.0.1", 14);
    private final JTextField portField = new JTextField("5000", 6);

    private final JTextField saveDirField = new JTextField(System.getProperty("user.home") + File.separator + "Downloads", 24);

    private final JButton browseSaveDirBtn = new JButton("Browse...");
    private final JButton startServerBtn = new JButton("Start Receiver");
    private final JButton stopServerBtn = new JButton("Stop Receiver");
    private final JButton chooseFilesBtn = new JButton("Choose Files");
    private final JButton sendFilesBtn = new JButton("Send Files");

    private final JLabel selectedFilesLabel = new JLabel("No files selected");

    private final JLabel currentFileLabel = new JLabel("Current file: -");

    private final JLabel speedLabel = new JLabel("Speed: 0.00 MB/s");

    private final JLabel statusLabel = new JLabel("Status: Ready");

    private final JProgressBar progressBar = new JProgressBar(0, 100);

    private final JTextArea logArea = new JTextArea(6, 50);

    private FileTransferServer server;

    private List<File> selectedFiles = new ArrayList<>();

    public MainWindow() {

        super("FastLAN");

        // Application Logo
        URL iconURL = getClass().getResource("/fastlan/fastlan-icon.png");

        if (iconURL != null) {
            setIconImage(new ImageIcon(iconURL).getImage());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Connection Settings
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));

        topPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8),"Connection Settings"));

        JPanel networkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        networkRow.add(new JLabel("Receiver IP:"));
        networkRow.add(hostField);
        networkRow.add(new JLabel("Port:"));
        networkRow.add(portField);
        networkRow.add(startServerBtn);
        networkRow.add(stopServerBtn);

        stopServerBtn.setEnabled(false);

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        saveRow.add(new JLabel("Save Folder:"));
        saveRow.add(saveDirField);
        saveRow.add(browseSaveDirBtn);

        topPanel.add(networkRow);
        topPanel.add(saveRow);

        // Main Content
        JPanel centerPanel = new JPanel();

        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // File Selection
        JPanel sendControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        sendControls.add(chooseFilesBtn);
        sendControls.add(sendFilesBtn);
        sendControls.add(selectedFilesLabel);

        // Transfer Progress
        JPanel progressPanel = new JPanel(new BorderLayout(8, 12));

        progressPanel.setBorder(BorderFactory.createTitledBorder("Transfer Progress"));

        JPanel infoLine = new JPanel(new BorderLayout());

        infoLine.add(currentFileLabel, BorderLayout.WEST);
        infoLine.add(speedLabel, BorderLayout.EAST);

        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(600, 28));

        JPanel progressBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        progressBottom.add(statusLabel);

        progressPanel.add(infoLine, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(progressBottom, BorderLayout.SOUTH);

        progressPanel.setPreferredSize(new Dimension(800, 125));
        progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        // Activity Log
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(logArea);

        logScroll.setBorder(BorderFactory.createTitledBorder("Activity Log"));

        logScroll.setPreferredSize(new Dimension(800, 180));
        logScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Arrange Components
        centerPanel.add(sendControls);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(progressPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(logScroll);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Button Actions
        wireActions();

        setSize(900, 550);
        setMinimumSize(new Dimension(780, 480));
        setLocationRelativeTo(null);
    }

    private void wireActions() {

        browseSaveDirBtn.addActionListener(e -> {

            JFileChooser chooser = new JFileChooser(saveDirField.getText());

            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {

                saveDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        startServerBtn.addActionListener(e -> startReceiver());

        stopServerBtn.addActionListener(e -> stopReceiver());

        chooseFilesBtn.addActionListener(e -> {

            JFileChooser chooser = new JFileChooser();

            chooser.setMultiSelectionEnabled(true);

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {

                selectedFiles = new ArrayList<>( Arrays.asList(chooser.getSelectedFiles()));

                selectedFilesLabel.setText(selectedFiles.size() + " file(s) selected");

                appendLog("Selected " + selectedFiles.size() + " file(s).");
            }
        });

        sendFilesBtn.addActionListener(e -> sendSelectedFiles());
    }

    private void startReceiver() {

        int port = parsePort();

        if (port == -1) return;

        File dir = new File(saveDirField.getText().trim());

        server = new FileTransferServer(port, dir, this);

        server.start();

        startServerBtn.setEnabled(false);
        stopServerBtn.setEnabled(true);
    }

    private void stopReceiver() {

        if (server != null) {
            server.stop();
        }

        startServerBtn.setEnabled(true);
        stopServerBtn.setEnabled(false);
    }

    private void sendSelectedFiles() {

        String host = hostField.getText().trim();

        int port = parsePort();

        if (host.isEmpty() || port == -1) return;

        if (selectedFiles.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Please choose at least one file first.",
                    "No Files",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        sendFilesBtn.setEnabled(false);

        progressBar.setValue(0);

        FileTransferClient client =
                new FileTransferClient(host, port, selectedFiles, this);

        new Thread(() -> {

            client.sendFiles();

            SwingUtilities.invokeLater(() ->
                    sendFilesBtn.setEnabled(true));

        }).start();
    }

    private int parsePort() {

        try {

            int p = Integer.parseInt(portField.getText().trim());

            if (p < 1024 || p > 65535) {

                JOptionPane.showMessageDialog(
                        this,
                        "Use a port between 1024 and 65535.",
                        "Invalid Port",
                        JOptionPane.ERROR_MESSAGE);

                return -1;
            }

            return p;

        } catch (NumberFormatException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Port must be a valid number.",
                    "Invalid Port",
                    JOptionPane.ERROR_MESSAGE);

            return -1;
        }
    }

    private void appendLog(String line) {

        logArea.append(line + "\n");

        logArea.setCaretPosition(
                logArea.getDocument().getLength());
    }

    @Override
    public void onStatus(String message) {

        SwingUtilities.invokeLater(() -> {

            statusLabel.setText("Status: " + message);

            appendLog("[STATUS] " + message);
        });
    }

    @Override
    public void onFileStart(
            String fileName, long fileSize, int index, int total) {

        SwingUtilities.invokeLater(() -> {

            currentFileLabel.setText(
                    "Current file (" + index + "/" + total + "): "
                    + fileName);

            progressBar.setValue(0);

            appendLog("[START] " + fileName
                    + " (" + fileSize + " bytes)");
        });
    }

    @Override
    public void onProgress(
            long bytesTransferred, long fileSize, double speedMBps) {

        SwingUtilities.invokeLater(() -> {

            int pct = fileSize > 0
                    ? (int) ((bytesTransferred * 100L) / fileSize)
                    : 0;

            progressBar.setValue(
                    Math.min(100, Math.max(0, pct)));

            speedLabel.setText(
                    String.format("Speed: %.2f MB/s", speedMBps));
        });
    }

    @Override
    public void onFileComplete(String fileName) {

        SwingUtilities.invokeLater(() -> {

            progressBar.setValue(100);

            appendLog("[DONE] " + fileName);
        });
    }

    @Override
    public void onError(String error) {

        SwingUtilities.invokeLater(() -> {

            statusLabel.setText("Status: Error");

            appendLog("[ERROR] " + error);

            JOptionPane.showMessageDialog(
                    this,
                    error,
                    "FastLAN Error",
                    JOptionPane.ERROR_MESSAGE);
        });
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() ->
                new MainWindow().setVisible(true));
    }
}