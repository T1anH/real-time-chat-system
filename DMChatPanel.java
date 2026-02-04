package client;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class DMChatPanel extends JPanel{

	private final ChatConnection conn;
	private final String me;
	private final String friend;
	private final JTextArea chatArea = new JTextArea();
	private final JTextField inputField = new JTextField();
	private final JButton sendButton = new JButton("Send");
	
	public DMChatPanel(ChatConnection conn, String me, String friend) {
		this.conn = conn;
		this.me = me;
		this.friend = friend;
		
		setLayout(new BorderLayout());
		chatArea.setEditable(false);
		chatArea.setLineWrap(true);
		chatArea.setWrapStyleWord(true);
		add(new JScrollPane(chatArea), BorderLayout.CENTER);
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(inputField, BorderLayout.CENTER);
		bottomPanel.add(sendButton, BorderLayout.EAST);
		add(bottomPanel, BorderLayout.SOUTH);
		
		sendButton.addActionListener(e -> sendMessage());
		inputField.addActionListener(e -> sendMessage());
		append("[System] Chat with " + friend);
		SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
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
		conn.send("DM " + friend + " " + text);
		append(me + ": " + text);
		inputField.setText("");
	}
	
}
