package bg.sap.utils;

import bg.sap.client.ChatClient;
import bg.sap.server.ChatServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Chat extends JFrame implements ActionListener {

    private JButton startServerButton;
    private JButton startClientButton;

    public Chat() {
        // Close on exit.
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set the layout.
        this.setLayout(new FlowLayout());

        // Initialize the buttons and add them to the content pane.
        this.startServerButton = new JButton("Start Server");
        this.startClientButton = new JButton("Start Client");

        this.add(startServerButton);
        this.add(startClientButton);

        // Add the action listeners.
        startServerButton.addActionListener(this);
        startClientButton.addActionListener(this);

        // Display the frame.
        this.setTitle("Simple Chat With NIO");
        this.pack();
        this.setVisible(true);
    }

    public static void main(String[] args) {

        Chat start = new Chat();
    }

    /**
     * Invoked when an action occurs.
     * @param e the event that happened.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == startServerButton) {
            new Thread(new ChatServer(Constants.PORT)).start();
        }
        else if (e.getSource() == startClientButton) {
            new Thread(new ChatClient("localhost")).start();
        }
    }
}
