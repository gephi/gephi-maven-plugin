/*
 * Copyright 2015 Gephi Consortium
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.gephi.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.maven.plugin.logging.Log;

/**
 * Resolves common, free-text license names (as entered via the 'licenseName'
 * configuration) to a SPDX license identifier, and downloads the
 * corresponding license text from the
 * <a href="https://github.com/spdx/license-list-data">SPDX license-list-data</a>
 * repository.
 */
public class LicenseUtils {

    private static final String LICENSE_TEXT_URL_TEMPLATE
            = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/%s.txt";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    /**
     * Maps a normalized (lowercase, alphanumeric-only) license name alias to
     * its SPDX license identifier.
     */
    private static final Map<String, String> SPDX_ALIASES = new HashMap<String, String>();

    static {
        putAliases("Apache-2.0", "Apache 2.0", "Apache License 2.0", "Apache License, Version 2.0", "Apache-2.0");
        putAliases("MIT", "MIT", "MIT License", "The MIT License");
        putAliases("BSD-3-Clause", "BSD 3-Clause", "BSD-3-Clause", "New BSD", "New BSD License", "3-Clause BSD License", "BSD 3-Clause License");
        putAliases("BSD-2-Clause", "BSD 2-Clause", "BSD-2-Clause", "Simplified BSD License", "BSD 2-Clause License");
        putAliases("GPL-3.0-only", "GPL v3", "GPL 3.0", "GPL-3.0", "GNU GPL v3", "GPLv3", "GNU General Public License v3.0");
        putAliases("GPL-2.0-only", "GPL v2", "GPL 2.0", "GPL-2.0", "GNU GPL v2", "GPLv2", "GNU General Public License v2.0");
        putAliases("LGPL-3.0-only", "LGPL v3", "LGPL 3.0", "LGPL-3.0", "GNU LGPL v3", "LGPLv3", "GNU Lesser General Public License v3.0");
        putAliases("LGPL-2.1-only", "LGPL v2.1", "LGPL 2.1", "LGPL-2.1", "GNU LGPL v2.1", "LGPLv2.1", "GNU Lesser General Public License v2.1");
        putAliases("MPL-2.0", "MPL 2.0", "MPL-2.0", "Mozilla Public License 2.0");
        putAliases("EPL-2.0", "EPL 2.0", "EPL-2.0", "Eclipse Public License 2.0");
        putAliases("Unlicense", "Unlicense", "The Unlicense");
        putAliases("CC0-1.0", "CC0", "CC0 1.0", "CC0-1.0");
    }

    private static void putAliases(String spdxId, String... names) {
        for (String name : names) {
            SPDX_ALIASES.put(normalize(name), spdxId);
        }
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Resolves a free-text license name to a SPDX license identifier.
     *
     * @param licenseName license name, as configured in 'licenseName'
     * @return SPDX identifier (e.g. 'Apache-2.0'), or null if the license
     * name isn't recognized
     */
    protected static String resolveSpdxId(String licenseName) {
        if (licenseName == null || licenseName.trim().isEmpty()) {
            return null;
        }
        return SPDX_ALIASES.get(normalize(licenseName));
    }

    /**
     * Downloads the license text for the given SPDX identifier from the
     * SPDX license-list-data repository and writes it to the given file.
     * Never throws; any I/O problem (offline, rate-limited, unknown id) is
     * logged at debug level and reported through the return value instead,
     * since this is a best-effort convenience and should never turn into a
     * build failure by itself.
     *
     * @param spdxId SPDX license identifier (e.g. 'Apache-2.0')
     * @param destFile destination file the license text is written to
     * @param log log
     * @return true if the license text was successfully downloaded and
     * written to destFile
     */
    protected static boolean downloadLicenseText(String spdxId, File destFile, Log log) {
        String url = String.format(LICENSE_TEXT_URL_TEMPLATE, spdxId);
        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "gephi-maven-plugin");
            InputStream in = connection.getInputStream();
            try {
                Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                in.close();
            }
            return true;
        } catch (IOException ex) {
            log.debug("Could not download license text for '" + spdxId + "' from '" + url + "'", ex);
            return false;
        }
    }
}
