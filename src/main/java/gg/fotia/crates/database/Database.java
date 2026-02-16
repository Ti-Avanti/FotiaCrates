package gg.fotia.crates.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Database {
    boolean connect();
    void close();
    Connection getConnection() throws SQLException;
    void createTables();
}
