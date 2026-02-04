package server;


import database.RoomDAO;
import database.MessageDAO;
import database.ActivityLogDAO;
import database.FriendDAO;
import database.UserDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
	
	private final Socket socket;
	private final ChatServer server;
	private PrintWriter out;
	private BufferedReader in;
	private String username; 
	private final Set<String> joinedRooms = ConcurrentHashMap.newKeySet();

	
	public ClientHandler(Socket socket, ChatServer server) {
		this.socket = socket;
		this.server = server;
	}
	
	public String getUsername() {
		return username == null ? "anonymous" : username;
	}
	
	@Override
	public void run() {
		try {
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			if(!handleAuthentication()) {
				return;
			}
			
			joinRoomFlow("lobby");
			server.broadcastOnlineList();
			server.setStatus(username, "online");

			String line;
			while((line = in.readLine()) != null) {
				if(line.equalsIgnoreCase("/quit")) {
					break;
				}
				handleCommand(line);
			}
			
		} catch (IOException e) {
			System.err.println("Connection error with " + getUsername() + ": " + e.getMessage());
		} finally{
			for(String jR : new HashSet<>(joinedRooms))
				server.leaveRoom(jR, this);
			
			if (username != null) {
			    ActivityLogDAO.log(username, "LOGOUT", "Client disconnected or quit");
			}
			if(username != null) server.unregisterOnline(username);
			joinedRooms.clear();
			close();
		}
	}
	
	private boolean joinRoomFlow(String room) {
		if(username == null) return false;
		room = room.trim();
		if(room.isEmpty()) return false;
		Integer myId = UserDAO.getUserId(username);
		if(myId == null) return false;
		Integer roomId = RoomDAO.getRoomId(room);
		boolean isLobby = room.equalsIgnoreCase("lobby");

		if(roomId == null) {
			roomId = RoomDAO.ensureRoomExist(room, username);
			RoomDAO.addMember(roomId, myId);
		} else {
			if(!RoomDAO.isMember(roomId, myId)) {
				if(isLobby) {
					RoomDAO.addMember(roomId, myId);
				} else if (RoomDAO.inviteExists(roomId, myId)) {
					RoomDAO.deleteInvite(roomId, myId);
					RoomDAO.addMember(roomId, myId);
				} else {
					sendMessage("ERR Not a member or not invited to a room " + room);
					return false;
				}
			}
		}
		server.joinRoomMemory(room, this);
		joinedRooms.add(room);
		sendMessage("JOINED " + room);
		sendMessage("SYS Joined room " + room);
		server.roomBroadcast(room,  "SYS " + username + " joined " + room, this);
		ActivityLogDAO.log(username,  "JOIN_ROOM", "room=" + room);
		return true;
	}
	
	private void leaveRoomFlow(String room) {
		if(username == null) return;
		room = room.trim();
		if(room.isEmpty()) return;
		Integer myId = UserDAO.getUserId(username);
		Integer roomId = RoomDAO.getRoomId(room);
		if(roomId != null && myId != null) {
			RoomDAO.removeMember(roomId, myId);
		}
		server.leaveRoomMemory(room, this);
		joinedRooms.remove(room);
		sendMessage("SYS Left room " + room);
		server.roomBroadcast(room, "SYS " + username + " left " + room, this);
		ActivityLogDAO.log(username, "LEAVE_ROOM", "room=" + room);
	}
	
	public void sendMessage(String message) {
		if(out != null) {
			out.println(message);
		}
	}
	
	private void close() {
		try {
			if(in != null) in.close();
		} catch (IOException ignored) {
			
		}
		
		if(out != null) out.close();
		
		try {
			if(socket != null && !socket.isClosed()) socket.close();
		} catch (IOException ignored) {
			
		}
	}
	
	private boolean handleAuthentication() throws IOException{
		while(true) {
			out.println("SYS Login/Register");
            out.println("SYS REGISTER <username> <password>");
            out.println("SYS LOGIN <username> <password>");
			
			String line = in.readLine();
			if(line == null) {
				return false;
			}
			
			String[] splittedParts = line.trim().split("\\s+");
			if(splittedParts.length != 3) {
				out.println("ERR Invalid Format. Click REGISTER or LOGIN after filling out username and password.");
				continue;
			}
			
			String command = splittedParts[0].toUpperCase();
			String userName = splittedParts[1];
			String password = splittedParts[2];
			
			if(command.equals("REGISTER")) {
				UserDAO.RegisterResult result = UserDAO.registerUser(userName, password);
				if(result == UserDAO.RegisterResult.OK) {
					out.println("REGISTER SUCCESSFUL");
					if(!server.registerOnline(userName, this)) {
						out.println("ERR Already logged in elsewhere.");
						return false;
					} 
					this.username = userName;
					out.println("SYS Welcome " + userName);
					ActivityLogDAO.log(userName, "REGISTER", "Account created");
					ActivityLogDAO.log(userName, "LOGIN", "Registered and logged in");
					return true;
				} else if(result == UserDAO.RegisterResult.USERNAME_TAKEN) {
					out.println("ERR REGISTER_USERNAME_TAKEN");
				} else if(result == UserDAO.RegisterResult.INVALID_PASSWORD) {
					out.println("ERR REGISTER_BAD_PASSWORD");
				} else if(result == UserDAO.RegisterResult.INVALID_USERNAME) {
					out.println("ERR REGISTER_BAD_USERNAME");
				} else {
					out.println("ERR REGISTER FAILED");
				}
			} else if(command.equals("LOGIN")) {
				UserDAO.LoginResult loginResult = UserDAO.validateLogin(userName, password);
				if(loginResult == UserDAO.LoginResult.OK) {
					out.println("LOGIN SUCCESSFUL");
					if(!server.registerOnline(userName, this)) {
						out.println("ERR Already logged in elsewhere.");
                        return false;
					}
					this.username = userName;
					out.println("SYS Welcome back " + userName);
					ActivityLogDAO.log(userName, "LOGIN", "Login successful");
					return true;
				} else if(loginResult == UserDAO.LoginResult.NO_SUCH_USER) {
					out.println("ERR LOGIN_NO_SUCH_USER");
				} else if(loginResult == UserDAO.LoginResult.WRONG_PASSWORD) {
					out.println("ERR LOGIN_WRONG_PASSWORD");
				} else {
					out.println("ERR LOGIN FAILED");
				}
			} else {
				out.println("ERR UNKNOWN COMMAND");
			}
		}
	}
	
	private void handleCommand(String raw) {
		raw = raw.trim();
		if(raw.isEmpty()) return;
		String[] splittedParts = raw.split("\\s+", 3);
		if(splittedParts.length == 0) return;
		
		String command = splittedParts[0].toUpperCase();
		
		if(command.equals("JOIN")) {
			if(splittedParts.length < 2) {
				sendMessage("ERR Joining room");
				return;
			}
			joinRoomFlow(splittedParts[1]);
			return;
		} else if(command.equals("LEAVE")) {
			if(splittedParts.length < 2) {
				sendMessage("ERR Leaving room");
				return;
			}
			leaveRoomFlow(splittedParts[1]);
			return;
		} else if(command.equals("ROOMMSG")) {
			if(splittedParts.length < 3) {
				sendMessage("ERR Sending room msg");
				return;
			}
			String room = splittedParts[1];
		    String msg = splittedParts[2];
			Integer myId = UserDAO.getUserId(username);
		    Integer roomId = RoomDAO.getRoomId(room);
		    if (roomId == null) { 
		    	sendMessage("ERR Room does not exist: " + room); 
		    	return; 
		    }
		    if (!RoomDAO.isMember(roomId, myId)) { 
		    	sendMessage("ERR Not a member of room: " + room); 
		    	return; 
		    }
		    MessageDAO.saveRoom(username,  room, msg);
		    server.roomBroadcast(room, "ROOMFROM " + room + " " + username + " " + msg, this);
			return;
		} else if(command.equals("DM")) {
			if(splittedParts.length < 3) {
				sendMessage("ERR DMing");
				return;
			}
			String to = splittedParts[1];
		    String msg = splittedParts[2];
		    MessageDAO.saveDM(username, to, msg);
		    server.sendDM(to, msg, this);
		    ActivityLogDAO.log(username, "DM_SENT", "to=" + to);
		    return;
		} else if(command.equals("FRIENDS")) {
			sendMessage("FRIENDS " + String.join(",", FriendDAO.getFriends(username)));
		    return;
		} else if(command.equals("FRIEND_ADD")) {
			if(splittedParts.length < 2) {
				sendMessage("ERR ADDING FRIEND");
				return;
			}
			boolean passed = FriendDAO.addFriend(username, splittedParts[1]);
			sendMessage(passed ? "SYS Friend added: " + splittedParts[1] : "ERR Couldn't find friend.");
			return;
		} else if(command.equals("FRIEND_REMOVE")) {
			if(splittedParts.length < 2) {
				sendMessage("ERR removing user");
				return;
			}
			boolean passed = FriendDAO.removeFriend(username, splittedParts[1]);
			sendMessage(passed ? "SYS Friend removed: " + splittedParts[1] : "ERR Couldn't remove friend");
			return;
		} else if(command.equals("ONLINE")) {
			server.broadcastOnlineList();
			return;
		} else if(command.equals("FRIEND_REQ")) {
			if(splittedParts.length < 2) {
				sendMessage("ERR friend request");
				return;
			}
			String target = splittedParts[1];
			boolean okCondition = FriendDAO.sendRequest(username, target);
			if(!okCondition) {
				sendMessage("ERR Couldn't send friend request");
				return;
			}
			sendMessage("SYS Friend request sent to " + target);
			ClientHandler otherUser = server.getOnlineUser(target);
			if(otherUser != null)
				otherUser.sendMessage("FRIENDREQFROM " + username);
			ActivityLogDAO.log(username, "FRIEND_REQUEST_SENT", "to=" + target);
			return;
		} else if(command.equals("FRIEND_REQUEST")) {
			sendMessage("FRIENDREQS " + String.join(",", FriendDAO.getIncomingRequests(username)));
			return;
		} else if(command.equals("FRIEND_ACCEPT")) {
			if(splittedParts.length < 2) {
				sendMessage("ERR accept request");
				return;
			}
			String fromUser = splittedParts[1];
			boolean okCondition = FriendDAO.acceptRequest(username, fromUser);
			if(!okCondition) {
				sendMessage("ERR Couldn't accept request.");
				return;
			}
			sendMessage("SYS Friend request accepted: " + fromUser);
			ClientHandler otherUser = server.getOnlineUser(fromUser);
			if(otherUser != null)
				otherUser.sendMessage("SYS " + username + " accepted your friend request!");
			sendMessage("FRIENDS " + String.join(",", FriendDAO.getFriends(username)));
			if(otherUser != null)
				otherUser.sendMessage("FRIENDS " + String.join(",", FriendDAO.getFriends(fromUser)));
			ActivityLogDAO.log(username, "FRIEND_REQUEST_ACCEPTED", "from=" + fromUser);
			return;
		} else if(command.equals("FRIEND_DECLINE")){
			if (splittedParts.length < 2) { 
				sendMessage("ERR decline request"); 
				return; 
				}
		    String fromUser = splittedParts[1];
		    boolean okCondition = FriendDAO.declineRequest(username, fromUser);
		    if(!okCondition) {
		    	sendMessage("ERR Couldn't decline request.");
		    	return;
		    }
		    sendMessage("SYS Friend request declined: " + fromUser);
		    ClientHandler otherUser = server.getOnlineUser(fromUser);
		    if(otherUser != null)
		    	otherUser.sendMessage("SYS " + username + " rejected your friend request.");
		    ActivityLogDAO.log(username, "FRIEND_REQUEST_DECLINED", "from=" + fromUser);
		    return;
		} else if(command.equals("STATUS")) {
			if (splittedParts.length < 2) {
		        sendMessage("ERR Usage: STATUS <online|away|busy>");
		        return;
		    }
		    String newStatus = splittedParts[1];
		    String normalized = "online";
		    if (newStatus != null) {
		        normalized = newStatus.trim().toLowerCase();
		    }
		    if (!normalized.equals("online") && !normalized.equals("away") && !normalized.equals("busy")) {
		        normalized = "online";
		    }
		    server.setStatus(username, normalized);
		    ActivityLogDAO.log(username, "STATUS", "Changed status to " + normalized);
		    sendMessage("SYS Status set to " + normalized);
		    return;
		} else if(command.equals("STATUSES")) {
			server.statusesSnapshot();
			return;
		} else if(command.equals("ROOM_INVITE")) {
			if(splittedParts.length < 3) {
				sendMessage("ERR ROOM_INVITE"); 
				return; 
			}
			String room = splittedParts[1];
		    String targetUser = splittedParts[2];
		    Integer roomId = RoomDAO.getRoomId(room);
		    if (roomId == null) {
		    	sendMessage("ERR Room does not exist: " + room); 
		    	return; 
		    }
		    Integer fromId = UserDAO.getUserId(username);
		    Integer toId = UserDAO.getUserId(targetUser);
		    if (fromId == null || toId == null) { 
		    	sendMessage("ERR User not found"); 
		    	return; 
		    }
		    if (!RoomDAO.isMember(roomId, fromId)) { 
		    	sendMessage("ERR You are not a member of room " + room); 
		    	return; 
		    }
		    if (RoomDAO.isMember(roomId, toId)) { 
		    	sendMessage("ERR User already in room"); 
		    	return; 
		    }
		    boolean created = RoomDAO.createInvite(roomId, fromId, toId);
		    if (!created) { 
		    	sendMessage("ERR Invite already exists"); 
		    	return; 
		    }
		    sendMessage("SYS Invited " + targetUser + " to room " + room);
		    ClientHandler otherUser = server.getOnlineUser(targetUser);
		    if (otherUser != null) 
		    	otherUser.sendMessage("ROOMINVITE " + room + " " + username);
		    ActivityLogDAO.log(username, "ROOM_INVITE_SENT", "room=" + room + ",to=" + targetUser);
		    return;
		} else if(command.equals("ROOM_INVITE_ACCEPT")) {
			if (splittedParts.length < 2) { 
				sendMessage("ERR ROOM_INVITE_ACCEPT"); 
				return; 
			}
		    String room = splittedParts[1];
		    Integer roomId = RoomDAO.getRoomId(room);
		    Integer myId = UserDAO.getUserId(username);
		    if (roomId == null || myId == null) { 
		    	sendMessage("ERR Room/user invalid"); 
		    	return; 
		    }
		    if (!RoomDAO.inviteExists(roomId, myId)) { 
		    	sendMessage("ERR No pending invite for " + room); 
		    	return; 
		    }
		    RoomDAO.deleteInvite(roomId, myId);
		    RoomDAO.addMember(roomId, myId);
		    joinRoomFlow(room);
		    sendMessage("SYS Invivte accepted for room " + room);
		    ActivityLogDAO.log(username, "ROOM_INVITE_ACCEPT", "room=" + room);
		    return;
		} else if(command.equals("ROOM_INVITE_DECLINE")) {
			if (splittedParts.length < 2) { 
				sendMessage("ERR ROOM_INVITE_DECLINE"); 
				return; 
			}
		    String room = splittedParts[1].trim();
		    Integer roomId = RoomDAO.getRoomId(room);
		    Integer myId = UserDAO.getUserId(username);
		    if (roomId == null || myId == null) { 
		    	sendMessage("ERR Room/user invalid"); 
		    	return; 
		    }
		    RoomDAO.deleteInvite(roomId, myId);
		    if (joinedRooms.contains(room)) {
		        server.leaveRoomMemory(room, this);
		        joinedRooms.remove(room);
		    }
		    if (RoomDAO.isMember(roomId, myId)) {
		        RoomDAO.removeMember(roomId, myId);
		    }
		    sendMessage("SYS Invite declined for room " + room);
		    ActivityLogDAO.log(username, "ROOM_INVITE_DECLINE", "room=" + room);
		    return;
		} else if(command.equals("DM_HISTORY")) {
			if (splittedParts.length < 2) { 
				sendMessage("ERR DM_HISTORY"); 
				return; 
			}
		    String otherU = splittedParts[1];
		    int limit = 50;
		    for (MessageDAO.Message m : MessageDAO.getDMHistory(username, otherU, limit)) {
		        sendMessage("DMHISTORYLINE " + otherU + " " + m.from + " " + m.bodyText);
		    }
		    sendMessage("DMHISTORYDONE " + otherU);
		    return;
		} else if (command.equals("ROOM_HISTORY")) {
			if (splittedParts.length < 2) { 
				sendMessage("ERR ROOM_HISTORY"); 
				return;
			}
		    String room = splittedParts[1];
		    int limit = 50;
		    Integer roomId = RoomDAO.getRoomId(room);
		    Integer myId = UserDAO.getUserId(username);
		    if (roomId == null || myId == null || !RoomDAO.isMember(roomId, myId)) {
		        sendMessage("ERR Not allowed to view history for " + room);
		        return;
		    }
		    for (MessageDAO.Message m : MessageDAO.getRoomHistory(room, limit)) {
		        sendMessage("ROOMHISTLINE " + room + " " + m.from + " " + m.bodyText);
		    }
		    sendMessage("ROOMHISTORYDONE " + room);
		    return;
		} else {
			server.roomBroadcast("lobby",  "ROOMFROM lobby " + username + " " + raw, null);
		}
	}
	
}

