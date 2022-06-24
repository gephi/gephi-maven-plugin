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

import org.gephi.maven.json.PluginsMetadata;
import org.gephi.maven.json.PluginMetadata;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.gephi.maven.json.Version;

/**
 * Builds the plugins metadata.
 */
@Mojo(name = "build-metadata", aggregator = true, defaultPhase = LifecyclePhase.SITE)
public class BuildMetadata extends AbstractMojo {

    /**
     * Output directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/site")
    protected File outputDirectory;

    /**
     * NBM manifest path.
     */
    @Parameter(required = true, defaultValue = "src/main/nbm/manifest.mf")
    private String sourceManifestFile;

    /**
     * If the executed project is a reactor project, this will contains the full
     * list of projects in the reactor.
     */
    @Parameter(required = true, readonly = true, property = "reactorProjects")
    private List<MavenProject> reactorProjects;

    /**
     * The Maven project.
     */
    @Parameter(required = true, readonly = true, property = "project")
    private MavenProject project;

    /**
     * Data format for <em>lastUpdated</em> field.
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMMMM d, yyyy", Locale.ENGLISH);

    /**
     * Metadata url.
     */
    @Parameter(required = true)
    protected String metadataUrl;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String gephiVersion = (String) project.getProperties().get("gephi.version");
        if (gephiVersion == null) {
            throw new MojoExecutionException("The 'gephi.version' property should be defined");
        }
        getLog().debug("Building metadata for 'gephi.version=" + gephiVersion + "'");
        String gephiMinorVersion = MetadataUtils.getMinorVersion(gephiVersion);
        getLog().debug("Gephi minor version is '" + gephiMinorVersion + "'");

        // Dry run when dev version
        boolean dryRun = gephiVersion.endsWith("-SNAPSHOT");
        if (dryRun) {
           getLog().info("Running in dryrun mode because Gephi version contains SNAPSHOT");
        }

        // Create folder
        if (outputDirectory.mkdirs()) {
            getLog().debug("Folder '" + outputDirectory.getAbsolutePath() + "' created.");
        }

        if (reactorProjects != null && reactorProjects.size() > 0) {
            getLog().debug("Found " + reactorProjects.size() + " projects in reactor");
            List<MavenProject> modules = new ArrayList<MavenProject>();
            for (MavenProject proj : reactorProjects) {
                if (proj.getPackaging().equals("nbm")) {
                    String gephiVersionModule = proj.getProperties().getProperty("gephi.version");
                    String gephiMinorVersionModule = MetadataUtils.getMinorVersion(gephiVersionModule);

                    if (gephiMinorVersionModule.equals(gephiMinorVersion)) {
                        getLog().info("Found 'nbm' project '" + proj.getName() + "' with artifactId=" + proj.getArtifactId() + " and groupId=" + proj.getGroupId());
                        modules.add(proj);
                    } else {
                        getLog().warn("Ignored project '" + proj.getName() + "' based on 'gephi.version' value '" + gephiVersionModule + "'");
                    }
                }
            }

            ManifestUtils manifestUtils = new ManifestUtils(sourceManifestFile, getLog());

            // Get all modules with dependencies
            Map<MavenProject, List<MavenProject>> tree = ModuleUtils.getModulesTree(modules, getLog());

            //Download previous file
            File pluginsJsonFile = new File(outputDirectory, "plugins.json");
            try {
                URL url = new URL(metadataUrl + "plugins.json");
                URLConnection connection = url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                connection.connect();
                InputStream stream = connection.getInputStream();
                ReadableByteChannel rbc = Channels.newChannel(stream);
                FileOutputStream fos = new FileOutputStream(pluginsJsonFile);
                long read = fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                rbc.close();
                stream.close();
                getLog().debug("Read " + read + " bytes from url '" + url + "' and write to '" + pluginsJsonFile.getAbsolutePath() + "'");
            } catch (Exception e) {
                throw new MojoExecutionException("Error while downloading previous 'plugins.json'", e);
            }

            // Init json
            PluginsMetadata pluginsMetadata;
            Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
            if (pluginsJsonFile.exists()) {
                try {
                    FileReader reader = new FileReader(pluginsJsonFile);
                    pluginsMetadata = gson.fromJson(reader, PluginsMetadata.class);
                    reader.close();
                    getLog().debug("Read previous plugins.json file");
                } catch (JsonSyntaxException e) {
                    throw new MojoExecutionException("Error while reading previous 'plugins.json'", e);
                } catch (JsonIOException e) {
                    throw new MojoExecutionException("Error while reading previous 'plugins.json'", e);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error while reading previous 'plugins.json'", e);
                }
            } else {
                pluginsMetadata = new PluginsMetadata();
                pluginsMetadata.plugins = new ArrayList<PluginMetadata>();
                getLog().debug("Create plugins.json");
            }

            // Build metadata
            for (Map.Entry<MavenProject, List<MavenProject>> entry : tree.entrySet()) {
                MavenProject topPlugin = entry.getKey();
                PluginMetadata pm = new PluginMetadata();
                pm.id = topPlugin.getArtifactId();

                // Find previous
                boolean foundPrevious = false;
                for (PluginMetadata oldPm : pluginsMetadata.plugins) {
                    if (oldPm.id.equals(pm.id)) {
                        pm = oldPm;
                        foundPrevious = true;
                        getLog().debug("Found matching plugin id=" + pm.id + " in previous plugins.json");
                        break;
                    }
                }

                // Skip if the plugin version has not changed
                if(foundPrevious && pm.versions.containsKey(gephiVersion) &&
                    pm.versions.get(gephiVersion).plugin_version != null &&
                    pm.versions.get(gephiVersion).plugin_version.equals(entry.getKey().getVersion())) {
                    getLog().info("Skipped plugin id=" + pm.id
                        + " because the version for gephi.version="
                        + gephiVersion + " hasn't changed (" + entry.getKey().getVersion() + ")");

                    // Set property so it can be used in CreateAutoUpdate task
                    topPlugin.getProperties().setProperty("skipPlugin", "true");
                    for (MavenProject childPlugin : entry.getValue()) {
                        childPlugin.getProperties().setProperty("skipPlugin", "true");
                    }
                    continue;
                } else {
                    getLog().info("Updating plugin id=" + pm.id
                        + " to version '"+entry.getKey().getVersion()+ "'" );
                }

                manifestUtils.readManifestMetadata(topPlugin, pm);
                pm.license = MetadataUtils.getLicenseName(topPlugin);
                pm.authors = MetadataUtils.getAuthors(topPlugin);
                pm.last_update = dateFormat.format(new Date());
                pm.readme = MetadataUtils.getReadme(topPlugin, getLog());
                pm.images = ScreenshotUtils.copyScreenshots(topPlugin, new File(outputDirectory, "imgs" + File.separator + pm.id), "imgs" + "/" + pm.id + "/", getLog(), dryRun);
                pm.homepage = MetadataUtils.getHomepage(topPlugin);
                pm.sourcecode = MetadataUtils.getSourceCode(topPlugin, getLog());

                if (pm.versions == null) {
                    pm.versions = new HashMap<String, Version>();
                }
                Version v = new Version();
                v.last_update = dateFormat.format(new Date());
                v.url = gephiMinorVersion + "/" + ModuleUtils.getModuleDownloadPath(entry.getKey(), entry.getValue(), new File(outputDirectory, gephiMinorVersion), getLog());
                v.plugin_version = entry.getKey().getVersion();
                pm.versions.put(gephiVersion, v);

                if (!foundPrevious) {
                    pluginsMetadata.plugins.add(pm);
                }
            }

            String json = gson.toJson(pluginsMetadata);

            if(!dryRun) {
                // Write json file
                try {
                    FileWriter writer = new FileWriter(pluginsJsonFile);
                    writer.append(json);
                    writer.close();
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error while writing plugins.json file", ex);
                }
            }
            getLog().info("Plugins.json file written with "+pluginsMetadata.plugins.size()+" plugins");
        } else {
            throw new MojoExecutionException("The project should be a reactor project");
        }
    }
}
