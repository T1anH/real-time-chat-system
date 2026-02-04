package server;

import database.ActivityLogDAO;
import database.FriendDAO;
import database.MessageDAO;
import database.RoomDAO;
import database.UserDAO;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

	private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<ClientHandler>> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>();

	public void start(int port) {
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Chat server started on port " + port);
			
			while(true) {
				Socket clientSocket = serverSocket.accept();				
				ClientHandler handler = new ClientHandler(clientSocket, this);
				Thread thread = new Thread(handler, "ClientHandler");
				thread.start();
			}
			
		} catch (IOException e) {
			System.err.println("Server error: " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if(serverSocket != null && !serverSocket.isClosed()) {
					serverSocket.close();
				}
			} catch(IOException ignored) {}
		}
	}
	
	public boolean registerOnline(String username, ClientHandler handler) {
		boolean okCondition = online.putIfAbsent(username, handler) == null;
	    if (okCondition) {
	    	statuses.putIfAbsent(username,  "online");
	    	broadcastOnlineList();
	    	statusesSnapshot();
	    }
	    return okCondition;
	}
	
	public void unregisterOnline(String username) {
		if(username != null) {
			online.remove(username);
			statuses.remove(username);
		}
		broadcastOnlineList();
		statusesSnapshot();
	}
	
	public ClientHandler getOnlineUser(String username) {
		return online.get(username);
	}
	
	public void broadcastOnlineList() {
		List<String> users = new ArrayList<>(online.keySet());
		Collections.sort(users);
		String list = String.join(",", users);
		String msg = "ONLINE " + list;
		for(ClientHandler clientHandler : online.values()) {
			clientHandler.sendMessage(msg);
		}
	}
	
	private String normalizeStatus(String s) {
	    if (s == null) {
	        return "online";
	    }

	    s = s.trim().toLowerCase();

	    if (s.equals("away") || s.equals("busy") || s.equals("online")) {
	        return s;
	    } else {
	        return "online";
	    }
	}


	
	public void setStatus(String username, String status) {
		if(username == null) return;
		if(!online.containsKey(username)) return;
		String normalized = normalizeStatus(status);
		statuses.put(username, normalized);
		statusesSnapshot();
	}
	
	public void statusesSnapshot() {
        List<String> users = new ArrayList<>(online.keySet());
        Collections.sort(users);
        List<String> parts = new ArrayList<>();
        
        for (String u : users) {
            parts.add(u + ":" + statuses.getOrDefault(u, "online"));
        }
        String message = "STATUSES " + String.join(",", parts);

        for (ClientHandler ch : online.values()) {
            ch.sendMessage(message);
        }
    }
	
	private Set<ClientHandler> roomSet(String room){
		return rooms.computeIfAbsent(room, r -> ConcurrentHashMap.newKeySet());
	}
	
	public void joinRoom(String room, ClientHandler handler) {
		roomSet(room).add(handler);
		handler.sendMessage("SYS Joined room " + room);
		roomBroadcast(room, "SYS " + handler.getUsername() + " joined " + room, handler);
	}
	
	public void leaveRoom(String room, ClientHandler handler) {
		Set<ClientHandler> set = rooms.get(room);
		if(set != null) {
			set.remove(handler);
			handler.sendMessage("SYS Left room " + room);
			roomBroadcast(room, "SYS " + handler.getUsername() + " left " + room, handler);
			
			if (set.isEmpty()) rooms.remove(room, set);
		}
	}
	
	public void roomBroadcast(String room, String message, ClientHandler except) {
		Set<ClientHandler> set = rooms.get(room);
		if(set == null) return;
		
		for(ClientHandler clientHandler : new ArrayList<>(set)) {
			if(clientHandler != except) clientHandler.sendMessage(message);
		}
	}
	
	public void sendDM(String toUser, String message, ClientHandler from) {
		ClientHandler target = online.get(toUser);
		if(target == null) {
			from.sendMessage("ERR User not online: " + toUser);
			return;
		}
		target.sendMessage("DMFROM " + from.getUsername() + " " + message);
		from.sendMessage("SYS DM sent to " + toUser);
	}
	
	public void joinRoomMemory(String room, ClientHandler handler) {
	    roomSet(room).add(handler);
	}

	public void leaveRoomMemory(String room, ClientHandler handler) {
	    Set<ClientHandler> set = rooms.get(room);
	    if (set != null) {
	        set.remove(handler);
	        if (set.isEmpty()) rooms.remove(room, set);
	    }
	}

	
	public static void main(String[] args) {

		UserDAO.initDatabase();
		FriendDAO.initFriendsTable();
		RoomDAO.initRoomTable();
		MessageDAO.initMessageTable();
		ActivityLogDAO.initActivityLogTable();
		ChatServer server = new ChatServer();
		int port = 5000;
		server.start(port);
	}
}
