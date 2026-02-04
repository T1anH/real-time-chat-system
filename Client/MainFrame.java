package client;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Timer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

public class MainFrame {
	private final ChatConnection conn;
	private final String me;
	private static final int AWAY_TIME = 30_000;
	private volatile long lastActivity = System.currentTimeMillis();
	private volatile String myAutoStatus = "online";
	private Timer idleTimer;
	private AWTEventListener activityListener;
	private JFrame frame;
	private DefaultListModel<String> friendsModel;
	private JList<String> friendsList;
	private JLabel statusLabel;
	private JTabbedPane tabs;
	private final Map<String, DMChatPanel> dmPanels = new ConcurrentHashMap<>();
    private final Map<String, RoomChatPanel> roomPanels = new ConcurrentHashMap<>();
	private final Set<String> onlineSet = ConcurrentHashMap.newKeySet();
	private final Map<String, String> statusMap = new ConcurrentHashMap<>();

	public MainFrame(ChatConnection conn, String me) {
		this.conn = conn;
		this.me = me;
		buildUI();
		//conn.setOnMessage(line -> this.onServerLine(line));
		conn.send("FRIENDS");
		conn.send("ONLINE");
		conn.send("STATUSES");
		startAutoAway();
		conn.send("STATUS online");
	}
	
	private void buildUI() {
		frame = new JFrame("Chat - " + me);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	    frame.setLayout(new BorderLayout());
	    frame.setJMenuBar(buildMenuBar());
	    
	    statusLabel = new JLabel(" ");
	    frame.add(statusLabel, BorderLayout.SOUTH);
	    
	    friendsModel = new DefaultListModel<>();
	    friendsList = new JList<>(friendsModel);
	    friendsList.setCellRenderer(new FriendCell(statusMap, onlineSet));
	    friendsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	    //friendsList.addListSelectionListener(e -> {
	    //	if(!e.getValueIsAdjusting()) {
	    //		String selected = friendsList.getSelectedValue();
	    //		if(selected != null) openDM(selected);
	    //	}
	    //});
	    friendsList.addMouseListener(new MouseAdapter() {
	        @Override
	        public void mouseClicked(MouseEvent e) {
	        	if (SwingUtilities.isRightMouseButton(e)) return;
	            if (e.getClickCount() == 2) {
	                int i = friendsList.locationToIndex(e.getPoint());
	                if (i >= 0) {
	                    Rectangle bounds = friendsList.getCellBounds(i, i);
	                    if (bounds != null && bounds.contains(e.getPoint())) {
	                        String selected = friendsModel.get(i);
	                        openDM(selected);
	                    }
	                }
	            }
	        }
	        
	        @Override
	        public void mousePressed(MouseEvent e) {
	        	if(e.isPopupTrigger())
	        		showFriendPopup(e);
	        }
	        
	        @Override
	        public void mouseReleased(MouseEvent e) {
	        	if(e.isPopupTrigger())
	        		showFriendPopup(e);
	        }
	    });



	    JButton addButton = new JButton("Send Friend Request");
	    JButton removeButton = new JButton("Remove Friend");
	    JButton refreshButton = new JButton("Refresh");
	    JButton requestsButton = new JButton("Friend Request");
	    addButton.addActionListener(e -> addFriend());
	    removeButton.addActionListener(e -> removeFriendSelected());
	    refreshButton.addActionListener(e -> {
	    	conn.send("FRIENDS");
	    	conn.send("ONLINE");
	    	conn.send("STATUSES");
	    });
	    requestsButton.addActionListener(e -> conn.send("FRIEND_REQUEST"));

	    JPanel leftButtonsPanel = new JPanel();
	    leftButtonsPanel.add(addButton);
	    leftButtonsPanel.add(removeButton);
	    leftButtonsPanel.add(refreshButton);
	    leftButtonsPanel.add(requestsButton);
	    JPanel leftPanel = new JPanel(new BorderLayout());
	    leftPanel.add(new JScrollPane(friendsList), BorderLayout.CENTER);
	    leftPanel.add(leftButtonsPanel, BorderLayout.SOUTH);
	    leftPanel.setPreferredSize(new Dimension(50, 100));
	    
	    tabs = new JTabbedPane();
	    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, tabs);
	    splitPane.setResizeWeight(0.15);
	    
	    frame.add(splitPane, BorderLayout.CENTER);
	    frame.setSize(900, 600);
	    frame.setLocationRelativeTo(null);
	    frame.addWindowListener(new WindowAdapter() {
	    	@Override 
	    	public void windowClosing(WindowEvent e) {
	            if (idleTimer != null) idleTimer.stop();
	            if (activityListener != null) {
	                Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
	            }
	    		conn.send("/quit");
	    		conn.close();
	    	}
	    });
	    frame.setVisible(true);
	    SwingUtilities.invokeLater(() -> statusLabel.setText("Connected as " + me));
	}
	private JMenuBar buildMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		JMenu file = new JMenu("File");
		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(e -> {
			conn.send("/quit");
			conn.close();
			frame.dispose();
		});
		file.add(exit);
		
		JMenu rooms = new JMenu("Rooms");
		JMenuItem createRoom = new JMenuItem("Create Room");
		createRoom.addActionListener(e -> createRoom());
		JMenuItem joinRoom = new JMenuItem("Join Room");
		joinRoom.addActionListener(e -> joinRoom());
		JMenuItem leaveRoom = new JMenuItem("Leave Current Room");
		leaveRoom.addActionListener(e -> leaveCurrentRoom());
		
		rooms.add(createRoom);
		rooms.add(joinRoom);
		rooms.add(leaveRoom);
		menuBar.add(file);
		menuBar.add(rooms);
		return menuBar;
	}
	
	private void createRoom() {
		String room = JOptionPane.showInputDialog(frame, "Enter a new room name:");
		if(room == null) return;
		room = room.trim();
		if(room.isEmpty()) return;
		conn.send("JOIN " + room);
		//openRoom(room);
	}
	
	private void joinRoom() {
		String room = JOptionPane.showInputDialog(frame, "Enter room name to join:");
		if(room == null) return;
		room = room.trim();
		if(room.isEmpty()) return;
		conn.send("JOIN " + room);
		//openRoom(room);
	}
	
	private void openRoom(String room) {
		roomPanels.computeIfAbsent(room, r -> {
			RoomChatPanel panel = new RoomChatPanel(conn, me, r, () -> {
				int index = tabs.indexOfComponent(roomPanels.get(r));
				if(index >= 0) tabs.removeTabAt(index);
				roomPanels.remove(r);
			});
			tabs.addTab("Room: " + r, panel);
			conn.send("ROOM_HISTORY " + r);
			return panel;
		});
		RoomChatPanel roomPanel = roomPanels.get(room);
		tabs.setSelectedComponent(roomPanel);
	}
	
	private void showFriendPopup(MouseEvent e) {
		int index = friendsList.locationToIndex(e.getPoint());
		if(index < 0) return;
		Rectangle bounds = friendsList.getCellBounds(index, index);
		if(bounds == null || !bounds.contains(e.getPoint())) return;
		
		friendsList.setSelectedIndex(index);
		String friend = friendsModel.get(index);
		JPopupMenu popupMenu = new JPopupMenu();
		JMenuItem dM = new JMenuItem("DM");
		dM.addActionListener(f -> openDM(friend));
		JMenuItem inviteToRoom = new JMenuItem("Invite to Room");
		inviteToRoom.addActionListener(f -> inviteFriendToRoom(friend));
		JMenuItem remove = new JMenuItem("Remove Friend");
		remove.addActionListener(f -> removeFriendByName(friend));
		
		popupMenu.add(dM);
		popupMenu.add(inviteToRoom);
		popupMenu.addSeparator();
		popupMenu.add(remove);
		popupMenu.show(friendsList, e.getX(), e.getY());
	}
	
	private void leaveCurrentRoom() {
		Component comp = tabs.getSelectedComponent();
		if(!(comp instanceof RoomChatPanel)) {
			JOptionPane.showMessageDialog(frame, "Select a room tab first.");
			return;
		}
		RoomChatPanel roomPanel = (RoomChatPanel) comp;
		conn.send("LEAVE " + roomPanel.getRoom());
		
		 if (tabs.indexOfComponent(roomPanel) >= 0) 
			 tabs.removeTabAt(tabs.indexOfComponent(roomPanel));
	     roomPanels.remove(roomPanel.getRoom());
	}
	
	private void addFriend() {
		String friend = JOptionPane.showInputDialog(frame, "Enter username to send a friend request to:");
		if(friend == null) return;
		if(friend.trim().isEmpty()) return;
		String f = friend.trim();
		if(f.equals(me)) {
			 JOptionPane.showMessageDialog(frame, "You can’t add yourself.");
			 return;
		}
		conn.send("FRIEND_REQ " + friend.trim());
		conn.send("FRIENDS");
		conn.send("ONLINE");
		conn.send("STATUSES");
	}
	
	private void removeFriendSelected() {
        String selected = friendsList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(frame, "Select a friend you want to remove.");
            return;
        }
        removeFriendByName(selected);
    }

    private void removeFriendByName(String friend) {
        int removeConfirmation = JOptionPane.showConfirmDialog(frame, "Remove " + friend + "?", "Remove Confirmation", JOptionPane.YES_NO_OPTION);
        if (removeConfirmation != JOptionPane.YES_OPTION) return;

        conn.send("FRIEND_REMOVE " + friend);
        conn.send("FRIENDS");
        conn.send("ONLINE");
        conn.send("STATUSES");
    }
    
	/*private void removeFriend() {
		String selected = friendsList.getSelectedValue();
		if(selected == null) {
			JOptionPane.showMessageDialog(frame, "Select a friend you want to remove.");
			return;
		}
		int removeConfirmation = JOptionPane.showConfirmDialog(frame, "Remove " + selected + "?", " Remove Confirmation", JOptionPane.YES_NO_OPTION);
		if(removeConfirmation != JOptionPane.YES_OPTION) return;
		
		conn.send("FRIEND_REMOVE " + selected);
		conn.send("FRIENDS");
		conn.send("ONLINE");
		conn.send("STATUSES");
	}*/

	private void inviteFriendToRoom(String friend) {
		String room = JOptionPane.showInputDialog(frame, "Room name for group chat: ");
		if(room == null) return;
		room = room.trim();
		if(room.isEmpty()) return;
		conn.send("JOIN " + room);
		//openRoom(room);
		conn.send("ROOM_INVITE " + room + " " + friend);
		statusLabel.setText("Invited " + friend + " to room " + room);
	}
	
	private void openDM(String friend) {
		dmPanels.computeIfAbsent(friend, e -> {
			DMChatPanel panel = new DMChatPanel(conn, me, e);
			tabs.addTab(e, panel);
			tabs.setIconAt(tabs.indexOfComponent(panel), statusIcon(e));
			conn.send("DM_HISTORY " + e);
			return panel;
		});
		DMChatPanel dmChatPanel = dmPanels.get(friend);
		if(tabs.indexOfComponent(dmChatPanel) >= 0) {
			tabs.setTitleAt(tabs.indexOfComponent(dmChatPanel), friend);
			tabs.setIconAt(tabs.indexOfComponent(dmChatPanel), statusIcon(friend));
		}
		tabs.setSelectedComponent(dmChatPanel);
	}
	
	private void updateOnlineSet(Set<String> onlineUsers) {
		onlineSet.clear();
		onlineSet.addAll(onlineUsers);
		
		SwingUtilities.invokeLater(() -> {
			for(Map.Entry<String, DMChatPanel> entry : dmPanels.entrySet()) {
				String friend = entry.getKey();
				DMChatPanel dmCPanel = entry.getValue();
				if(tabs.indexOfComponent(dmCPanel) >= 0)
					tabs.setTitleAt(tabs.indexOfComponent(dmCPanel), friend);
			}
			refreshTabIcons();
			friendsList.repaint();
		});
	}
	
	private String parseInviteRoom(String message) {
		String s = "Join my group chat: type JOIN ";
	    if (!message.startsWith(s)) return null;
	    String rest = message.substring(s.length()).trim();
	    if (rest.isEmpty()) return null;
	    return rest.split("\\s+")[0].trim();
	}
	
	public void onServerLine(String line) {
		if(line == null) return;
		if(line.startsWith("JOINED ")) {
			String room = line.substring("JOINED ".length()).trim();
			 SwingUtilities.invokeLater(() -> openRoom(room));
			 return;
		}
		
		if(line.startsWith("ONLINE ")) {
			String onlineNames = line.substring("ONLINE ".length()).trim();
			Set<String> onlineUsers = new HashSet<>();
			if(!onlineNames.isEmpty()) {
				for(String user : onlineNames.split(","))
					onlineUsers.add(user.trim());
			}
			updateOnlineSet(onlineUsers);
			SwingUtilities.invokeLater(() -> statusLabel.setText("Online users updated"));
			return;
		}
		
		if(line.startsWith("FRIENDS ")) {
			String friendNames = line.substring("FRIENDS ".length()).trim();
			List<String> friends = new ArrayList<>();
			if(!friendNames.isEmpty()) {
				for(String friend : friendNames.split(",")) 
					friends.add(friend.trim());
			}
			SwingUtilities.invokeLater(() -> {
	            friendsModel.clear();
	            for (String f : friends) 
	            	friendsModel.addElement(f);
	        });
			SwingUtilities.invokeLater(() -> statusLabel.setText("Friend list updated"));
			return;
		}
		
		if(line.startsWith("DMFROM ")) {
			String dm = line.substring("DMFROM ".length());
			String[] splittedParts = dm.split("\\s+", 2);
			if(splittedParts.length >= 2) {
				String from = splittedParts[0];
				String message = splittedParts[1];
				
				/*String room = parseInviteRoom(message);
				if(room != null) {
					SwingUtilities.invokeLater(() -> {
						int choice = JOptionPane.showConfirmDialog(frame,  
								from + " invited you to join room [" + room + "] Accept?", 
								"Room Invitation", 
								JOptionPane.YES_NO_OPTION);
						if(choice == JOptionPane.YES_OPTION) {
							conn.send("JOIN " + room);
							openRoom(room);
						} else {
							return;
						}
					});
				}*/
				SwingUtilities.invokeLater(() ->{
					openDM(from);
					DMChatPanel panel = dmPanels.get(from);
					if(panel != null) {
						panel.append(from + ": " + message);
					} else {
				        statusLabel.setText("DM received from " + from + " but panel was not ready.");
					}
				});
			}
			return;
		}
		
		if(line.startsWith("ROOMFROM ")) {
			String rest = line.substring("ROOMFROM ".length());
            String[] parts = rest.split("\\s+", 3);
            if (parts.length == 3) {
                String room = parts[0].trim();
                String from = parts[1].trim();
                String msg = parts[2];
                SwingUtilities.invokeLater(() -> appendRoomMessage(room, from, msg));
            }
            return;
		}
		
		if(line.startsWith("FRIENDREQFROM ")) {
			String from = line.substring("FRIENDREQFROM ".length()).trim();
			SwingUtilities.invokeLater(() -> {
				int choice = JOptionPane.showConfirmDialog(frame, from + " sent you a friend request. Accept?", "Friend Request", JOptionPane.YES_NO_OPTION);
				if(choice == JOptionPane.YES_OPTION) {
					conn.send("FRIEND_ACCEPT " + from);
					conn.send("FRIENDS");
					conn.send("ONLINE");
				} else if(choice == JOptionPane.NO_OPTION) {
					conn.send("FRIEND_DECLINE " + from);
					conn.send("FRIENDS");
					conn.send("ONLINE");
				}
			});
			return;
		}
		
		if(line.startsWith("DMHISTORYLINE ")) {
			String rest = line.substring("DMHISTORYLINE ".length());
		    String[] parts = rest.split("\\s+", 3);
		    if (parts.length == 3) {
		        String friend = parts[0].trim();
		        String from = parts[1].trim();
		        String message = parts[2];
		        SwingUtilities.invokeLater(() -> {
		            openDM(friend);
		            DMChatPanel panel = dmPanels.get(friend);
		            if (panel != null) 
		            	panel.append("[HIST] " + from + ": " + message);
		        });
		    }
		    return;
		}
		
		if(line.startsWith("ROOMHISTORYLINE ")) {
		    String rest = line.substring("ROOMHISTORYLINE ".length());
		    String[] parts = rest.split("\\s+", 3);
		    if (parts.length == 3) {
		        String room = parts[0].trim();
		        String from = parts[1].trim();
		        String msg = parts[2];
		        SwingUtilities.invokeLater(() -> {
		            openRoom(room);
		            RoomChatPanel roomPanel = roomPanels.get(room);
		            if (roomPanel != null) 
		            	roomPanel.append("[HIST] " + from + ": " + msg);
		        });
		    }
		    return;
		}
		
		if(line.startsWith("FRIENDREQS ")) {
		    String name = line.substring("FRIENDREQS ".length()).trim();
		    List<String> requests = new ArrayList<>();
		    if(!name.isEmpty()) {
		    	for(String n : name.split(","))
		    		requests.add(n.trim());
		    }
		    SwingUtilities.invokeLater(() -> {
		    	if(requests.isEmpty()) {
		    		JOptionPane.showMessageDialog(frame, "No pending requests.");
		    		return;
		    	}
		    	String selectFriend = (String) JOptionPane.showInputDialog(frame, 
		    			"Pending requests (select one):", 
		    			"Friend Requests", 
		    			JOptionPane.PLAIN_MESSAGE, null, 
		    			requests.toArray(new String[0]), 
		    			requests.get(0));
		    	if(selectFriend == null) return;
		    	int choice = JOptionPane.showConfirmDialog(frame,
		    			"Accept " + selectFriend + "?", 
		    			"Friend Request",
		    			JOptionPane.YES_NO_OPTION);
		    	if(choice == JOptionPane.YES_OPTION) {
		    		conn.send("FRIEND_ACCEPT " + selectFriend);
		    		conn.send("FRIENDS");
		    	    conn.send("ONLINE");
		    	} else if( choice == JOptionPane.NO_OPTION) {
		    		conn.send("FRIEND_DECLINE " + selectFriend);
		    		conn.send("FRIENDS");
		    	    conn.send("ONLINE");
		    	}
		    });
		    return;
		}
		
		if(line.startsWith("STATUSES ")) {
			String data = line.substring("STATUSES ".length()).trim();
			statusMap.clear();
			if(!data.isEmpty()) {
				for(String parts : data.split(",")) {
					String[] keyValue = parts.split(":", 2);
					if(keyValue.length == 2)
						statusMap.put(keyValue[0].trim(), keyValue[1].trim());
				}
			}
			SwingUtilities.invokeLater(() -> {
				for(Map.Entry<String, DMChatPanel> entry : dmPanels.entrySet()) {
					String friend = entry.getKey();
					DMChatPanel panel = entry.getValue();
					if(tabs.indexOfComponent(panel) >= 0)
						tabs.setTitleAt(tabs.indexOfComponent(panel), friend);
				}
				refreshTabIcons();
				friendsList.repaint();
				statusLabel.setText("Statuses updated");
			});
			return;
		}
		
		if (line.startsWith("STATUS ")) {
		    String rest = line.substring("STATUS ".length()).trim();
		    String[] parts = rest.split("\\s+", 2);
		    if (parts.length == 2) {
		        statusMap.put(parts[0].trim(), parts[1].trim());
		        SwingUtilities.invokeLater(() -> {
		            for (Map.Entry<String, DMChatPanel> entry : dmPanels.entrySet()) {
		                String friend = entry.getKey();
		                DMChatPanel panel = entry.getValue();
		                if (tabs.indexOfComponent(panel) >= 0) 
		                	tabs.setTitleAt(tabs.indexOfComponent(panel), friend);
		            }
		            refreshTabIcons();
		            friendsList.repaint();
		        });
		    }
		    return;
		}
		
		if(line.startsWith("ROOMINVITE ")) {
			String rest = line.substring("ROOMINVITE ".length()).trim();
			String[] parts = rest.split("\\s+", 2);
			if(parts.length == 2) {
				String room = parts[0].trim();
				String from = parts[1].trim();
				SwingUtilities.invokeLater(() -> {
					int choice = JOptionPane.showConfirmDialog(frame, 
							from + " invited you to join room [" + room + "]. Accept?",
							"Room Invitation",
							JOptionPane.YES_NO_OPTION);
					if(choice == JOptionPane.YES_OPTION) {
						conn.send("ROOM_INVITE_ACCEPT " + room);
						openRoom(room);
					} else {
						conn.send("ROOM_INVITE_DECLINE " + room);
					}
				});
			}
			return;
		}
		
		if(line.startsWith("SYS ")) {
			SwingUtilities.invokeLater(() -> statusLabel.setText(line.substring(4)));
			return;
		}
		
		if(line.startsWith("ERR ") || line.startsWith("ERROR ")) {
			SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(frame, line, "Server", JOptionPane.WARNING_MESSAGE)
			);
			return;
		}
		
		SwingUtilities.invokeLater(() -> statusLabel.setText(line));
	}
	
	private void appendRoomMessage(String room, String from, String message) {
		openRoom(room);
		RoomChatPanel roomPanel = roomPanels.get(room);
		if(roomPanel != null)
			roomPanel.append(from + ": " + message);
	}
	
	private void startAutoAway() {
	    activityListener = event -> {
	    	if (frame == null || !frame.isActive()) return;
	        lastActivity = System.currentTimeMillis();
	        if (!"online".equals(myAutoStatus)) {
	            myAutoStatus = "online";
	            conn.send("STATUS online");
	        }
	    };
	    long mask =AWTEvent.KEY_EVENT_MASK
	    		 | AWTEvent.MOUSE_EVENT_MASK
	    		 | AWTEvent.MOUSE_MOTION_EVENT_MASK
	    		 | AWTEvent.MOUSE_WHEEL_EVENT_MASK;
	    Toolkit.getDefaultToolkit().addAWTEventListener(activityListener, mask);
	    
	    idleTimer = new Timer(1000, e -> {
	        long idle = System.currentTimeMillis() - lastActivity;
	        if (idle >= AWAY_TIME && !"away".equals(myAutoStatus)) {
	            myAutoStatus = "away";
	            conn.send("STATUS away");
	        }
	    });
	    idleTimer.start();
	}
	
	//creating circle for tab status
	class CircleForTab implements Icon{
		private final Color color;
		private final int size = 10;
		
		CircleForTab(Color color){
			this.color = color;
		}
		@Override 
		public int getIconWidth() { return size; }
		@Override 
		public int getIconHeight() { return size; }
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2D = (Graphics2D) g.create();
			g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2D.setColor(color);
		    g2D.fillOval(x + 1, y + 1, size - 2, size - 2);
		    g2D.setColor(Color.DARK_GRAY);
		    g2D.drawOval(x + 1, y + 1, size - 2, size - 2);
		    g2D.dispose();
		}
	}
	
	private Icon statusIcon(String friend) {
		String sta = statusMap.get(friend);
		boolean isOnline = onlineSet.contains(friend);
		if(!isOnline) return new CircleForTab(Color.DARK_GRAY);
		if ("away".equals(sta)) return new CircleForTab(Color.ORANGE);
	    if ("busy".equals(sta)) return new CircleForTab(Color.RED);
	    return new CircleForTab(Color.GREEN);
	}
	
	private void refreshTabIcons() {
		for (Map.Entry<String, DMChatPanel> entry : dmPanels.entrySet()) {
	        String friend = entry.getKey();
	        DMChatPanel panel = entry.getValue();
	        if (tabs.indexOfComponent(panel) >= 0) {
	            tabs.setTitleAt(tabs.indexOfComponent(panel), friend);           
	            tabs.setIconAt(tabs.indexOfComponent(panel), statusIcon(friend));
	        }
	    }
	}
	
}
