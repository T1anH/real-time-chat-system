package database;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class ActivityLogDAO {

	public static void initActivityLogTable() {
		String sqlCommand =
	            "CREATE TABLE IF NOT EXISTS activity_log (" +
	            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
	            "  user_id INTEGER," +
	            "  event TEXT NOT NULL," +
	            "  details TEXT," +
	            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
	            "  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL" +
	            ");";
		try(Connection conn = DatabaseManager.getConnection();
			Statement statement = conn.createStatement()){
			statement.execute(sqlCommand);
			System.out.println("Activity log table created.");
		} catch (SQLException e) {
			System.err.println("ERR initActivityLogTable error: " + e.getMessage());
		}
	}
	
	public static void log(String username, String event, String details) {
		if (event == null || event.trim().isEmpty()) return;
		if (username != null) username = username.trim();
		Integer userId = null;
		if (username != null && !username.isEmpty()) {
		    userId = UserDAO.getUserId(username);
		}
        String sqlCommand = "INSERT INTO activity_log(user_id, event, details) VALUES(?, ?, ?)";
        try(Connection conn = DatabaseManager.getConnection();
        	PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
        	if(userId == null) {
        		pStatement.setNull(1,  Types.INTEGER);
        	} else {
        		pStatement.setInt(1,  userId);
        	}
        	pStatement.setString(2, event.trim());
        	pStatement.setString(3, details == null ? "" : details);
        	pStatement.executeUpdate();
        } catch (SQLException e) {
        	System.err.println("ERR ActivityLogDAO.log error: " + e.getMessage());
        }
	}
	
}
