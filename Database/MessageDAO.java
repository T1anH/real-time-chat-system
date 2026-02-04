package database;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

	public static class Message{
		public final String from;
		public final String bodyText;
		public final String createdAt;
		
		public Message(String from, String bodyText, String createdAt) {
			this.from = from;
			this.bodyText = bodyText;
			this.createdAt = createdAt;
		}
	}
	
	public static void initMessageTable() {
		String sqlCommand ="CREATE TABLE IF NOT EXISTS messages (" +
                         "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         "  kind TEXT NOT NULL," + 
                         "  room_id INTEGER," +                 
                         "  from_id INTEGER," +
                         "  to_id INTEGER," +                      
                         "  body TEXT NOT NULL," +
                         "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                         "  FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE," +
                         "  FOREIGN KEY(from_id) REFERENCES users(id) ON DELETE SET NULL," +
                         "  FOREIGN KEY(to_id) REFERENCES users(id) ON DELETE SET NULL" +
                         ");";
		try(Connection conn = DatabaseManager.getConnection();
			Statement statement = conn.createStatement()){
			statement.execute(sqlCommand);
			System.out.println("Message table created.");
		} catch (SQLException e) {
			System.err.println("initMessagesTable error: " + e.getMessage());
		}
	}
	
    public static void saveDM(String fromUser, String toUser, String body) {
        Integer fromId = UserDAO.getUserId(fromUser);
        Integer toId = UserDAO.getUserId(toUser);
        if (fromId == null || toId == null) return;
        String sqlCommand = "INSERT INTO messages(kind, from_id, to_id, body) VALUES('DM', ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, fromId);
             pStatement.setInt(2, toId);
             pStatement.setString(3, body);
             pStatement.executeUpdate();
        } catch (SQLException e) {
             System.err.println("saveDM error: " + e.getMessage());
        }
    }
	
    public static void saveRoom(String fromUser, String roomName, String body) {
        Integer fromId = UserDAO.getUserId(fromUser);
        Integer roomId = RoomDAO.getRoomId(roomName);
        if (fromId == null || roomId == null) return;
        String sqlCommand = "INSERT INTO messages(kind, room_id, from_id, body) VALUES('ROOM', ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, fromId);
             pStatement.setString(3, body);
             pStatement.executeUpdate();
        } catch (SQLException e) {
             System.err.println("saveRoom error: " + e.getMessage());
        }
    }
    
    public static List<Message> getDMHistory(String UserA, String UserB, int limit){
    	Integer IdA = UserDAO.getUserId(UserA);
    	Integer IdB = UserDAO.getUserId(UserB);
    	List<Message> out = new ArrayList<>();
    	if(IdA == null || IdB == null) return out;
    	String sqlCommand = "SELECT u.username, m.body, m.created_at " +
                			"FROM messages m " +
                			"JOIN users u ON u.id = m.from_id " +
                			"WHERE m.kind='DM' AND ((m.from_id=? AND m.to_id=?) OR (m.from_id=? AND m.to_id=?)) " +
                			"ORDER BY m.id DESC LIMIT ?";
    	try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, IdA); 
             pStatement.setInt(2, IdB);
             pStatement.setInt(3, IdB); 
             pStatement.setInt(4, IdA);
             pStatement.setInt(5, Math.max(1, Math.min(limit, 200)));
             try (ResultSet resultSet = pStatement.executeQuery()) {
            	 while (resultSet.next()) {
                     out.add(new Message(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3)));
                 }
             }
        } catch (SQLException e) {
            System.err.println("getDMHistory error: " + e.getMessage());
        }
           List<Message> resultList = new ArrayList<>();
           for (int i = out.size() - 1; i >= 0; i--) 
        	   resultList.add(out.get(i));
           return resultList;
    }
    
    public static List<Message> getRoomHistory(String roomName, int limit){
        Integer roomId = RoomDAO.getRoomId(roomName);
        List<Message> out = new ArrayList<>();
        if (roomId == null) return out;
        String sqlCommand = "SELECT u.username, m.body, m.created_at " +
        					"FROM messages m " +
        					"JOIN users u ON u.id = m.from_id " +
        					"WHERE m.kind='ROOM' AND m.room_id=? " +
        					"ORDER BY m.id DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
             pStatement.setInt(1, roomId);
             pStatement.setInt(2, Math.max(1, Math.min(limit, 200)));
             try (ResultSet resultSet = pStatement.executeQuery()) {
                 while (resultSet.next()) 
                	 out.add(new Message(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3)));
             }
        } catch (SQLException e) {
            System.err.println("getRoomHistory error: " + e.getMessage());
        }

        List<Message> resultList = new ArrayList<>();
        for (int i = out.size() - 1; i >= 0; i--) 
        	resultList.add(out.get(i));
        return resultList;
    }
    
}
