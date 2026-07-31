package gg.fotia.crates.database;

import gg.fotia.crates.FotiaCrates;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLDatabase implements Database {

    private final FotiaCrates plugin;
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private boolean initialConnectLogged = false;

    public MySQLDatabase(FotiaCrates plugin, String host, int port, String database, String username, String password) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public synchronized boolean connect() {
        try {
            HikariConfig config = new HikariConfig();
            // 不使用 autoReconnect：其语义与连接池冲突，会掩盖坏连接，交由 Hikari 管理连接生命周期
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8");
            config.setUsername(username);
            config.setPassword(password);
            int maximumPoolSize = Math.max(2, plugin.getConfigManager().getConfig()
                    .getInt("database.mysql.pool.maximum-size", 4));
            int minimumIdle = Math.max(0, Math.min(maximumPoolSize, plugin.getConfigManager().getConfig()
                    .getInt("database.mysql.pool.minimum-idle", 1)));
            config.setMaximumPoolSize(maximumPoolSize);
            config.setMinimumIdle(minimumIdle);
            config.setIdleTimeout(300000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(600000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            if (!initialConnectLogged) {
                plugin.getLogger().info("MySQL database connected!");
                initialConnectLogged = true;
            }
            return true;
        } catch (Exception e) {
            dataSource = null;
            plugin.getLogger().severe("Failed to connect to MySQL database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL database connection closed.");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        HikariDataSource current = dataSource;
        if (current != null && !current.isClosed()) {
            return current.getConnection();
        }
        // 懒重连需要同步：并发触发会各建一个连接池，其中一个永不关闭造成连接泄漏
        synchronized (this) {
            if (dataSource == null || dataSource.isClosed()) {
                if (!connect()) {
                    throw new SQLException("MySQL connection pool is unavailable");
                }
            }
            return dataSource.getConnection();
        }
    }

    @Override
    public void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 虚拟钥匙表 - 使用 key_id 存储钥匙ID
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_keys (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    key_id VARCHAR(64) NOT NULL,
                    amount INT DEFAULT 0,
                    UNIQUE KEY unique_player_key (uuid, key_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crate_history (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    reward_name VARCHAR(128) NOT NULL,
                    timestamp BIGINT NOT NULL,
                    INDEX idx_uuid (uuid),
                    INDEX idx_timestamp (timestamp),
                    INDEX idx_history_uuid_timestamp_id (uuid, timestamp, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pity_counter (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    count INT DEFAULT 0,
                    UNIQUE KEY unique_player_crate (uuid, crate_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_collected_rewards (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    UNIQUE KEY unique_collected_reward (uuid, crate_id, reward_id),
                    INDEX idx_collected_rewards_uuid_crate (uuid, crate_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crate_locations (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    yaw FLOAT DEFAULT 0,
                    UNIQUE KEY unique_location (world, x, y, z)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // 为旧表添加yaw列（如果不存在）
            try {
                stmt.executeUpdate("ALTER TABLE crate_locations ADD COLUMN yaw FLOAT DEFAULT 0");
            } catch (SQLException ignored) {
                // 列已存在，忽略错误
            }

            // 分层保底使用 crateId#t<次数> 复合键，加宽列避免长 ID 截断（幂等）
            try {
                stmt.executeUpdate("ALTER TABLE pity_counter MODIFY COLUMN crate_id VARCHAR(96) NOT NULL");
            } catch (SQLException ignored) {
                // 已是目标宽度或无权限时忽略
            }

            try {
                stmt.executeUpdate("CREATE INDEX idx_history_uuid_timestamp_id ON crate_history(uuid, timestamp, id)");
            } catch (SQLException ignored) {
                // 索引已存在，忽略错误
            }

            plugin.getLogger().info("Database tables created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }
}
