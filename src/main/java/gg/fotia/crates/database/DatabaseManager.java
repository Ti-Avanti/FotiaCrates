package gg.fotia.crates.database;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.ConfigManager;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private final FotiaCrates plugin;
    private Database database;

    public DatabaseManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        ConfigManager config = plugin.getConfigManager();
        String type = config.getDatabaseType();

        if (type.equalsIgnoreCase("mysql")) {
            database = new MySQLDatabase(
                    plugin,
                    config.getMySQLHost(),
                    config.getMySQLPort(),
                    config.getMySQLDatabase(),
                    config.getMySQLUsername(),
                    config.getMySQLPassword()
            );
        } else {
            database = new SQLiteDatabase(plugin, config.getSQLiteFile());
        }

        if (!database.connect()) {
            return false;
        }

        database.createTables();
        return true;
    }

    public void close() {
        if (database != null) {
            database.close();
        }
    }

    public Connection getConnection() throws SQLException {
        return database.getConnection();
    }

    public Database getDatabase() {
        return database;
    }
}
