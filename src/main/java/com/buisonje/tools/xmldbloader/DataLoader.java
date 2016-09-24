/*
 * © 2016 Volkert de Buisonjé, released under the Apache License 2.0. License terms: https://www.apache.org/licenses/LICENSE-2.0
 */
package com.buisonje.tools.xmldbloader;

import java.util.List;
import java.util.Properties;

/**
 * Interface for various data loaders.
 *
 * @author Volkert de Buisonj&eacute;
 * @since 23-09-2016
 */
public interface DataLoader {

    /**
     * Loads data from input files and processes them.
     *
     * @param params <b>optional<b></b> parameters that may adjust the way the input files are processed.
     * @param properties the {@link Properties} <b>required</b> for this method to do its work.
     * @param inputFilePaths the paths to the input files that are to be loaded and processed.
     */
    void loadDataFiles(List<String> params, Properties properties, List<String> inputFilePaths);

}
