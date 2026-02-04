package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class RoomDAO {

	public static void initRoomTable() {
		String rooms =
                "CREATE TABLE IF NOT EXISTS rooms (" +
                        "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "  name TEXT UNIQUE NOT NULL," +
                        "  owner_id INTEGER," +
                        "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE SET NULL" +
                        ");";

        String members =
                "CREATE TABLE IF NOT EXISTS room_members (" +
                        "  room_id INTEGER NOT NULL," +
                        "  user_id INTEGER NOT NULL," +
                        "  joined_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "  PRIMARY KEY(room_id, user_id)," +
                        "  FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE," +
                        "  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ");";

        String invites =
                "CREATE TABLE IF NOT EXISTS room_invites (" +
                        "  room_id INTEGER NOT NULL," +
                        "  from_id INTEGER NOT NULL," +
                        "  to_id INTEGER NOT NULL," +
                        "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "  PRIMARY KEY(room_id, to_id)," +
                        "  FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE," +
                        "  FOREIGN KEY(from_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "  FOREIGN KEY(to_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ");";
        
        try(Connection conn = DatabaseManager.getConnection();
        	Statement statement = conn.createStatement()) {
        	statement.execute(rooms);
        	statement.execute(members);
        	statement.execute(invites);
        	System.out.println("Rooms/members/invites tables created");
        } catch (SQLException e) {
        	System.err.println("RoomDAO init error: " + e.getMessage());
        }
	}
	
    public static Integer getRoomId(String roomName) {
        String sqlCommand = "SELECT id FROM rooms WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setString(1, roomName);
             try (ResultSet resultSet = pStatement.executeQuery()) {
                 if (resultSet.next()) return resultSet.getInt(1);
             }
        } catch (SQLException e) {
            System.err.println("getRoomId error: " + e.getMessage());
        }
        return null;
    }
	
    public static Integer ensureRoomExist(String roomName, String ownerUsername) {
    	Integer exist = getRoomId(roomName);
    	if(exist != null) return exist;
    	Integer ownerId = UserDAO.getUserId(ownerUsername);
    	String sqlCommand = "INSERT INTO rooms(name, owner_id) VALUES(?, ?)";
    	try(Connection conn = DatabaseManager.getConnection();
    		PreparedStatement pStatement = conn.prepareStatement(sqlCommand, Statement.RETURN_GENERATED_KEYS)) {
    		pStatement.setString(1,  roomName);
    		if(ownerId == null) {
    			pStatement.setNull(2, Types.INTEGER);
    		} else {
    			pStatement.setInt(2, ownerId);
    		}
    		pStatement.executeUpdate();
    		try (ResultSet keys = pStatement.getGeneratedKeys()){
    			if(keys.next()) return keys.getInt(1);
    		}
    	} catch (SQLException e) {
    		System.err.println("ensureRoom error: " + e.getMessage());
    	}
    	return getRoomId(roomName);
    }
    
    public static boolean isMember(Integer roomId, Integer userId) {
    	if(roomId == null || userId == null) return false;
    	String sqlCommand = "SELECT 1 FROM room_members WHERE room_id = ? AND user_id = ?";
    	try(Connection conn = DatabaseManager.getConnection(); 
    		PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
    		pStatement.setInt(1, roomId);
    		pStatement.setInt(2, userId);
    		try(ResultSet resultSet = pStatement.executeQuery()){
    			return resultSet.next();
    		}
    	} catch (SQLException e) {
    		System.err.println("isMember error: " + e.getMessage());
    		return false;
    	}
    }
    
    public static boolean addMember(Integer roomId, Integer userId) {
        if (roomId == null || userId == null) return false;
        String sqlCommand = "INSERT OR IGNORE INTO room_members(room_id, user_id) VALUES(?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, userId);
             pStatement.executeUpdate();
             return true;
        } catch (SQLException e) {
             System.err.println("addMember error: " + e.getMessage());
             return false;
        }
    }
    
    public static boolean removeMember(Integer roomId, Integer userId) {
        if (roomId == null || userId == null) return false;
        String sqlCommand = "DELETE FROM room_members WHERE room_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, userId);
             return pStatement.executeUpdate() > 0;
        } catch (SQLException e) {
             System.err.println("removeMember error: " + e.getMessage());
             return false;
        }
    }
    
    public static boolean inviteExists(Integer roomId, Integer toId) {
        if (roomId == null || toId == null) return false;
        String sqlCommand = "SELECT 1 FROM room_invites WHERE room_id = ? AND to_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, toId);
             try (ResultSet rs = pStatement.executeQuery()) {
                 return rs.next();
             }
        } catch (SQLException e) {
             System.err.println("inviteExists error: " + e.getMessage());
             return false;
        }
    }
    
    public static boolean createInvite(Integer roomId, Integer fromId, Integer toId) {
        if (roomId == null || fromId == null || toId == null) return false;
        String sqlCommand = "INSERT OR IGNORE INTO room_invites(room_id, from_id, to_id) VALUES(?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, fromId);
             pStatement.setInt(3, toId);
             return pStatement.executeUpdate() > 0;
        } catch (SQLException e) {
             System.err.println("createInvite error: " + e.getMessage());
             return false;
        }
    }
    
    
    public static boolean deleteInvite(Integer roomId, Integer toId) {
        if (roomId == null || toId == null) return false;
        String sqlCommand = "DELETE FROM room_invites WHERE room_id = ? AND to_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, toId);
             pStatement.executeUpdate();
             return true;
        } catch (SQLException e) {
             System.err.println("deleteInvite error: " + e.getMessage());
             return false;
        }
    }
    
    public static List<String> listMembers(String roomName){
    	Integer roomId = getRoomId(roomName);
    	List<String> out = new ArrayList<>();
    	if(roomId == null) return out;
    	String sqlCommand ="SELECT u.username FROM room_members m " +
    						"JOIN users u ON u.id = m.user_id " +
    						"WHERE m.room_id = ? ORDER BY u.username";
    	try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             try (ResultSet resultSet = pStatement.executeQuery()) {
                 while (resultSet.next()) {
                	 out.add(resultSet.getString(1));
                 }
             }
         } catch (SQLException e) {
             System.err.println("listMembers error: " + e.getMessage());
         }
         return out;
    }
    
}
