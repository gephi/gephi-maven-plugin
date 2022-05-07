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
import java.util.List;
import java.util.Map;
import org.apache.maven.Maven;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.gephi.maven.json.PluginMetadata;

/**
 * Validate the plugin.
 */
@Mojo(name = "validate", aggregator = true, defaultPhase = LifecyclePhase.VALIDATE)
public class Validate extends AbstractMojo {

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
     * Manifest Utils.
     */
    private ManifestUtils manifestUtils;

    /**
     * The Maven project.
     */
    @Parameter(required = true, readonly = true, property = "project")
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        manifestUtils = new ManifestUtils(sourceManifestFile, getLog());
        if (reactorProjects != null && reactorProjects.size() > 0) {
            getLog().debug("Found " + reactorProjects.size() + " projects in reactor");
            List<MavenProject> modules = new ArrayList<MavenProject>();
            for (MavenProject proj : reactorProjects) {
                if (proj.getPackaging().equals("nbm")) {
                    getLog().debug("Found 'nbm' project '" + proj.getName() + "' with artifactId=" + proj.getArtifactId() + " and groupId=" + proj.getGroupId());
                    modules.add(proj);
                }
            }

            if (modules.isEmpty()) {
                throw new MojoExecutionException("No 'nbm' modules have been detected, make sure to add folders to <modules> into pom");
            } else if (modules.size() == 1) {
                // Unique NBM module
                executeSingleModuleProject(modules.get(0));
            } else {
                executeMultiModuleProject(modules);
            }
        } else {
            throw new MojoExecutionException("The project should be a reactor project");
        }
    }

    private void executeSingleModuleProject(MavenProject moduleProject) throws MojoExecutionException, MojoFailureException {
        getLog().info("Unique module found: '" + moduleProject.getName() + "'");
        checkGephiVersion(moduleProject);
        checkMetadata(moduleProject);
    }

    private void executeMultiModuleProject(List<MavenProject> projects) throws MojoExecutionException, MojoFailureException {
        // Multiple NBM modules
        Map<MavenProject, List<MavenProject>> tree = ModuleUtils.getModulesTree(projects, getLog());
        if (tree.isEmpty()) {
            throw new MojoExecutionException("Multiple modules have been found but no suite detected, make sure one of the module has dependencies on the others");
        } else {
            getLog().info("Multiple modules found: " + tree.size() + " projects");
        }

        for (Map.Entry<MavenProject, List<MavenProject>> entry : tree.entrySet()) {
            getLog().info("Suite of modules found: '" + entry.getKey().getName() + "'");
            List<MavenProject> children = entry.getValue();
            checkSameGephiVersion(children);

            children.remove(entry.getKey());
            if(children.isEmpty()) {
                getLog().info("   Single module '" + entry.getKey().getName() + "'");
            }
            for (MavenProject child : children) {
                getLog().info("   '" + child.getName() + "' is a dependency");
            }
            for (MavenProject child : children) {
                checkGephiVersion(child);
                manifestUtils.checkManifestShowClientFalse(child);
            }
            checkGephiVersion(entry.getKey());
            checkMetadata(entry.getKey());
        }
    }

    private void checkMetadata(MavenProject moduleProject) throws MojoExecutionException {
        if (MetadataUtils.getLicenseName(moduleProject) == null) {
            throw new MojoExecutionException("The 'licenseName' configuration should be set for the project '" + moduleProject.getName() + "'. This can be added to the configuration of the 'nbm-maven-plugin' plugin. In addition, a 'licenseFile' can be specified, relative to the module's root folder.");
        }
        if (MetadataUtils.getAuthors(moduleProject) == null) {
            throw new MojoExecutionException("The 'author' configuration should be set fot the project '" + moduleProject.getName() + "'. This can be added to the configuration of the 'nbm-maven-plugin' plugin. Multiple authors can be specificed, separated by a comma.");
        }

        manifestUtils.readManifestMetadata(moduleProject, new PluginMetadata());
    }

    private void checkGephiVersion(MavenProject moduleProject) throws MojoExecutionException {
        String moduleVersion = moduleProject.getProperties().getProperty("gephi.version");
        String projectVersion = project.getProperties().getProperty("gephi.version");
        if (!moduleVersion.equals(projectVersion)) {
            getLog().warn("The project '" + moduleProject.getName() + "' depends on Gephi version '" + moduleVersion + "' but '" + projectVersion + "' is expected, it will be ignored");
        }
    }

    private void checkSameGephiVersion(List<MavenProject> projects) throws MojoExecutionException {
        String key = null;
        for (MavenProject moduleProject : projects) {
            String moduleVersion = moduleProject.getProperties().getProperty("gephi.version");
            if (key == null) {
                key = moduleVersion;
            } else if (!key.equals(moduleVersion)) {
                throw new MojoExecutionException("Inconsistent 'gephi.version' property between modules of a same suite. All modules of the same and their plugin dependencies (if any) should have the same 'gephi.version'.");
            }
        }
    }
}
