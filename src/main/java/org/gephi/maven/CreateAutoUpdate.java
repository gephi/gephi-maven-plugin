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
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
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

    /**
     * Metadata url.
     */
    @Parameter(required = true)
    protected String metadataUrl;

    @Parameter(defaultValue = "false")
    protected Boolean skipUnchangedVersions;

    private void downloadLatestNbm(String version, File destinationFile) throws MojoExecutionException {
        //Download previous file
        try {
            URL url = new URL(metadataUrl + version + "/" + destinationFile.getName());
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.connect();
            InputStream stream = connection.getInputStream();
            ReadableByteChannel rbc = Channels.newChannel(stream);
            FileOutputStream fos = new FileOutputStream(destinationFile);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            rbc.close();
            stream.close();
            getLog().info("Downloaded plugin file to '" + destinationFile.getAbsolutePath() + "'");
        } catch (Exception e) {
            throw new MojoExecutionException("Error while downloading previous 'plugins.json'", e);
        }
    }

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
                    if (skipUnchangedVersions && skipPlugin) {
                        getLog().info("The plugin '"+proj.getName()+"' will be downloaded " +
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
                                        if (skipUnchangedVersions && skipPlugin) {
                                            skippedFiles.add(new File(outputFolder, nbmFile.getName()));
                                        } else {
                                            FileUtils.copyFileToDirectory(nbmFile, outputFolder);
                                            getLog().info("Copying  '" + nbmFile + "' to '" +
                                                outputFolder.getAbsolutePath() + "'");
                                        }
                                    } catch (IOException ex) {
                                        getLog().error("Error while copying nbm file '" +
                                            nbmFile.getAbsolutePath() + "'", ex);
                                    }
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

            // Download last version of skipped plugins
            if (skipUnchangedVersions) {
                for (File skippedFile : skippedFiles) {
                    downloadLatestNbm(gephiMinorVersion, skippedFile);
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
        } else {
            throw new MojoExecutionException("This should be executed on the reactor project");
        }
    }
}
