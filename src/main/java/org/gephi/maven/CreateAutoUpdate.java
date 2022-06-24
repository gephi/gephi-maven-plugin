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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.netbeans.nbm.utils.AbstractNetbeansMojo;
import org.apache.tools.ant.types.FileSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.gzip.GZipArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.netbeans.nbbuild.MakeUpdateDesc;

/**
 * Fork of the NBM's autoupdate task that handles needed packaging.
 */
@Mojo(name = "create-autoupdate", aggregator = true, defaultPhase = LifecyclePhase.PACKAGE)
public class CreateAutoUpdate extends AbstractNetbeansMojo {

    /**
     * Output directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}")
    protected File outputDirectory;

    /**
     * The Maven project.
     */
    @Parameter(required = true, readonly = true, property = "project")
    private MavenProject project;

    /**
     * If the executed project is a reactor project, this will contains the full
     * list of projects in the reactor.
     */
    @Parameter(required = true, readonly = true, property = "reactorProjects")
    private List<MavenProject> reactorProjects;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String gephiVersion = (String) project.getProperties().get("gephi.version");
        if (gephiVersion == null) {
            throw new MojoExecutionException("The 'gephi.version' property should be defined");
        }
        String gephiMinorVersion = MetadataUtils.getMinorVersion(gephiVersion);

        File outputFolder = new File(outputDirectory, gephiMinorVersion);
        if (outputFolder.mkdirs()) {
            getLog().debug("Folder '" + outputFolder.getAbsolutePath() + "' created.");
        }

        Set<File> skippedFiles = new HashSet<>();

        if (reactorProjects != null && reactorProjects.size() > 0) {
            Project antProject = registerNbmAntTasks();

            for (MavenProject proj : reactorProjects) {
                if (proj.getPackaging().equals("nbm")) {
                    // Property set by BuildMetadata based on the latest plugins.json
                    boolean skipPlugin = proj.getProperties().getProperty("skipPlugin", "false").equals("true");
                    if (skipPlugin) {
                        getLog().info("The plugin '"+proj.getName()+"' will be skipped " +
                            "because its version hasn't changed");
                    }
                    File moduleDir = proj.getFile().getParentFile();
                    if (moduleDir != null && moduleDir.exists()) {
                        File targetDir = new File(moduleDir, "target");
                        if (targetDir.exists()) {
                            String gephiVersionModule =
                                proj.getProperties().getProperty("gephi.version");
                            if (gephiVersionModule == null) {
                                throw new MojoExecutionException(
                                    "The 'gephi.version' property should be defined in project '" +
                                        proj.getName() + "'");
                            }
                            String gephiMinorVersionModule =
                                MetadataUtils.getMinorVersion(gephiVersionModule);
                            if (gephiMinorVersionModule.equals(gephiMinorVersion)) {
                                File[] nbmsFiles = targetDir.listFiles(new FilenameFilter() {
                                    @Override
                                    public boolean accept(File dir, String name) {
                                        return !name.startsWith(".") && name.endsWith(".nbm");
                                    }
                                });
                                for (File nbmFile : nbmsFiles) {
                                    try {
                                        FileUtils.copyFileToDirectory(nbmFile, outputFolder);
                                        if (skipPlugin) {
                                            skippedFiles.add(new File(outputFolder, nbmFile.getName()));
                                        }
                                    } catch (IOException ex) {
                                        getLog().error("Error while copying nbm file '" +
                                            nbmFile.getAbsolutePath() + "'", ex);
                                    }
                                    getLog().info("Copying  '" + nbmFile + "' to '" +
                                        outputFolder.getAbsolutePath() + "'");
                                }
                            } else {
                                getLog().warn("The NBM of module '" + proj.getName() +
                                    "' has been ignored because its Gephi Version is '" +
                                    gephiMinorVersionModule + "' while '" + gephiMinorVersion +
                                    "' is expected");
                            }
                        } else {
                            getLog().error(
                                "The module target dir for project '" + proj.getName() +
                                    "' doesn't exists");
                        }
                    } else {
                        getLog().error("The module dir for project '" + proj.getName() +
                            "' doesn't exists");
                    }
                }
            }

            // Create updates.xml
            String fileName = "updates.xml";
            MakeUpdateDesc descTask = (MakeUpdateDesc) antProject.createTask("updatedist");
            File xmlFile = new File(outputFolder, fileName);
            descTask.setDesc(xmlFile);
            FileSet fs = new FileSet();
            fs.setDir(outputFolder);
            fs.createInclude().setName("**/*.nbm");
            descTask.addFileset(fs);
            try {
                descTask.execute();
            } catch (BuildException ex) {
                throw new MojoExecutionException("Cannot create autoupdate site xml file", ex);
            }
            getLog().info("Generated autoupdate site content at " + outputFolder.getAbsolutePath());

            // Create compressed version of updates.xml
            try {
                GZipArchiver gz = new GZipArchiver();
                gz.addFile(xmlFile, fileName);
                File gzipped = new File(outputFolder, fileName + ".gz");
                gz.setDestFile(gzipped);
                gz.createArchive();
            } catch (ArchiverException ex) {
                throw new MojoExecutionException("Cannot create gzipped version of the update site xml file.", ex);
            } catch (IOException ex) {
                throw new MojoExecutionException("Cannot create gzipped version of the update site xml file.", ex);
            }
            getLog().info("Generated compressed autoupdate site content at " + outputFolder.getAbsolutePath());

            // Remove skipped NBM files
            for (File file : skippedFiles) {
                if (!file.delete()) {
                    getLog().warn("The skipped plugin file '"+file.getName()+"' couldn't be deleted");
                } else {
                    getLog().info("Skipped file '"+file.getName()+"' deleted");
                }
            }
        } else {
            throw new MojoExecutionException("This should be executed on the reactor project");
        }
    }
}
