package bg.sap.client;

import bg.sap.utils.Constants;
import bg.sap.utils.FileChannelWrapper;
import bg.sap.utils.OperationHandler;
import bg.sap.utils.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;

/**
 * @Created by Terrax on 15.3.2015.
 */

public class ChatClient extends JFrame implements Runnable, ActionListener {
    private String host;

    // File sending fields.
    private boolean connectedToServer;
    private boolean downloadRequest;
    private FileChannelWrapper fileChannelWrapper;
    private SelectionKey transferKey;
    private String fileName;

    // NIO fields.
    private SocketChannel socketChannel;
    private Selector selector;

    // GUI fields.
    private JButton sendMsgButton;
    private JButton logoutButton;
    private JButton sendFileButton;
    private JButton getFileButton;
    private JButton getFileListButton;
    private JTextPane textPane;
    private JScrollPane scrollPane;
    private JTextField textField;
    private JPanel mainPanel;
    private JFileChooser fileChooser;

    // File transfer fields.
    private SocketChannel fileSendingChannel;

    public ChatClient(String host) {
        this.host = host;
        connectedToServer = false;
        downloadRequest = false;
        System.out.println("Starting client");

        initGUI();

        try {
            initClient(host, Constants.PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize the client and connect to a server.
     * @param host the host name of the server.
     * @param port the port of the server.
     * @throws IOException
     */
    private void initClient(String host, int port) throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        selector = Selector.open();

        socketChannel.connect(new InetSocketAddress(host, port));
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
    }

    /**
     * Initiates the client interface.
     */
    private void initGUI() {
        // Close on exit.
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setTitle("NIO Chat Client");
        createPanel();
        add(mainPanel);
        pack();
        setMinimumSize(getSize());
        setVisible(true);
    }

    /**
     * Creates the main panel and shows it.
     */
    private void createPanel() {
        // Initialize the text area, scroll pane and input field and add them to the content pane.
        textPane = new JTextPane();
        textPane.setPreferredSize(new Dimension(Constants.FRAME_WIDTH, Constants.FRAME_HEIGHT));
        textPane.setEditable(false);
        textField = new JTextField();
        textField.setPreferredSize(new Dimension(Constants.TEXT_FIELD_WIDTH, Constants.TEXT_FIELD_HEIGHT));
        scrollPane = new JScrollPane(textPane);

        // Create the panel and add the components to it.
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        mainPanel.add(scrollPane, constraints);
        constraints.gridy = 1;
        mainPanel.add(textField, constraints);

        // Initialize the buttons and add them to the main panel.
        sendMsgButton = new JButton("Send Message");
        constraints.gridwidth = 1;
        constraints.weightx = 0.5;
        constraints.gridy = 2;
        constraints.insets = new Insets(1, 1, 1, 1);
        mainPanel.add(sendMsgButton, constraints);

        getFileListButton = new JButton("Get File List");
        constraints.weightx = 0.5;
        constraints.gridx = 1;
        mainPanel.add(getFileListButton, constraints);

        sendFileButton = new JButton("Send File");
        constraints.weightx = 0.5;
        constraints.gridx = 0;
        constraints.gridy = 3;
        mainPanel.add(sendFileButton, constraints);

        getFileButton = new JButton("Get File");
        constraints.weightx = 0.5;
        constraints.gridx = 1;
        constraints.gridy = 3;
        mainPanel.add(getFileButton, constraints);

        logoutButton = new JButton("Logout");
        constraints.weightx = 0.0;
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.gridy = 4;
        mainPanel.add(logoutButton, constraints);

        // Add the button listeners.
        sendMsgButton.addActionListener(this);
        logoutButton.addActionListener(this);
        sendFileButton.addActionListener(this);
        getFileButton.addActionListener(this);
        getFileListButton.addActionListener(this);
    }

    /**
     * Asks the user for credentials and sends them to the server.
     * @throws IOException
     */
    private void sendCredentialsToServer() throws IOException {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel labels = new JPanel(new GridLayout(0, 1, 2, 2));
        labels.add(new JLabel("User", SwingConstants.RIGHT));
        labels.add(new JLabel("Pass", SwingConstants.RIGHT));
        panel.add(labels, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField usernameField = new JTextField();
        controls.add(usernameField);

        JPasswordField passwordField = new JPasswordField();
        controls.add(passwordField);
        panel.add(controls, BorderLayout.CENTER);

        String username = "";
        String password = "";

        while (username == null || username.equals("") ||
                password == null || password.equals("")) {
            JOptionPane.showConfirmDialog(this, panel, "Enter credentials", JOptionPane.OK_CANCEL_OPTION);

            username = usernameField.getText();
            password = new String(passwordField.getPassword());
        }

        User user = new User(username, password);

        OperationHandler.sendData(socketChannel, user.toString());
    }

    /**
     * Logs the user out from the server.
     * @throws IOException
     */
    private void logout() throws IOException {
        String logout = "*Logout";
        OperationHandler.sendData(socketChannel, logout);

        this.dispose();
    }

    /**
     * Sends the chat message to the server.
     * @throws IOException
     */
    private void sendMsg() throws IOException {
        String text = textField.getText();
        textField.setText("");
        text = text.trim();

        OperationHandler.sendData(socketChannel, text);
    }

    /**
     * Open a channel used for transmitting files.
     * @throws IOException
     */
    private void openFileSendingChannel() throws IOException {
        fileSendingChannel = SocketChannel.open();
        fileSendingChannel.configureBlocking(false);
        fileSendingChannel.connect(new InetSocketAddress(host, Constants.PORT));
        fileSendingChannel.register(selector, SelectionKey.OP_CONNECT);
    }

    /**
     * Sends the selected file to the server.
     * @throws IOException
     */
    private void sendFile() throws IOException {
        fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File("."));
        fileChooser.setDialogTitle("Browse the file to send");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(true);

        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            OperationHandler.sendFile(fileChooser.getSelectedFile(), fileSendingChannel);
        }
    }

    /**
     * Send command to the server that a user wants to download a file.
     * @throws IOException
     */
    private void downloadFile() throws IOException {
        OperationHandler.sendData(fileSendingChannel, Constants.FILE_DOWNLOAD + "-" + fileName);
        fileSendingChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Get ready channels.
                int readyChannels = selector.select();
                if (readyChannels == 0) continue;

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                // Handle events.
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    // Finish connection for the ready channels.
                    if (key.isConnectable()) {
                        SocketChannel sockChannel = (SocketChannel) key.channel();
                        sockChannel.finishConnect();

                        // File sending channel.
                        if (connectedToServer == true) {
                            if (downloadRequest) {
                                downloadFile();
                                downloadRequest = false;
                            }
                            else {
                                sendFile();
                            }
                        }
                        // Chat channel.
                        else {
                            sockChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

                            // Login to the server.
                            sendCredentialsToServer();
                            connectedToServer = true;
                        }
                    }
                    // Client has sent data.
                    else if (key.isReadable()) {
                        // Download file.
                        if (key == transferKey) {
                            OperationHandler.getFile(key, fileChannelWrapper);
                        }
                        else {
                            // Read data.
                            String output = OperationHandler.readData((SocketChannel) key.channel());

                            // The server is sending a file.
                            if (output.contains(Constants.FILE_UPLOAD)) {
                                String[] fileData = output.split("-");

                                File file = new File(fileData[1]);

                                file.createNewFile();

                                FileOutputStream fileOutputStream = new FileOutputStream(file);
                                FileChannel fileChannel = fileOutputStream.getChannel();
                                fileChannelWrapper = new FileChannelWrapper(Long.parseLong(fileData[2]), fileChannel);

                                transferKey = key;
                            }
                            // Server did not find requested file.
                            else if (output.contains(Constants.FILE_NOT_FOUND)) {
                                textPane.setText(textPane.getText() + " File not found\n");
                                fileSendingChannel.close();
                                key.cancel();
                                transferKey = null;
                            }
                            else {
                                textPane.setText(textPane.getText() + output + "\n");
                            }
                        }
                    }
                    keyIterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Invoked when an action occurs.
     * @param e the event that happened.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendMsgButton) {
            try {
                sendMsg();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        else if (e.getSource() == logoutButton) {
            try {
                logout();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        else if (e.getSource() == sendFileButton) {
            try {
                openFileSendingChannel();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        else if (e.getSource() == getFileButton) {
            fileName = JOptionPane.showInputDialog(this, "Enter file name");
            try {
                openFileSendingChannel();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            downloadRequest = true;
        }
        else if (e.getSource() == getFileListButton) {
            try {
                OperationHandler.sendData(socketChannel, Constants.GET_FILE_LIST);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}