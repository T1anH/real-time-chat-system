package client;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class LoginFrame {

	private final String host;
	private final int port;
	private JFrame frame;
	private JTextField usernameField;
	private JPasswordField passwordField;
	private JButton loginButton;
	private JButton registerButton;
	private JLabel statusLabel;
	private ChatConnection conn; 
	private final BlockingQueue<String> inboxQueue = new ArrayBlockingQueue<>(200);
	
	public LoginFrame(String host, int port) {
		this.host = host;
		this.port = port;
		
		buildUI();
		connect();
	}
	
	private void buildUI() {
		frame = new JFrame("Chat Login");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		
		usernameField = new JTextField(18);
		passwordField = new JPasswordField(18);
		loginButton = new JButton("Login");
		registerButton = new JButton("Register");
		statusLabel = new JLabel(" ");
		statusLabel.setForeground(Color.DARK_GRAY);
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Username:"), gbc);
		gbc.gridx = 1;
		panel.add(usernameField, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		panel.add(new JLabel("Password:"), gbc);
		gbc.gridx = 1;
		panel.add(passwordField, gbc);
		
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		buttonsPanel.add(loginButton);
		buttonsPanel.add(registerButton);
		
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 2;
		panel.add(buttonsPanel, gbc);
		gbc.gridy = 3;
		panel.add(statusLabel, gbc);
		
		loginButton.addActionListener(e -> attemptAuthorization("LOGIN"));
		registerButton.addActionListener(e -> attemptAuthorization("REGISTER"));
		frame.setContentPane(panel);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	
	private void attemptAuthorization(String command) {
		String username = usernameField.getText().trim();
		String password = new String(passwordField.getPassword()).trim();
		if(username.isEmpty() || password.isEmpty()) {
			if(username.isEmpty() && password.isEmpty()) {
				statusLabel.setText("Username and password cannot be empty.");
			} else if(username.isEmpty()){
				statusLabel.setText("Username cannot be empty.");
			} else if(password.isEmpty()) {
				statusLabel.setText("Password cannot be empty.");
			}
			return;
		}
		
		loginButton.setEnabled(false);
		registerButton.setEnabled(false);
		statusLabel.setText("Sending " + command + ". . .");
		inboxQueue.clear();
		conn.send(command + " " + username + " " + password);
		
		//Wait for server
		new Thread(() -> {
			try {
				long deadline = System.currentTimeMillis() + 5000;
				
				while(System.currentTimeMillis() < deadline) {
					String line = inboxQueue.poll(5, TimeUnit.SECONDS);
					if(line == null) {
						continue;
					}
					if(line.toUpperCase().contains("LOGIN SUCCESSFUL") || line.toUpperCase().contains("REGISTER SUCCESSFUL")) {
						SwingUtilities.invokeLater(() -> {
							statusLabel.setText("Success!");
							frame.dispose();
							
							final MainFrame[] main = new MainFrame[1];
							conn.setOnMessage(msg -> {
								if(main[0] != null)
									main[0].onServerLine(msg);
								});
							main[0] = new MainFrame(conn, username);
						});
						return;
					}
					
					if(line.toUpperCase().startsWith("ERR ")) {
						final String friendly = mapError(line);
						SwingUtilities.invokeLater(() -> {
							statusLabel.setText(friendly);
							loginButton.setEnabled(true);
							registerButton.setEnabled(true);
						});
						return;
					}
					
					if(line.toUpperCase().contains("LOGIN FAILED") || line.toUpperCase().contains("REGISTER FAILED")) {
						SwingUtilities.invokeLater(() -> {
							statusLabel.setText("Login/Register failed.");
							loginButton.setEnabled(true);
							registerButton.setEnabled(true);
						});
						return;
					}
				}
				
				SwingUtilities.invokeLater(() -> {
					statusLabel.setText("No response from server. Try again later.");
					loginButton.setEnabled(true);
					registerButton.setEnabled(true);
				});
			} catch (Exception e) {
				SwingUtilities.invokeLater(() ->{
					statusLabel.setText("Authorization error: " + e.getMessage());
					loginButton.setEnabled(true);
					registerButton.setEnabled(true);
				});
			}
		}).start();
	}
	
	private String mapError(String line) {
	    String l = line.trim().toUpperCase();
	    if (l.equals("ERR LOGIN_NO_SUCH_USER")) {
	        return "No such user.";
	    } else if (l.equals("ERR LOGIN_WRONG_PASSWORD")) {
	        return "Wrong password.";
	    } else if (l.equals("ERR LOGIN FAILED")) {
	        return "Login failed (server/db error).";
	    } else if (l.equals("ERR REGISTER_USERNAME_TAKEN")) {
	        return "Username already taken.";
	    } else if (l.equals("ERR REGISTER_BAD_PASSWORD")) {
	        return "Password must be at least 6 characters and contain no spaces.";
	    } else if (l.equals("ERR REGISTER_BAD_USERNAME")) {
	        return "Bad username.";
	    } else if (l.equals("ERR REGISTER FAILED")) {
	        return "Register failed (server/db error).";
	    } else {
	        return line;
	    }
	}

	private void connect() {
		try {
			conn = new ChatConnection(host, port);
			conn.connect(line -> {inboxQueue.offer(line);
			});
			statusLabel.setText("Connected. Please login or register.");
		} catch (Exception e) {
			statusLabel.setText("Failed to connect: " + e.getMessage());
			loginButton.setEnabled(false);
			registerButton.setEnabled(false);
		}
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new LoginFrame("localhost", 5000));
	}
	
}
