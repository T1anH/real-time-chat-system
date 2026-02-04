package database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

public class UserDAO {
	
	public enum RegisterResult { OK, USERNAME_TAKEN, INVALID_PASSWORD, INVALID_USERNAME, DB_ERROR }
	
	public enum LoginResult { OK, NO_SUCH_USER, WRONG_PASSWORD, DB_ERROR }
	
	public static void initDatabase() {
		String sqlCommand = "CREATE TABLE IF NOT EXISTS users (" +
							"id INTEGER PRIMARY KEY AUTOINCREMENT," +
							"username TEXT UNIQUE NOT NULL," +
							"password_hash TEXT NOT NULL," +
							"created_at DATETIME DEFAULT CURRENT_TIMESTAMP" + ");";
		try(Connection conn = DatabaseManager.getConnection();
			Statement statement = conn.createStatement()) {
			statement.execute(sqlCommand);
			System.out.println("User table created!");
		} catch (SQLException e) {
			System.err.println("Error while init database: " + e.getMessage());
		}
	}
	
	public static RegisterResult registerUser(String username, String password) {
		if(username == null || password == null) return RegisterResult.INVALID_USERNAME;
		username = username.trim();
		if(username.isEmpty()) return RegisterResult.INVALID_USERNAME;
		if(!passwordCheck(password)) return RegisterResult.INVALID_PASSWORD;
		if(usernameExists(username)) return RegisterResult.USERNAME_TAKEN;
		String sqlCommand = "INSERT INTO users(username, password_hash) VALUES(?, ?)";
	    try (Connection conn = DatabaseManager.getConnection();
	         PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
	         pStatement.setString(1, username);
	         pStatement.setString(2, hashPassword(password));
	         pStatement.executeUpdate();
	         return RegisterResult.OK;
	    } catch (SQLException e) {
	         return RegisterResult.DB_ERROR;
	    }
	}
	
	public static LoginResult validateLogin(String username, String password) {
		if(username == null || password == null) return LoginResult.NO_SUCH_USER;
		username = username.trim();
		if(username.isEmpty()) return LoginResult.NO_SUCH_USER;
	    if (!usernameExists(username)) return LoginResult.NO_SUCH_USER;
	    String sqlCommand = "SELECT 1 FROM users WHERE username = ? AND password_hash = ?";
	    try (Connection conn = DatabaseManager.getConnection();
	         PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
	         pStatement.setString(1, username);
	         pStatement.setString(2, hashPassword(password));
	         try (ResultSet resultSet = pStatement.executeQuery()) {
	        	 if (resultSet.next()) {
	        		    return LoginResult.OK;
	        		} else {
	        		    return LoginResult.WRONG_PASSWORD;
	        		}
	         }
	    } catch (SQLException e) {
	         return LoginResult.DB_ERROR;
	    }
	}
	
	/*public static boolean registerUser(String username, String password) {
		if (username == null || password == null) return false;
		username = username.trim();
		if (username.isEmpty()) return false;
		String sqlCommand = "INSERT INTO users(username, password_hash) VALUES(?, ?)";
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
			 pStatement.setString(1,  username);
			 pStatement.setString(2, hashPassword(password));
			 pStatement.executeUpdate();
			 return true;
		} catch (SQLException e) {
			 System.err.println("Register error: " + e.getMessage());
			 return false;
		}
	}*/
	
	/*public static boolean validateLogin(String username, String password) {
		if (username == null || password == null) return false;
	    username = username.trim();
	    if (username.isEmpty()) return false;
		String sqlCommand = "SELECT 1 FROM users WHERE username = ? AND password_hash = ?";
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
			 pStatement.setString(1, username);
			 pStatement.setString(2, hashPassword(password));
			 try(ResultSet resultSet = pStatement.executeQuery()){
				 return resultSet.next();
			 }
		} catch (SQLException e) {
			System.err.println("Login error: " + e.getMessage());
			return false;
		}
	}*/
	
	private static String hashPassword(String password) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = messageDigest.digest(password.getBytes(StandardCharsets.UTF_8));
			
			StringBuilder sb = new StringBuilder();
			for(byte b: bytes) {
				sb.append(String.format("%02X", b));
			}
			return sb.toString();
			
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Error while hashing: ", e);
		}
	}
	
	public static void dropUsersTable() {
		String sqlCommand = "DROP TABLE IF EXISTS users";
		try(Connection conn = DatabaseManager.getConnection();
			Statement statement = conn.createStatement()) {
			statement.execute(sqlCommand);
			System.out.println("User Table Dropped!");
		} catch (SQLException e) {
			System.err.println("Error dropping users table: " + e.getMessage());
		}
	}
	
	public static Integer getUserId(String username) {
		String sqlCommand = "SELECT id FROM users WHERE username = ?";
		try(Connection conn = DatabaseManager.getConnection();
			PreparedStatement pStatement = conn.prepareStatement(sqlCommand)){
			pStatement.setString(1, username);
			try(ResultSet rs = pStatement.executeQuery()){
				if(rs.next()) return rs.getInt(1);
			}
		} catch(SQLException e) {
			System.err.println("getUserId error: " + e.getMessage());
		}
		return null;
	}
	
	public static boolean usernameExists(String username) {
		if(username == null) return false;
		username = username.trim();
		if(username.isEmpty()) return false;
		String sqlCommand = "SELECT 1 FROM users WHERE username = ?";
		try (Connection conn = DatabaseManager.getConnection();
		     PreparedStatement pStatement = conn.prepareStatement(sqlCommand)) {
		     pStatement.setString(1, username);
		     try (ResultSet resultSet = pStatement.executeQuery()) {
		         return resultSet.next();
		     }
		 } catch (SQLException e) {
			 return false;
		 }
	}
	
	public static boolean passwordCheck(String password) {
		if(password == null) return false;
		if(password.length() < 6) return false;
		return !password.contains(" ");
	}
}
