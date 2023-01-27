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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

public class ModuleUtils {

    /**
     * Investigate modules dependencies and return a map where keys are
     * top-level modules and values are all modules that it depends on plus the
     * key module.
     *
     * @param modules list of modules
     * @param log log
     * @return map that represents the tree of modules
     */
    protected static Map<MavenProject, List<MavenProject>> getModulesTree(List<MavenProject> modules, Log log) {
        Map<MavenProject, List<MavenProject>> result = new LinkedHashMap<MavenProject, List<MavenProject>>();
        for (MavenProject proj : modules) {
            List<Dependency> dependencies = proj.getDependencies();
            log.debug("Investigating the " + dependencies.size() + " dependencies of project '" + proj.getName() + "'");

            // Init
            List<MavenProject> deps = new ArrayList<MavenProject>();
            deps.add(proj);
            result.put(proj, deps);

            // Add all module-based dependencies
            for (Dependency d : dependencies) {
                for (MavenProject projDependency : modules) {
                    if (projDependency != proj
                            && projDependency.getArtifactId().equals(d.getArtifactId())
                            && projDependency.getGroupId().equals(d.getGroupId())
                            && projDependency.getVersion().equals(d.getVersion())) {
                        log.debug("Found a dependency that matches another module '" + proj.getName() + "' -> '" + projDependency.getName() + "'");
                        deps.add(projDependency);
                    }
                }
            }
        }

        // Remove modules that are entirely dependencies of others
        List<MavenProject> toBeRemoved = new ArrayList<MavenProject>();
        for (MavenProject proj : modules) {
            List<MavenProject> projDeps = result.get(proj);
            for (MavenProject proj2 : modules) {
                if (proj != proj2) {
                    if (result.get(proj2).containsAll(projDeps)) {
                        log.debug("Remove '" + proj.getName() + "' from list of top modules because is a dependency of '" + proj2.getName() + "'");
                        toBeRemoved.add(proj);
                        break;
                    }
                }
            }
        }
        for (MavenProject mp : toBeRemoved) {
            result.remove(mp);
        }

        return result;
    }

    protected static String getModuleDownloadPath(MavenProject topPlugin, List<MavenProject> modules, File directory, Log log) throws MojoExecutionException {
        File dest;
        if (modules.size() > 1) {
            dest = new File(directory, topPlugin.getArtifactId() + "-" + topPlugin.getVersion() + ".zip");
            log.debug("The plugin '" + topPlugin.getName() + "' is a suite, creating zip archive at '" + dest.getAbsolutePath() + "'");

            // Verify files exist and add to archive
            try {
                ZipArchiver archiver = new ZipArchiver();
                for (MavenProject module : modules) {
                    File folder = new File(module.getBasedir(), "target");
                    File f = new File(folder, module.getArtifactId() + "-" + module.getVersion() + ".nbm");
                    if (!f.exists()) {
                        throw new MojoExecutionException("The NBM file '" + f.getAbsolutePath() + "' can't be found");
                    }
                    archiver.addFile(f, f.getName());
                    log.debug("  Add file '" + f.getAbsolutePath() + "' to the archive");
                }
                archiver.setCompress(false);
                archiver.setDestFile(dest);
                archiver.setForced(true);
                archiver.createArchive();
                log.info("Created ZIP archive for project '" + topPlugin.getName() + "' at '" + dest.getAbsolutePath() + "'");
            } catch (IOException ex) {
                throw new MojoExecutionException("Something went wrong with the creation of the ZIP archive for project '" + topPlugin.getName() + "'", ex);
            } catch (ArchiverException ex) {
                throw new MojoExecutionException("Something went wrong with the creation of the ZIP archive for project '" + topPlugin.getName() + "'", ex);
            }
        } else {
            dest = new File(directory, topPlugin.getArtifactId() + "-" + topPlugin.getVersion() + ".nbm");
            log.debug("The plugin is not a suite, return nbm file '" + dest.getAbsolutePath() + "'");
        }
        return dest.getName();
    }
}
