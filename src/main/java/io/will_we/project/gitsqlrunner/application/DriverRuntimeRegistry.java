package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.support.CompatText;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class DriverRuntimeRegistry {
  private static final Map<String, Boolean> REGISTERED = new ConcurrentHashMap<String, Boolean>();

  private DriverRuntimeRegistry() {
  }

  public static void ensureRegistered(String driverClassName, String driverJarPath) {
    String className = CompatText.requireText(driverClassName, "driverClassName");
    String jarPath = CompatText.requireText(driverJarPath, "driverJarPath");
    String key = className + "@" + jarPath;
    if (REGISTERED.containsKey(key)) {
      return;
    }
    synchronized (DriverRuntimeRegistry.class) {
      if (REGISTERED.containsKey(key)) {
        return;
      }
      try {
        Path path = Paths.get(jarPath).toAbsolutePath().normalize();
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
          throw new IllegalArgumentException("driver jar not found: " + jarPath);
        }
        URLClassLoader classLoader = new URLClassLoader(new URL[]{file.toURI().toURL()}, DriverRuntimeRegistry.class.getClassLoader());
        Class<?> clazz = Class.forName(className, true, classLoader);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        DriverManager.registerDriver(new DriverProxy(driver));
        REGISTERED.put(key, Boolean.TRUE);
      } catch (Exception e) {
        throw new IllegalStateException("register driver failed: " + className + "@" + jarPath, e);
      }
    }
  }

  private static final class DriverProxy implements Driver {
    private final Driver delegate;

    private DriverProxy(Driver delegate) {
      this.delegate = delegate;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
      return delegate.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
      return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
      return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
      return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
      return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
      return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
      return delegate.getParentLogger();
    }
  }
}
