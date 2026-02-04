package client;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ChatFrame {

	private final ChatConnection conn;
	private final String username;
	private JFrame frame;
	private JTextArea chatArea;
	private JTextField inputField;
	private JButton sendButton;
	
	public ChatFrame(ChatConnection conn, String username) {
		this.conn = conn;
		this.username = username;
		
		buildUI();
		onServerLine("SYS Logged in. Start chatting!");

	}
	
	private void buildUI() {
		frame = new JFrame("Chat - " + username);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		chatArea = new JTextArea();
		chatArea.setEditable(false);
		chatArea.setLineWrap(true);
		chatArea.setWrapStyleWord(true);
		JScrollPane scroller = new JScrollPane(chatArea);
		inputField = new JTextField();
		sendButton = new JButton("Send");
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(inputField, BorderLayout.CENTER);
		bottomPanel.add(sendButton, BorderLayout.EAST);
		
		frame.setLayout(new BorderLayout());
		frame.add(scroller, BorderLayout.CENTER);
		frame.add(bottomPanel, BorderLayout.SOUTH);
		frame.setSize(500, 500);
		frame.setLocationRelativeTo(null);
		
		ActionListener sendAction = e -> sendMessage();
		sendButton.addActionListener(sendAction);
		inputField.addActionListener(sendAction);
		
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				conn.send("/quit");
				conn.close();
			}
		});
		
		frame.setVisible(true);
	}
	
	private void sendMessage() {
		String text = inputField.getText().trim();
		if(text.isEmpty()) return;
		if("/q".equalsIgnoreCase(text)) text = "/quit";
		conn.send(text);
		inputField.setText("");
		if("/quit".equalsIgnoreCase(text)) {
			inputField.setEnabled(false);
			sendButton.setEnabled(false);
		}
	}
	
	public void onServerLine(String line) {
		SwingUtilities.invokeLater(() -> {
			chatArea.append(line + "\n");
			chatArea.setCaretPosition(chatArea.getDocument().getLength());
		});
	}
	
	//private void hookIncomingMessage() {
	//	new Thread(() -> {
	//		try {
	//			
	//		} catch(Exception ignored) {
	//			
	//		}
	//	}).start();
	//
	//	SwingUtilities.invokeLater(() -> {
	//		chatArea.append("Logged in. Start Chatting!" + "\n");
	//		chatArea.setCaretPosition(chatArea.getDocument().getLength());
	//	});
	//}

}
