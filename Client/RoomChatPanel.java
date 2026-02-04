package client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class RoomChatPanel extends JPanel {
	private final ChatConnection conn;
    private final String me;
    private final String room;
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton leaveButton = new JButton("Leave Room");
    private final Runnable leave;
    
    public RoomChatPanel(ChatConnection conn, String me, String room, Runnable leave) {
    	this.conn = conn;
    	this.me = me;
    	this.room = room;
    	this.leave = leave;
    	setLayout(new BorderLayout());
    	chatArea.setEditable(false);
    	chatArea.setLineWrap(true);
    	chatArea.setWrapStyleWord(true);
    	add(new JScrollPane(chatArea), BorderLayout.CENTER);
    	
    	
    	JPanel bottomPanel = new JPanel(new BorderLayout());
    	bottomPanel.add(inputField, BorderLayout.CENTER);
    	bottomPanel.add(sendButton, BorderLayout.EAST);
    	JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    	topPanel.add(leaveButton);
    	add(topPanel, BorderLayout.NORTH);
    	add(bottomPanel, BorderLayout.SOUTH);
    	sendButton.addActionListener(e -> sendMessage());
    	inputField.addActionListener(e -> sendMessage());
    	leaveButton.addActionListener(e -> {
    		conn.send("LEAVE " + room);
    		if (leave != null) leave.run();
    	});
    	append("[SYSTEM] Joined room: " + room);
    	SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }
    
    public String getRoom() {
    	return room;
    }
    
    public void append(String line) {
    	SwingUtilities.invokeLater(() -> {
    		chatArea.append(line + "\n");
    		chatArea.setCaretPosition(chatArea.getDocument().getLength());
    	});
    }
    
    private void sendMessage() {
    	String text = inputField.getText().trim();
    	if(text.isEmpty()) return;
    	conn.send("ROOMMSG " + room + " " + text);
    	append(me + ": " + text);
    	inputField.setText("");
    }
    
}
