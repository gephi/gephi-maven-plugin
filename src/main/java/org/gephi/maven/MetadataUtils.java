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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gephi.maven.json.Author;

/**
 * Metadata utils.
 */
public class MetadataUtils {

    /**
     * Lookup and returns the value of the <em>licenseName</em> configuration.
     *
     * @param project project
     * @return license name or null if not found
     */
    protected static String getLicenseName(MavenProject project) {
        Plugin nbmPlugin = lookupNbmPlugin(project);
        if (nbmPlugin != null) {
            Xpp3Dom config = (Xpp3Dom) nbmPlugin.getConfiguration();
            if (config != null && config.getChild("licenseName") != null) {
                return config.getChild("licenseName").getValue();
            }
        }
        return null;
    }

    /**
     * Lookup and returns the value of the <em>homePageUrl</em> configuration.
     *
     * @param project project
     * @return homepage or null if not found
     */
    protected static String getHomepage(MavenProject project) {
        Plugin nbmPlugin = lookupNbmPlugin(project);
        if (nbmPlugin != null) {
            Xpp3Dom config = (Xpp3Dom) nbmPlugin.getConfiguration();
            if (config != null && config.getChild("homePageUrl") != null) {
                return config.getChild("homePageUrl").getValue();
            }
        }
        return null;
    }

    /**
     * Lookup and returns the value of the <em>author</em> configuration.
     * <p>
     * The configuration string is split based on ',' so multiple authors can be
     * defined.
     *
     * @param project project
     * @return list of authors or null if not found
     */
    protected static List<Author> getAuthors(MavenProject project) {
        Plugin nbmPlugin = lookupNbmPlugin(project);
        if (nbmPlugin != null) {
            Xpp3Dom config = (Xpp3Dom) nbmPlugin.getConfiguration();
            if (config != null && config.getChild("author") != null) {
                String authorName = config.getChild("author").getValue();
                String authorEmail = config.getChild("authorEmail") != null ? config.getChild("authorEmail").getValue() : null;
                String authorUrl = config.getChild("authorUrl") != null ? config.getChild("authorUrl").getValue() : null;
                Author author = new Author();
                author.name = authorName;
                author.email = authorEmail;
                author.link = authorUrl;

                return Arrays.asList(new Author[]{author});
            }
        }
        return null;
    }

    /**
     * Lookup and return the NBM plugin for this plugin.
     *
     * @param project project
     * @return NBM plugin
     */
    protected static Plugin lookupNbmPlugin(MavenProject project) {
        List plugins = project.getBuildPlugins();

        for (Iterator iterator = plugins.iterator(); iterator.hasNext();) {
            Plugin plugin = (Plugin) iterator.next();
            if ("org.codehaus.mojo:nbm-maven-plugin".equalsIgnoreCase(plugin.getKey())) {
                return plugin;
            }
        }
        return null;
    }

    /**
     * Lookup and return the content of the README.md file for this plugin.
     *
     * @param project project
     * @param log log
     * @return content of REDME.md file or null if not found
     */
    protected static String getReadme(MavenProject project, Log log) {
        File readmePath = new File(project.getBasedir(), "README.md");
        if (readmePath.exists()) {
            log.debug("README.md file has been found: '" + readmePath.getAbsolutePath() + "'");
            try {
                StringBuilder builder = new StringBuilder();
                LineNumberReader fileReader = new LineNumberReader(new FileReader(readmePath));
                String line;
                while ((line = fileReader.readLine()) != null) {
                    builder.append(line);
                    builder.append("\n");
                }
                log.info("File README.md with " + builder.length() + " characters has been attached to project '" + project.getName() + "'");
                return builder.toString();
            } catch (IOException ex) {
                log.error("Error while reading README.md file", ex);
            }
        }
        return null;
    }

    /**
     * Lookup source code configuration or default to SCM.
     *
     * @param project project
     * @param log log
     * @return source code url or null
     */
    protected static String getSourceCode(MavenProject project, Log log) {
        Plugin nbmPlugin = lookupNbmPlugin(project);
        if (nbmPlugin != null) {
            Xpp3Dom config = (Xpp3Dom) nbmPlugin.getConfiguration();
            if (config != null && config.getChild("sourceCodeUrl") != null) {
                return config.getChild("sourceCodeUrl").getValue();
            }
        }

        Scm scm = project.getScm();
        if (scm != null && scm.getUrl() != null && !scm.getUrl().isEmpty()) {
            log.debug("SCM configuration found, with url = '" + scm.getUrl() + "'");
            return scm.getUrl();
        } else {

        }
        return null;
    }

    /**
     * Use local Git repository configuration to lookup the sourcecode URL.
     *
     * @param project project
     * @param log log
     * @return source code url or null
     */
    protected static String getSourceCodeUrlFromGit(MavenProject project, Log log) {
        File gitPath = new File(project.getBasedir(), ".git");
        if (gitPath.exists() && gitPath.isDirectory()) {
            File gitPathConfig = new File(gitPath, "config");
            if (gitPathConfig.exists() && gitPathConfig.isFile()) {
                log.debug("Git config gile located at '" + gitPathConfig.getAbsolutePath() + "'");
                try {
                    BufferedReader fileReader = new BufferedReader(new FileReader(gitPathConfig));

                    Pattern pattern = Pattern.compile("\\s*url = (.*)");
                    String line, url = null;
                    while ((line = fileReader.readLine()) != null) {
                        Matcher m = pattern.matcher(line);
                        if (m.matches()) {
                            url = m.group(1);
                            break;
                        }
                    }
                    fileReader.close();

                    if (url != null) {
                        log.debug("URL found in .git/config: '" + url + "'");
                        if (url.startsWith("http://")) {
                            return url;
                        } else if (url.startsWith("git@")) {
                            Pattern gitPattern = Pattern.compile("git@([^:]*):([^.]*).git");
                            Matcher gitMatcher = gitPattern.matcher(url);
                            if (gitMatcher.matches()) {
                                String res = "http://" + gitMatcher.group(1) + "/" + gitMatcher.group(2);
                                log.debug("Rewrote URL to '" + res + "'");
                                return res;
                            }
                        } else {
                            log.debug("Couldn't find a pattern in the git URL: " + url);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error while reading Git config", e);
                }
            }
        } else {
            log.debug("The .git folder couldn't be found at '" + gitPath.getAbsolutePath() + "'");
        }
        return null;
    }
}
