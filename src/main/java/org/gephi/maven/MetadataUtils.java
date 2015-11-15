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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

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
     * Lookup and returns the value of the <em>author</em> configuration.
     * <p>
     * The configuration string is split based on ',' so multiple authors can be
     * defined.
     *
     * @param project project
     * @return list of authors or null if not found
     */
    protected static List<String> getAuthors(MavenProject project) {
        Plugin nbmPlugin = lookupNbmPlugin(project);
        if (nbmPlugin != null) {
            Xpp3Dom config = (Xpp3Dom) nbmPlugin.getConfiguration();
            if (config != null && config.getChild("author") != null) {
                String authors = config.getChild("author").getValue();
                List<String> res = new ArrayList<String>();
                for (String a : authors.split(",")) {
                    res.add(a.trim());
                }
                return res;
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
}
