package bg.sap.server;

import bg.sap.utils.Constants;
import bg.sap.utils.FileChannelWrapper;
import bg.sap.utils.OperationHandler;
import bg.sap.utils.User;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Created by Terrax on 13.3.2015.
 */

public class ChatServer implements Runnable {

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    // Container which maps a user to its chatting channel.
    private Map<User, SocketChannel> connectedUsers;
    private Map<SocketChannel, User> connectedChannels;

    // Container which maps a user to its file channel.
    private Map<SelectionKey, FileChannelWrapper> fileChannels;

    // Container for user accounts.
    private Map<String, String> userAccounts;

    public ChatServer(int port) {
        connectedUsers = new ConcurrentHashMap<User, SocketChannel>();
        connectedChannels = new ConcurrentHashMap<SocketChannel, User>();
        fileChannels = new ConcurrentHashMap<SelectionKey, FileChannelWrapper>();
        userAccounts = new ConcurrentHashMap<String, String>();

        try {
            startServer(port);
            loadUsers();
        }
        catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Opens the channel for the server.
     * @param port the port on which the server should listen for new connections.
     * @throws IOException
     */
    private void startServer(int port) throws IOException {
        System.out.println("Booting the server!");

        // Open the server channel.
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);

        // Register the channel into the selector.
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Get the ready channels.
                int readyChannels = selector.select();
                if (readyChannels == 0) continue;

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    // New connection from a client.
                    if (key.isAcceptable()) {
                        //System.out.println("New user joined the channel.");
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        server.configureBlocking(false);

                        SocketChannel socketChannel = server.accept();
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    }
                    // Received data from a client.
                    else if (key.isReadable()) {
                        handleEvents(key);
                    }
                    keyIterator.remove();
                }
            }
            catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Method which checks for user credentials and makes new user.
     * @param data the data which should be checked if it is user credentials.
     * @return new User object.
     * @throws IOException
     */
    private User checkForUserCredentials(String data) throws IOException {
        if (data.matches(".*:.*")) {
            String[] credentials = data.split(":");

            return new User(credentials[0], credentials[1]);
        }

        return null;
    }

    /**
     * Handle received message on a channel from client.
     * @param key the selection key of the client.
     * @throws IOException
     */
    private void handleEvents(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Download a file.
        if (fileChannels.containsKey(key)) {
            FileChannelWrapper fileChannelWrapper = fileChannels.get(key);

            OperationHandler.getFile(key, fileChannelWrapper);

            if (!fileChannelWrapper.getFileChannel().isOpen()) {
                fileChannels.remove(key);
            }
        }
        // Read data.
        else {
            String data = OperationHandler.readData(socketChannel);

            // Check if the data is user credentials.
            User user = checkForUserCredentials(data);
            if (user != null) {
                // Check if the user has account.
                if (isActiveAccount(user)) {
                    connectedUsers.put(user, socketChannel);
                    connectedChannels.put(socketChannel, user);
                }
                // If not, cancel the connection.
                else {
                    OperationHandler.sendData(socketChannel, Constants.INVALID_USER);
                }
            }
            // Check if the user wants to logout.
            else if (data.contains(Constants.LOGOUT)) {
                OperationHandler.sendData(socketChannel, "Server message: You have been logged out!");

                User logout = connectedChannels.get(socketChannel);

                connectedChannels.remove(socketChannel);
                connectedUsers.remove(logout);
            }
            // Check if a file is being sent to the server.
            else if (data.contains(Constants.FILE_UPLOAD)) {
                String[] details = data.split("-");

                File file = new File(Constants.FILE_DIR + details[1]);
                file.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                FileChannel fileChannel = fileOutputStream.getChannel();

                System.out.println("Receiving file");
                fileChannels.put(key, new FileChannelWrapper(Integer.parseInt(details[2]), fileChannel));
            }
            // Check if a user wants to download a file.
            else if (data.contains(Constants.FILE_DOWNLOAD)) {
                String[] details = data.split("-");

                File file = new File(Constants.FILE_DIR + details[1]);

                if (file.exists()) {
                    System.out.println("Server is sending file.");
                    OperationHandler.sendFile(file, socketChannel);
                }
                else {
                    OperationHandler.sendData(socketChannel, Constants.FILE_NOT_FOUND);
                    socketChannel.close();
                    key.cancel();
                }
            }
            // Check if the user requests the list of files stored on the server.
            else if (data.equals(Constants.GET_FILE_LIST)) {
                File dir = new File(Constants.FILE_DIR);
                File[] fileList = dir.listFiles();

                for (File file : fileList) {
                    OperationHandler.sendData(socketChannel, file.getName());
                }
            }
            // If none from above, broadcast the message.
            else {
                // Get the user who sent the message and exclude it.
                User currentUser = connectedChannels.get(socketChannel);

                broadcastData(data, currentUser);
            }
        }
    }

    /**
     * Method which sends the message to all users except the one which sent the message.
     * @param data the message to be sent.
     * @param currentUser the user which sent the message.
     * @throws IOException
     */
    private void broadcastData(String data, User currentUser) throws IOException {
        Set<User> keySet = connectedUsers.keySet();
        int count = 0;

        for (User temp : keySet) {
            if (temp.equals(currentUser)) {
                continue;
            }

            SocketChannel socketChannel = connectedUsers.get(temp);
            OperationHandler.sendData(socketChannel, currentUser.getUserName() + ": " + data);
            count++;
        }

        // Send feedback to the user.
        SocketChannel socketChannel = connectedUsers.get(currentUser);
        OperationHandler.sendData(socketChannel, String.format(Constants.MESSAGE_SENT, count));
    }

    /**
     * Check if the user is allowed to log in the server
     * @param user the user requesting access.
     * @return true if the user is allowed to log in, false otherwise.
     * @throws IOException
     */
    private boolean isActiveAccount(User user) throws IOException {
        // Register the user.
        if (!userAccounts.containsKey(user.getUserName())) {
            String password = user.getUserPass();
            // Password required in order to create an account.
            if (password == null || password.equals("")) return false;

            userAccounts.put(user.getUserName(), password);
            createAccount(user);

            return true;
        }

        // Check if the credentials are valid.
        String temp = userAccounts.get(user.getUserName());
        System.out.println(temp + " " + user.getUserPass());
        if (!temp.equals(user.getUserPass())) {
            return false;
        }

        // Check if the user is already logged in.
        Set<User> users = connectedUsers.keySet();
        String username = user.getUserName();

        // Deny access if the user is already logged in.
        for (User loggedUser : users) {
            if (loggedUser.getUserName().equals(username)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Store the list of active accounts in the memory.
     * @throws IOException
     */
    private void loadUsers() throws IOException {
        File accounts = new File("accounts.txt");
        if (!accounts.exists()) {
            accounts.createNewFile();
        }

        FileInputStream fileInputStream = new FileInputStream(accounts);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));

        for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
            String[] pair = line.split(":");
            userAccounts.put(pair[0], pair[1]);
        }
    }

    /**
     * Create a new account.
     * @param user the account to be created.
     * @throws IOException
     */
    private void createAccount(User user) throws IOException {
        File accounts = new File("accounts.txt");
        if (!accounts.exists()) {
            accounts.createNewFile();
        }

        FileOutputStream fileOutputStream = new FileOutputStream(accounts, true);
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));

        bufferedWriter.write(user.getUserName() + ":" + user.getUserPass());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }
}