package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

	private static final String DB_URL = "jdbc:sqlite:chatapp.db";
	static {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			System.err.println("Failed to load SQLite JDBC driver: " + e.getMessage());
		}
	}
	
	public static Connection getConnection() throws SQLException{
		Connection conn = DriverManager.getConnection(DB_URL);
		try (Statement st = conn.createStatement()) {
		    st.execute("PRAGMA foreign_keys = ON");
		}
		return conn;

	}
}
