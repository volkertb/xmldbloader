/*
 * © Nick Sayer, original source: http://www.kfu.com/~nsayer/Java/dyn-jdbc.html">http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
 */
package com.kfu.www.nsayer;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * <p>
 * If you're going to do any sort of database activity in Java, you'll probably be using JDBC. Like ODBC before it, JDBC is a great way to insure that your
 * program is free of any ties to the underlying database. Traditionally, the mechanism is that you put the JDBC driver somewhere in the classpath and then
 * use class.forName() to find and load the driver. One problem with this is that it presumes that your driver is in the classpath. This means either
 * packaging the driver in your jar, or having to stick the driver somewhere (probably unpacking it too), or modifying your classpath.
 * </p>
 * <p>
 * "But why not use something like URLClassLoader and the overload of class.forName() that lets you specify the ClassLoader?" Because the DriverManager will
 * refuse to use a driver not loaded by the system ClassLoader. Ouch!
 * </p>
 * <p>
 * The workaround for this is to create a shim class that implements java.sql.Driver. This shim class will do nothing but call the methods of an instance of a
 * JDBC driver that we loaded dynamically. Something like this:
 * </p>
 *
 * @author Nick Sayer
 * @see <a href="http://www.kfu.com/~nsayer/Java/dyn-jdbc.html">http://www.kfu.com/~nsayer/Java/dyn-jdbc.html</a>
 * @since 23-09-2016
 */
public class DriverShim implements Driver {
    private Driver driver;

    public DriverShim(Driver d) {
        this.driver = d;
    }

    @Override
    public boolean acceptsURL(String u) throws SQLException {
        return this.driver.acceptsURL(u);
    }

    @Override
    public Connection connect(String u, Properties p) throws SQLException {
        return this.driver.connect(u, p);
    }

    @Override
    public int getMajorVersion() {
        return this.driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return this.driver.getMinorVersion();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
        return this.driver.getPropertyInfo(u, p);
    }

    @Override
    public boolean jdbcCompliant() {
        return this.driver.jdbcCompliant();
    }

    /**
     * This method is a bit tricky, since it does not exist in Java 6, but it does in higher versions and it's abstract, so to maintain compatibility with
     * Java 6, we have to implement it in a way that would work both on version 6 and higher.
     * @return the parent logger.
     * @throws SQLFeatureNotSupportedException if the requested action is not supported.
     * @throws UnsupportedOperationException if this method is invoked while running on Java 6 or lower.
     * @author Volkert de Buisonj&eacute;
     */
    // @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        try {
            Method getParentLogger = driver.getClass().getDeclaredMethod("getParentLogger");
            return (Logger)getParentLogger.invoke(this.driver);
        } catch (Exception e) {
            throw new UnsupportedOperationException(
                    "The JVM version on which you are running this application does not support this method in its JDBC API.",
                    e
            );
        }

    }

}
