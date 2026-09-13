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
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import org.gephi.maven.json.Author;
import org.gephi.maven.json.PluginMetadata;

/**
 * Validate the plugin.
 */
@Mojo(name = "validate", aggregator = true, defaultPhase = LifecyclePhase.VALIDATE)
public class Validate extends AbstractMojo {

    /**
     * Above this length, the short description warning is triggered as it's
     * meant to be a single, short tagline rather than a paragraph.
     */
    private static final int SHORT_DESCRIPTION_WARNING_LENGTH = 200;

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

        // An IdentityHashMap is required here: two distinct plugin projects that
        // mistakenly share the same groupId/artifactId/version (the exact case
        // checkNoDuplicatePlugins needs to catch) are "equal" per MavenProject's
        // own equals()/hashCode(), so a regular Map would silently collapse them
        // into a single entry before the check below ever runs.
        Map<MavenProject, PluginMetadata> topLevelMetadata = new IdentityHashMap<MavenProject, PluginMetadata>();

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
            topLevelMetadata.put(entry.getKey(), checkMetadata(entry.getKey()));
        }

        // Cross-plugin checks, only meaningful when comparing multiple top-level plugins
        ModuleUtils.checkNoDuplicatePlugins(topLevelMetadata, getLog());
    }

    private PluginMetadata checkMetadata(MavenProject moduleProject) throws MojoExecutionException {
        if (MetadataUtils.getLicenseName(moduleProject) == null) {
            throw new MojoExecutionException("The 'licenseName' configuration should be set for the project '" + moduleProject.getName() + "'. This can be added to the configuration of the 'nbm-maven-plugin' plugin. In addition, a 'licenseFile' can be specified, relative to the module's root folder.");
        }
        if (MetadataUtils.getAuthors(moduleProject) == null) {
            throw new MojoExecutionException("The 'author' configuration should be set fot the project '" + moduleProject.getName() + "'. This can be added to the configuration of the 'nbm-maven-plugin' plugin. Multiple authors can be specificed, separated by a comma.");
        }

        PluginMetadata metadata = new PluginMetadata();
        manifestUtils.readManifestMetadata(moduleProject, metadata);

        checkReadme(moduleProject);
        checkLicenseFile(moduleProject);
        checkAuthorEmails(moduleProject);
        checkScreenshots(moduleProject);
        checkFolderName(moduleProject);
        checkShortDescriptionLength(moduleProject, metadata);

        return metadata;
    }

    /**
     * Warns when no README.md file is present, as it's displayed on the
     * plugin's page. Not a hard failure since the 'generate' goal itself
     * treats it as optional.
     */
    private void checkReadme(MavenProject moduleProject) {
        String readme = MetadataUtils.getReadme(moduleProject, getLog());
        if (readme == null || readme.trim().isEmpty()) {
            getLog().warn("No 'README.md' file was found at the root of the project '" + moduleProject.getName() + "'. It is displayed on the plugin's page and helps users understand what the plugin does, consider adding one.");
        }
    }

    /**
     * Default file name used when downloading a license text for a project
     * that has no 'licenseFile' configured at all.
     */
    private static final String DEFAULT_LICENSE_FILE_NAME = "LICENSE.txt";

    /**
     * Checks that a license file exists for the project, either at the
     * configured 'licenseFile' path or, if not configured, at a conventional
     * default location. When the file is missing and the 'licenseName' is a
     * recognized common license (e.g. 'Apache 2.0', 'MIT'), its text is
     * automatically downloaded from SPDX's license-list-data so contributors
     * don't have to track it down themselves.
     * <p>
     * Still fails when an explicitly configured 'licenseFile' points to a
     * missing file and the license isn't recognized, since that combination
     * is always a mistake. Otherwise, an unrecognized license only produces
     * a warning.
     */
    private void checkLicenseFile(MavenProject moduleProject) throws MojoExecutionException {
        String licenseName = MetadataUtils.getLicenseName(moduleProject);
        String licenseFile = MetadataUtils.getLicenseFile(moduleProject);
        boolean licenseFileConfigured = licenseFile != null && !licenseFile.trim().isEmpty();
        String targetPath = licenseFileConfigured ? licenseFile : DEFAULT_LICENSE_FILE_NAME;
        File file = new File(moduleProject.getBasedir(), targetPath);

        if (file.exists()) {
            return;
        }

        String spdxId = LicenseUtils.resolveSpdxId(licenseName);
        if (spdxId != null && LicenseUtils.downloadLicenseText(spdxId, file, getLog())) {
            getLog().info("Downloaded the '" + licenseName + "' license text to '" + targetPath + "' for project '" + moduleProject.getName() + "'."
                    + (licenseFileConfigured ? "" : " Consider referencing it by adding <licenseFile>" + targetPath + "</licenseFile> to the 'nbm-maven-plugin' configuration."));
            return;
        }

        if (licenseFileConfigured) {
            throw new MojoExecutionException("The 'licenseFile' configuration for project '" + moduleProject.getName() + "' points to '" + licenseFile + "' but this file can't be found in '" + moduleProject.getBasedir().getAbsolutePath() + "', and the license '" + licenseName + "' isn't recognized for automatic download. Please add the file manually.");
        } else {
            getLog().warn("No license file was found for project '" + moduleProject.getName() + "', and the license '" + licenseName + "' isn't recognized for automatic download. Consider adding a license file and referencing it via the 'licenseFile' configuration.");
        }
    }

    /**
     * Warns when an 'authorEmail' is set but doesn't look like a valid email
     * address, reusing the same pattern the interactive 'generate' goal
     * enforces.
     */
    private void checkAuthorEmails(MavenProject moduleProject) {
        List<Author> authors = MetadataUtils.getAuthors(moduleProject);
        if (authors != null) {
            for (Author author : authors) {
                if (author.email != null && !author.email.isEmpty()
                        && !GenerateUtils.VALID_EMAIL_ADDRESS_REGEX.matcher(author.email).find()) {
                    getLog().warn("The 'authorEmail' value '" + author.email + "' for project '" + moduleProject.getName() + "' doesn't look like a valid email address.");
                }
            }
        }
    }

    /**
     * Warns when no screenshot is found, as it makes the plugin's page more
     * appealing.
     */
    private void checkScreenshots(MavenProject moduleProject) {
        if (!ScreenshotUtils.hasScreenshots(moduleProject)) {
            getLog().warn("No screenshot images were found in 'src/img' for project '" + moduleProject.getName() + "'. Adding at least one is recommended, it makes the plugin's page more appealing.");
        }
    }

    /**
     * Warns when the module's folder name doesn't follow the convention
     * enforced by the interactive 'generate' goal.
     */
    private void checkFolderName(MavenProject moduleProject) {
        String folderName = moduleProject.getBasedir().getName();
        if (!folderName.matches("[a-zA-Z0-9]+") || !Character.isUpperCase(folderName.charAt(0))) {
            getLog().warn("The plugin folder '" + folderName + "' for project '" + moduleProject.getName() + "' should ideally only contain letters and digits and start with an uppercase character (e.g. 'MyPlugin').");
        }
    }

    /**
     * Warns when the short description is unusually long, as it's meant to
     * be a short tagline (see the 'generate' goal's own prompt).
     */
    private void checkShortDescriptionLength(MavenProject moduleProject, PluginMetadata metadata) {
        if (metadata.short_description != null && metadata.short_description.length() > SHORT_DESCRIPTION_WARNING_LENGTH) {
            getLog().warn("The short description for project '" + moduleProject.getName() + "' is " + metadata.short_description.length() + " characters long. It's meant to be a short, one-sentence tagline; consider shortening it and moving details to the long description or the README instead.");
        }
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
