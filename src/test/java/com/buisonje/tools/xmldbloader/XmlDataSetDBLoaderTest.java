package com.buisonje.tools.xmldbloader;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.dbunit.database.IDatabaseConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibatis.common.jdbc.ScriptRunner;

/**
 * Test for {@link XmlDataSetDBLoader}.
 */
public class XmlDataSetDBLoaderTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlDataSetDBLoaderTest.class);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private XmlDataSetDBLoader cut = new XmlDataSetDBLoader();

    private Properties testProperties = loadTestProperties();

    // We need to keep at least one connection open to the shared-cache in-memory database during testing, to prevent it from being destroyed prematurely.
    private IDatabaseConnection dbConnection = null;

    private Properties loadTestProperties() {
        final File propertiesTestFile = new File("target/test-classes/xmldbloader-test.properties");
        assertTrue(propertiesTestFile.exists());
        final Properties testProperties = new Properties();
        try {
            testProperties.load(new FileInputStream(propertiesTestFile));
        } catch (IOException e) {
            throw new IllegalStateException("Error while trying to load test properties.", e);
        }
        return testProperties;
    }

    @Before
    public void createEmptyTestDB() throws Exception {

        // final File tempDBFile = folder.newFile("chinook_empty.db");
        // tempDBFile.deleteOnExit();
        // testProperties.put("jdbc.url", "jdbc:sqlite:" + tempDBFile.getAbsolutePath());

        dbConnection = cut.processParams(Collections.<String>emptyList(), testProperties);

        Connection connection = dbConnection.getConnection();

        // printTablesInDataBase(connection);

        ScriptRunner scriptRunner = new ScriptRunner(connection, false, true);

        final File testSchemaSQLFile = new File("src/test/resources/chinook_structure.sql");
        assertTrue(testSchemaSQLFile.exists());

        BufferedReader sqlReader = new BufferedReader(new FileReader(testSchemaSQLFile));

        scriptRunner.runScript(sqlReader);

    }

    /**
     * Helper method for debugging purposes.
     * @param connection
     * @throws SQLException
     * @see <a href="https://coderwall.com/p/609ppa/printing-the-result-of-resultset">https://coderwall.com/p/609ppa/printing-the-result-of-resultset</a>
     */
    private void printTablesInDataBase(Connection connection) throws SQLException {

        final Statement statement = connection.createStatement();
        statement.execute("SELECT * FROM sqlite_master WHERE type='table';");
        final ResultSet resultSet = statement.getResultSet();
        ResultSetMetaData rsmd = resultSet.getMetaData();

        // Test code taken from https://coderwall.com/p/609ppa/printing-the-result-of-resultset
        System.out.println("Showing tables in in-memory database...");
        int columnsNumber = rsmd.getColumnCount();
        while (resultSet.next()) {
            for (int i = 1; i <= columnsNumber; i++) {
                if (i > 1) System.out.print(",  ");
                String columnValue = resultSet.getString(i);
                System.out.print(columnValue + " " + rsmd.getColumnName(i));
            }
            System.out.println("");
        }
        System.out.println("Done showing tables in in-memory database.");
    }

    @After
    public void disconnectFromInMemoryDatabase() throws Exception {
        cut.closeDBConnectionQuietly(dbConnection);
    }

    /**
     * Tests {@link XmlDataSetDBLoader#loadDataFiles(List, Properties, List)}.
     */
    @Test
    public void testLoadDataFiles() throws Exception {
        cut.loadDataFiles(
                Collections.<String>emptyList(),
                testProperties,
                Collections.singletonList(new File("src/test/resources/chinook-dataset.xml").getAbsolutePath())
        );
    }
}
