/*
 * © 2016 Volkert de Buisonjé, released under the Apache License 2.0. License terms: https://www.apache.org/licenses/LICENSE-2.0
 */
package com.buisonje.tools.xmldbloader;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.kfu.www.nsayer.DriverShim;

/**
 * Enables the loading of XML DataSets (as supported by {@link FlatXmlDataFileLoader}) directly into databases using the JDBC interface.
 *
 * @author Volkert de Buisonj&eacute;
 */
public class XmlDataSetDBLoader implements DataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlDataSetDBLoader.class);

    private static final String ARG_ALLOW_EMPTY_FIELDS = "allowemptyfields";
    private static final String ARG_JDBC_DRIVER_JAR_FILE_PATH = "jdbcdriverclassfile=";
    private static final String ARG_DB_URL = "dburl=";
    private static final String ARG_DB_USERNAME = "dbusername=";
    private static final String ARG_DB_PASSWORD = "dbpassword=";

    private static final String JDBC_SCHEME_PREFIX = "jdbc:";
    private static final String DATASET_XML_ROOT_ELEMENT_NAME = "dataset";

    /**
     * {@inheritDoc}
     * <p>
     * This implementation expects XML DataSet files (as supported by {@link FlatXmlDataFileLoader}) as input files and loads them into a database.
     * </p>
     */
    @Override
    public void loadDataFiles(List<String> params, Properties properties, List<String> inputFilePaths) {
        if (inputFilePaths.size() < 1) {
            throw new IllegalArgumentException(
                    "You need to specify at least one DataSet XML file (or at least one directory containing at least one DataSet XML file) to load into the"
                            + " database."
            );
        }

        IDatabaseConnection dbConnection = null;
        try {

            dbConnection = processParams(params, properties);
            processInputFilePaths(inputFilePaths, dbConnection);

        }
        catch (Exception e) {
            throw new IllegalStateException("An error occurred while trying to load one of the specified DataSet XML files into the database.", e);
        } finally {
            closeDBConnectionQuietly(dbConnection);
        }
    }

    /**
     * @param dbConnection the {@link IDatabaseConnection} to close.
     * @deprecated <b>TODO</b>: Upgrade this project beyond Java 6 and then replace this entire finally-block with a solution based on try-with-resources.
     */
    @Deprecated
    void closeDBConnectionQuietly(IDatabaseConnection dbConnection) {
        // TODO:
        try {
            if (dbConnection != null && dbConnection.getConnection() != null  && !dbConnection.getConnection().isClosed()) {
                dbConnection.close();
                dbConnection = null;
            }
        } catch (SQLException e) {
            LOGGER.error(String.format("Could not clean up %s after failure.", dbConnection.getClass()), e);
        }
    }

    private void processInputFilePaths(final List<String> inputFilePaths, IDatabaseConnection connection) throws DataSetException {
        DataFileLoader loader = new FlatXmlDataFileLoader();
        for (final String currentInputFilePath : inputFilePaths) {
            loadDataSetXMLFiles(connection, loader, new File(currentInputFilePath));
        }
    }

    private void loadDataSetXMLFiles(IDatabaseConnection connection, DataFileLoader loader, File currentFileOrDir) throws DataSetException {
        final String currentFileOrDirPath = currentFileOrDir.getAbsolutePath();
        if (!currentFileOrDir.exists() || !currentFileOrDir.canRead()) {
            throw new IllegalArgumentException(String.format("Specified path \"%s\" does not exist or is not accessible.", currentFileOrDirPath));
        }
        if (currentFileOrDir.isDirectory()) {
            FileFilter fileFilter = new WildcardFileFilter("*.xml", IOCase.INSENSITIVE);
            File[] files = currentFileOrDir.listFiles(fileFilter);
            if (files == null) {
                LOGGER.error("Skipping invalid directory \"{}\".", currentFileOrDirPath);
                return;
            }
            if (files.length < 1) {
                LOGGER.warn("Directory \"{}\" contains no XML files. Skipping this directory.", currentFileOrDirPath);
                return;
            }
            for (final File xmlFile : files) {
                if (xmlFile.isDirectory()) {
                    LOGGER.warn(
                            "Specified directory \"{}\" contains a subdirectory called \"{}\". Will *not* recurse.",
                            currentFileOrDir.getAbsolutePath(),
                            xmlFile.getAbsolutePath());
                } else {
                    loadDataSetXMLFile(connection, loader, xmlFile);
                }
            }
        } else {
            loadDataSetXMLFile(connection, loader, currentFileOrDir);
        }

    }

    private void loadDataSetXMLFile(IDatabaseConnection connection, DataFileLoader loader, File currentInputFile) throws DataSetException {
        final String currentInputFilePath = currentInputFile.getAbsolutePath();
        try {
            if (!isValidDataSetXML(currentInputFile)) {
                LOGGER.warn("Skipping unsupported XML file {}.", currentInputFilePath);
                return;
            }
            LOGGER.info("Loading DataSet XML file {}...", currentInputFilePath);
            DatabaseOperation.CLEAN_INSERT.execute(connection, loader.loadDataSet(currentInputFile.toURI().toURL()));
        } catch (Exception e) {
            throw new DataSetException(String.format("Error while trying to load DataSet XML file \"%s\" into the database.", currentInputFilePath), e);
        }
    }

    private boolean isValidDataSetXML(File currentInputFile) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document document = dBuilder.parse(currentInputFile);
        if (!DATASET_XML_ROOT_ELEMENT_NAME.equalsIgnoreCase(document.getDocumentElement().getTagName())) {
            LOGGER.error("The file \"{}\" is not a valid XML DataSet, since it has a root element other than \"<{}>\".",
                    currentInputFile.getAbsolutePath(),
                    DATASET_XML_ROOT_ELEMENT_NAME);
            return false;
        }
        return true;
    }

    IDatabaseConnection processParams(List<String> params, Properties properties) throws SQLException, DatabaseUnitException {

        boolean allowEmptyFields = false;
        final String allowEmptyFieldsProperty = getRequiredProperty(properties, "allowEmptyFields");
        if ("true".equalsIgnoreCase(allowEmptyFieldsProperty) || "yes".equalsIgnoreCase(allowEmptyFieldsProperty)) {
            LOGGER.info("Setting allowEmptyFields to \"true\" as configured in {}.", properties);
            allowEmptyFields = true;
        }

        String dbURL = getRequiredProperty(properties, "jdbc.url");
        String dbUsername = getRequiredProperty(properties, "jdbc.username");
        String dbPassword = getRequiredProperty(properties, "jdbc.password");


        boolean dbJarAlreadyLoaded = false;

        for (String param : params) {
            param = param.replaceAll("--", "");
            if (ARG_ALLOW_EMPTY_FIELDS.equalsIgnoreCase(param)) {
                LOGGER.info("Argument \"{}\" was specified. Empty fields will be allowed in the specified source files.", ARG_ALLOW_EMPTY_FIELDS);
                allowEmptyFields = true;
                continue;
            }

            final String currentParamValue = param.substring(param.indexOf('=') + 1);

            if (param.toLowerCase().startsWith(ARG_JDBC_DRIVER_JAR_FILE_PATH)) {
                LOGGER.info("Loading specified JDBC driver class file \"{}\"...", currentParamValue);
                loadJdbcDriverClassFromJar(currentParamValue);
                dbJarAlreadyLoaded = true;
                continue;
            }

            if (param.toLowerCase().startsWith(ARG_DB_URL)) {
                dbURL = currentParamValue;
                LOGGER.info("Specified target database URL: \"{}\"", dbURL);
                continue;
            }
            if (param.toLowerCase().startsWith(ARG_DB_USERNAME)) {
                dbUsername = currentParamValue;
                LOGGER.info("Specified target database username: \"{}\"", dbUsername);
                continue;
            }
            if (param.toLowerCase().startsWith(ARG_DB_PASSWORD)) {
                dbPassword = currentParamValue;
                LOGGER.info("Specified target database password: \"{}\"", dbPassword);
                continue;
            }

            throw new IllegalArgumentException(
                    "Unsupported parameter \"%s\" specified. If you are trying to specify an XML file or a directory with a name that happens to start with a"
                            + " dash or a hyphen, try either specifying its absolute path, or prefixing it with \"." + File.separatorChar + "\"."
            );

        }

        final String dbJarPath = properties.getProperty("jdbc.jarpath");
        if (!dbJarAlreadyLoaded && dbJarPath != null) {
            LOGGER.info("Loading JDBC Driver JAR from location \"{}\" as specified in {}...", dbJarPath, properties);
            loadJdbcDriverClassFromJar(dbJarPath);
        }

        if (!dbURL.toLowerCase().startsWith(JDBC_SCHEME_PREFIX)) {
            dbURL = JDBC_SCHEME_PREFIX + dbURL;
        }

        Connection jdbcConnection = DriverManager.getConnection(dbURL, dbUsername, dbPassword);
        IDatabaseConnection dbConnection = new DatabaseConnection(jdbcConnection);

        if (allowEmptyFields) {
            dbConnection.getConfig().setProperty(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, true);
        }

        return dbConnection;
    }

    private String getRequiredProperty(Properties properties, String property) {
        final String propValue = properties.getProperty(property);
        if (propValue == null || propValue.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Required property \"%s\" empty or missing in properties file \"%s\".", property, properties)
            );
        }
        return propValue;
    }

    /**
     * Instructs the {@link System}'s {@link ClassLoader} to load a JDBC driver class file from an external JAR.
     * @param jarFilePath the path to the class file to be loaded.
     * @throws IllegalArgumentException if no class could successfully loaded from the specified path.
     */
    private void loadJdbcDriverClassFromJar(final String jarFilePath) throws IllegalArgumentException {

        try {

            URL u = new URL("jar:file:///" + jarFilePath + "!/");
            URLClassLoader ucl = new URLClassLoader(new URL[] { u });

            Reflections reflections = new Reflections(ucl);

            final Set<Class<? extends Driver>> detectedJdbcDrivers = reflections.getSubTypesOf(Driver.class);

            if (detectedJdbcDrivers.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format(
                                "The supplied JAR file at \"%s\" contains no JDBC drivers (which should implement the interface \"%s\").",
                                jarFilePath,
                                Driver.class.getName()
                        )
                );
            }

            final int numberOfDetectedJdbcDrivers = detectedJdbcDrivers.size();
            if (numberOfDetectedJdbcDrivers > 1) {
                LOGGER.warn(
                        "Detected more than one ({}) JDBC drivers in the supplied JAR file at \"{}\". Choosing the first one...",
                        numberOfDetectedJdbcDrivers,
                        jarFilePath
                );
            }

            Driver driver = detectedJdbcDrivers.iterator().next().newInstance();
            LOGGER.info("Loaded JDBC driver \"{}\".", driver.getClass().getName());
            DriverManager.registerDriver(new DriverShim(driver));

        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("JAR file \"%s\" apparently contains a JDBC Driver that is incompatible with the Java version "
                    + "on which the application is currently running (version %s). To solve this problem, either upgrade the Java version (recommended) or "
                    + "downgrade the JDBC driver.", jarFilePath, System.getProperty("java.version")), e);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to load JDBC Driver class from JAR \"%s\".", jarFilePath), e);
        }
    }

}
