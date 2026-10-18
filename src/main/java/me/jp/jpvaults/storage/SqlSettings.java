package me.jp.jpvaults.storage;

/**
 * Ajustes de conexion del backend SQL. La URL ya viene montada: el plugin la
 * compone a partir de host/puerto/base o usa la que escriba el administrador.
 *
 * <p>El driver se pasa como objeto ({@link java.sql.Driver}) en vez de cargarse
 * aqui dentro para que el backend se pueda probar con cualquier driver y para
 * no depender de {@code DriverManager}, que rechaza drivers cargados por el
 * classloader de otro plugin.
 */
public final class SqlSettings {

    private final String driverClass;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final int poolSize;
    private final long connectionTimeoutMillis;

    public SqlSettings(String driverClass, String jdbcUrl, String username, String password,
                       String tablePrefix, int poolSize, long connectionTimeoutMillis) {
        this.driverClass = driverClass == null ? "" : driverClass.trim();
        this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.tablePrefix = tablePrefix == null ? "" : tablePrefix.trim();
        this.poolSize = Math.max(1, Math.min(32, poolSize));
        this.connectionTimeoutMillis = Math.max(0L, connectionTimeoutMillis);
    }

    /** Nombre de la clase del driver, vacio para autodetectar. */
    public String getDriverClass() {
        return driverClass;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /** Prefijo de las tablas, sin espacios y sin caracteres raros. */
    public String getTablePrefix() {
        return tablePrefix;
    }

    /** Cuantas conexiones se reutilizan como maximo. */
    public int getPoolSize() {
        return poolSize;
    }

    public long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    /** URL sin credenciales, para logs y mensajes. */
    public String describe() {
        String url = jdbcUrl;
        int query = url.indexOf('?');
        if (query >= 0) {
            url = url.substring(0, query);
        }
        return url + " (tablas '" + tablePrefix + "players' y '" + tablePrefix + "vaults')";
    }
}
