package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FriendDAO {

	public static void initFriendsTable() {
		String friendshipCommand =
			       "CREATE TABLE IF NOT EXISTS friendships (" +
			       "  user_id INTEGER NOT NULL," +
			       "  friend_id INTEGER NOT NULL," +
			       "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
			       "  PRIMARY KEY(user_id, friend_id)," +
			       "  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
			       "  FOREIGN KEY(friend_id) REFERENCES users(id) ON DELETE CASCADE" +
			       ");";
		String requestsCommand =
				"CREATE TABLE IF NOT EXISTS friend_requests (" +
		        "  from_id INTEGER NOT NULL," +
		        "  to_id INTEGER NOT NULL," +
		        "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
		        "  PRIMARY KEY(from_id, to_id)," +
		        "  FOREIGN KEY(from_id) REFERENCES users(id) ON DELETE CASCADE," +
		        "  FOREIGN KEY(to_id) REFERENCES users(id) ON DELETE CASCADE" +
		        ");";
		try (Connection conn = DatabaseManager.getConnection();
	            Statement statement = conn.createStatement()) {
				statement.execute(friendshipCommand);
	            statement.execute(requestsCommand);
	            System.out.println("Friendships and friend_request table created!");
	        } catch (SQLException e) {
	            System.err.println("Init friendships error: " + e.getMessage());
	        }
	}
	
	public static boolean sendRequest(String fromUser, String toUser) {
		if(fromUser == null || toUser == null) return false;
		if(fromUser.equals(toUser)) return false;
		Integer fromId = UserDAO.getUserId(fromUser);
	    Integer toId = UserDAO.getUserId(toUser);
	    if(fromId == null || toId == null) return false;
	    if(areFriends(fromId, toId)) return false;
	    if(requestExists(fromId, toId) || requestExists(toId, fromId)) return false;
	    String sqlCommand = "INSERT OR IGNORE INTO friend_requests(from_id, to_id) VALUES(?, ?)";
	    try(Connection conn = DatabaseManager.getConnection();
	    	PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
	    	pStatement.setInt(1,  fromId);
	    	pStatement.setInt(2,  toId);
	    	return pStatement.executeUpdate() > 0;
	    } catch (SQLException e) {
	    	System.err.println("sendRequest error: " + e.getMessage());
	    	return false;
	    }
	}
	
	private static boolean areFriends(int a, int b) {
	    String sqlCommand = "SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?";
	    try(Connection conn = DatabaseManager.getConnection();
	    	PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
	    	pStatement.setInt(1, a);
	    	pStatement.setInt(2, b);
	    	try(ResultSet resultSet = pStatement.executeQuery()){
	    		return resultSet.next();
	    	}
	    } catch (SQLException e) {
	        System.err.println("areFriends error: " + e.getMessage());
	    	return false;
	    }
	}
	
	private static boolean requestExists(int fromId, int toId) {
	    String sqlCommand = "SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?";
	    try (Connection conn = DatabaseManager.getConnection();
	         PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
	         pStatement.setInt(1, fromId);
	         pStatement.setInt(2, toId);
	         try (ResultSet resultSet = pStatement.executeQuery()) {
	        	 return resultSet.next();
	         }
	     } catch (SQLException e) {
	    	 System.err.println("requestExists error: " + e.getMessage());
	    	 return false;
	     }
	}
	
	public static List<String> getIncomingRequests(String toUser){
		Integer toId = UserDAO.getUserId(toUser);
		List<String> incomingReq = new ArrayList<>();
		if(toId == null) return incomingReq;
		String sqlCommand =
		        "SELECT u.username " +
		        "FROM friend_requests r " +
		        "JOIN users u ON u.id = r.from_id " +
		        "WHERE r.to_id = ? " +
		        "ORDER BY u.username";
		try(Connection conn = DatabaseManager.getConnection();
			PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
			pStatement.setInt(1,  toId);
			try(ResultSet resultSet = pStatement.executeQuery()){
				while(resultSet.next())
					incomingReq.add(resultSet.getString(1));
			}
		} catch (SQLException e) {
			System.err.println("getIncomingRequests error: " + e.getMessage());
		}
		return incomingReq;
	}
	
	public static boolean acceptRequest(String toUser, String fromUser) {
		 Integer toId = UserDAO.getUserId(toUser);
		 Integer fromId = UserDAO.getUserId(fromUser);
		 if (toId == null || fromId == null) return false;
		 if(!requestExists(fromId,  toId)) return false;
		 String sqlCommand = "DELETE FROM friend_requests WHERE from_id = ? AND to_id = ?";
		 String sqlCommand2 = "INSERT OR IGNORE INTO friendships(user_id, friend_id) VALUES(?, ?)";

		 try(Connection conn = DatabaseManager.getConnection()){
			 conn.setAutoCommit(false);
			 try {
				 try(PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
					 pStatement.setInt(1, fromId);
					 pStatement.setInt(2, toId);
					 pStatement.executeUpdate();
				 }
				 
				 try(PreparedStatement pStatement = conn.prepareStatement(sqlCommand2)){
					 pStatement.setInt(1,  toId);
					 pStatement.setInt(2,  fromId);
					 pStatement.executeUpdate();
					 pStatement.setInt(1, fromId);
					 pStatement.setInt(2, toId);
					 pStatement.executeUpdate();
				 }
				 conn.commit();
				 return true;
			 } catch (SQLException e) {
				 conn.rollback();
				 System.err.println("acceptRequest error: " + e.getMessage());
				 return false;
			 } finally {
				 conn.setAutoCommit(true);
			 }
		 } catch (SQLException e) {
			 System.err.println("acceptRequest error: " + e.getMessage());
			 return false;
		 }
	}
	
	public static boolean declineRequest(String toUser, String fromUser) {
		Integer toId = UserDAO.getUserId(toUser);
		Integer fromId = UserDAO.getUserId(fromUser);
		if(toId == null || fromId == null) return false;
		String sqlCommand = "DELETE FROM friend_requests WHERE from_id = ? AND to_id = ?";
		try(Connection conn = DatabaseManager.getConnection();
			PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
			pStatement.setInt(1,  fromId);
			pStatement.setInt(2,  toId);
			return pStatement.executeUpdate() > 0;		
		} catch (SQLException e) {
			System.err.println("declineRequest error: " + e.getMessage());
			return false;
		}
	}
	
	public static boolean addFriend(String user, String friend) {
		if (user == null || friend == null) return false;
		if (user.equals(friend)) return false;
		Integer userId = UserDAO.getUserId(user);
		Integer friendId = UserDAO.getUserId(friend);
		if(userId == null || friendId == null) return false;
		return insertEdge(userId, friendId) && insertEdge(friendId, userId);
	}
	
	public static boolean removeFriend(String user, String friend) {
		if (user == null || friend == null) return false;
		if (user.equals(friend)) return false;
		Integer userId = UserDAO.getUserId(user);
		Integer friendId = UserDAO.getUserId(friend);
		if(userId == null || friendId == null) return false;
		return deleteEdge(userId, friendId) && deleteEdge(friendId, userId);
	}
	
	public static List<String> getFriends(String user){
		Integer userId = UserDAO.getUserId(user);
		List<String> friends = new ArrayList<>();
		if(userId == null) return friends;
		String sqlCommand = "SELECT u.username FROM friendships f " +
                	 "JOIN users u ON u.id = f.friend_id " +
                	 "WHERE f.user_id = ? ORDER BY u.username";

		try (Connection conn = DatabaseManager.getConnection();
	         PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
	         pStatement.setInt(1, userId);
	         try (ResultSet rs = pStatement.executeQuery()) {
	        	 while (rs.next()) friends.add(rs.getString(1));
	         }
	    } catch (SQLException e) {
	         System.err.println("Get friends error: " + e.getMessage());
	    }
	    return friends;
	}
	
	private static boolean insertEdge(int userId, int friendId) {
        String sqlCommand = "INSERT OR IGNORE INTO friendships(user_id, friend_id) VALUES(?, ?)";
        try(Connection conn = DatabaseManager.getConnection();
        	PreparedStatement pstatement = conn.prepareStatement(sqlCommand)){
        	pstatement.setInt(1, userId);
        	pstatement.setInt(2, friendId);
        	pstatement.executeUpdate();
        	return true;
        } catch(SQLException e) {
        	System.err.println("Add friend edge error: " + e.getMessage());
        	return false;
        }
	}
	
	private static boolean deleteEdge(int userId, int friendId) {
        String sqlCommand = "DELETE FROM friendships WHERE user_id = ? AND friend_id = ?";
        try(Connection conn = DatabaseManager.getConnection();
        	PreparedStatement pstatement = conn.prepareStatement(sqlCommand)){
        	pstatement.setInt(1,  userId);
        	pstatement.setInt(2, friendId);
        	pstatement.executeUpdate();
        	return true;
        } catch (SQLException e) {
        	System.err.println("Remove friend edge error: "  + e.getMessage());
        	return false;
        }
	}
	
}
