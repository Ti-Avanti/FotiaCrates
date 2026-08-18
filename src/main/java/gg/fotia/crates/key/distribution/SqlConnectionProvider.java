package gg.fotia.crates.key.distribution;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlConnectionProvider {

    Connection get() throws SQLException;
}
