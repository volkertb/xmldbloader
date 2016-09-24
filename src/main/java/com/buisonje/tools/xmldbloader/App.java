/*
 * © 2016 Volkert de Buisonjé, released under the Apache License 2.0. License terms: https://www.apache.org/licenses/LICENSE-2.0
 */
package com.buisonje.tools.xmldbloader;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.google.common.base.Charsets;

/**
 * Application entry point.
 *
 * @author Volkert de Buisonj&eacute;
 * @since 23-09-2016
 */
public class App {

    private static final String PROPERTIES_FILE_NAME = "xmldbloader.properties";
    private static final Properties APPLICATION_PROPERTIES = loadProperties(PROPERTIES_FILE_NAME);

    private static final String NEW_LINE = System.getProperty("line.separator");

    private static Properties loadProperties(String propertiesFileName) {
        final Properties props = new Properties();
        try {
            props.load(new FileInputStream(System.getProperty("user.dir") + File.separatorChar + propertiesFileName));
            return props;
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format(
                            "This application requires a properties file called \"%s\" to be present in the same folder.",
                            PROPERTIES_FILE_NAME),
                    e);
        }
    }

    /**
     * Loads one or more XML DataSets (as supported by {@link org.dbunit.util.fileloader.FlatXmlDataFileLoader}) into a database using the JDBC interface.
     *
     * Requires a JDBC driver in the form of an external JAR file as well as a properly configured {@value PROPERTIES_FILE_NAME} file in order to work.
     * The {@value PROPERTIES_FILE_NAME} file needs to be located in the same directory from which the application is started. The location of the JDBC driver
     * JAR needs to be defined in the {@value PROPERTIES_FILE_NAME}.
     *
     * @param args any combination of supported parameters, XML files and/or directories containing XML files. At least one argument with either an XML file
     *             or a directory is required. See {@link XmlDataSetDBLoader#processParams(java.util.List, java.util.Properties)} for the supported arguments
     *             and their meanings.
     *
     * @throws Exception in case anything goes wrong. <b>TODO</b>: display more user-friendly error messages instead of logging stacktraces to the console.
     */
    public static void main(final String[] args) throws Exception {

        printWelcomeMessage();

        final List<String> params = new ArrayList<String>();
        final List<String> inputFilePaths = new ArrayList<String>();

        separateArguments(args, params, inputFilePaths);

        new XmlDataSetDBLoader().loadDataFiles(params, APPLICATION_PROPERTIES, inputFilePaths);
    }

    /**
     * Platform-independent and encoding-neutral console output.
     * TODO: Extract this into a separate reusable class.
     * @param format see {@link PrintStream#printf(String, Object...)}
     * @param args see {@link PrintStream#printf(String, Object...)}
     * @see System#out
     * @see PrintStream
     * @see Console
     */
    private static PrintWriter printf(String format, Object ... args) {

        PrintWriter writer;

        Console console = System.console();
        if (console != null) {
            // With thanks to https://stackoverflow.com/a/4747502
            writer = console.writer();
        } else {
            // Either we're running Git Bash or Cygterm under Windows, or we're on some kind of non-Windows system. Either way, the encoding would be UTF-8.

            try {
                // With thanks to https://stackoverflow.com/a/20387039
                writer = new PrintWriter(
                        new OutputStreamWriter(System.out, Charsets.UTF_8),
                        true);
            } catch (UnsupportedCharsetException e) {
                throw new IllegalStateException("Seriously, what kind of system doesn't even support UTF-8???", e);
            }
        }

        writer.printf(format, args);
        return writer;
    }

    private static void printWelcomeMessage () {
        printf(NEW_LINE)
                .printf(
                    "xmldbloader version %s, (C) 2016 Volkert de Buisonjé. For source code and license terms, see https://github.com/volkertb/xmldbloader",
                    App.class.getPackage().getImplementationVersion()
                )
                .printf(NEW_LINE)
                .printf(NEW_LINE);
    }

    /**
     * Splits the arguments in two groups: parameters (recognized by a leading '-' prefix) and input paths (which can be individual XML files, directories
     * containing XML files, or any combinations thereof.
     * @param args the arguments to split.
     * @param params the {@link List<String>} in which to expect the parameters to be collected.
     * @param inputFilePaths the {@link List<String>} in which to expect the input paths to be collected.
     */
    private static void separateArguments(String[] args, List<String> params, List<String> inputFilePaths) {
        for (final String arg : args) {
            if (arg.startsWith("-")) {
                params.add(arg);
            } else {
                inputFilePaths.add(arg);
            }
        }
    }
}
