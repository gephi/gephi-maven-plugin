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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generate new plugin.
 */
@Mojo(name = "generate", aggregator = true)
public class Generate extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(required = true, readonly = true, property = "project")
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File baseDir = project.getBasedir();

        //Info
        getLog().info("Start generating Gephi plugin configuration base. This process only creates things (does not delete anything).");
        getLog().info("It's recommended to pull/merge the latest changes from the master branch before starting.");

        //Get gephi.version
        String gephiVersion = project.getProperties().getProperty("gephi.version");
        if (gephiVersion == null) {
            throw new MojoExecutionException("Can't obtain gephi version number");
        }
        getLog().info("Gephi version is defined as '" + gephiVersion + "'");

        //Checks
        File pomFile = new File(baseDir, "pom.xml");
        if (!pomFile.exists()) {
            throw new MojoExecutionException("The 'pom.xml' file doesn' exist at '" + pomFile.getAbsolutePath() + "'");
        }

        getLog().info("Gephi Maven Plugin version: " + GenerateUtils.getPluginVersion(project));

        String org, artifact, branding, folder, category, version,
                shortDescription, longDescription, author, license, readme,
                authorEmail, authorUrl;

        Scanner input = new Scanner(System.in);

        getLog().info("Please answer the following questions so the plugin can be configured. All values can be changed afterwards by editing the configuration files.\n");

        System.out.println("Configuration:");
        //Organization
        do {
            System.out.print("  Name of organization (e.g. my.company): ");
        } while ((org = GenerateUtils.validateOrganization(input.nextLine())) == null);

        //Artifact
        do {
            System.out.print("  Name of artifact (e.g my-plugin): ");
        } while ((artifact = GenerateUtils.validateArtifact(input.nextLine())) == null);

        //Version
        do {
            System.out.print("  Version (e.g. 1.0.0): ");
        } while ((version = GenerateUtils.validateVersion(input.nextLine())) == null);

        //Folder
        do {
            System.out.print("  Directory name (e.g MyPlugin): ");
        } while ((folder = GenerateUtils.validateFolderName(input.nextLine())) == null);

        //Branding
        do {
            System.out.print("  Branding name (e.g My Plugin): ");
        } while ((branding = GenerateUtils.validateBrandingName(input.nextLine())) == null);

        //Category
        do {
            System.out.print("  Category (e.g Layout, Filter, etc.): ");
        } while ((category = GenerateUtils.validateCategory(input.nextLine())) == null);

        //Author
        do {
            System.out.print("  Author: ");
        } while ((author = GenerateUtils.validateAuthor(input.nextLine())) == null);

        //Author email
        do {
            System.out.print("  Author email (optional): ");
        } while ((authorEmail = GenerateUtils.validateAuthorEmail(input.nextLine())) == null);

        //Author url
        do {
            System.out.print("  Author URL (optional): ");
        } while ((authorUrl = GenerateUtils.validateAuthorUrl(input.nextLine())) == null);

        //License
        do {
            System.out.print("  License (e.g Apache 2.0): ");
        } while ((license = GenerateUtils.validatePomEntry(input.nextLine())) == null);

        //Short description
        do {
            System.out.print("  Short description (i.e. one sentence): ");
        } while ((shortDescription = GenerateUtils.validateDescription(input.nextLine())) == null);

        //Long description
        do {
            System.out.print("  Long description (i.e multiple sentences): ");
        } while ((longDescription = GenerateUtils.validateDescription(input.nextLine())) == null);

        //Readme
        do {
            System.out.print("  Would you like to add a README.md file (yes|no): ");
        } while ((readme = GenerateUtils.validateYesNoChoice(input.nextLine())) == null);

        //Create folder
        File pluginFolder = new File(baseDir, "modules" + File.separator + folder);
        if (pluginFolder.exists()) {
            String response;
            do {
                System.out.print("The plugin folder 'modules" + File.separator + folder + "' already exists, the configuration will be overriden, would you like to continue? (yes|no): ");
            } while ((response = GenerateUtils.validateYesNoChoice(input.nextLine())) == null);
            if (response.matches("(?i)n|no")) {
                getLog().warn("Process aborted, nothing has been done");
                return;
            }
        } else {
            GenerateUtils.createFolder(new File(baseDir, "modules" + File.separator + folder), getLog());
        }

        //Close input
        input.close();

        //Get source code
        String sourceCodeUrl = MetadataUtils.getSourceCodeUrlFromGit(project, getLog());
        getLog().debug("Obtained source code url from Git: " + sourceCodeUrl);

        //Create pom.xml
        GenerateUtils.createTopPomFile(new File(pluginFolder, "pom.xml"), gephiVersion, org, artifact, version, branding, author, authorEmail, authorUrl, license, null, sourceCodeUrl, null);
        getLog().debug("Created 'pom.xml' file at '" + pluginFolder.getAbsolutePath() + "'");

        //Readme
        if (readme.matches("(?i)y|yes")) {
            createReadme(pluginFolder, branding);
            getLog().debug("Created 'README.md' file at '" + pluginFolder.getAbsolutePath() + "'");
        }

        //Create nbm, java and resources in src/main folder
        File srcMain = GenerateUtils.createFolder(new File(pluginFolder, "src" + File.separator + "main"), getLog());
        File nbmFolder = GenerateUtils.createFolder(new File(srcMain, "nbm"), getLog());
        GenerateUtils.createFolder(new File(srcMain, "java"), getLog());
        GenerateUtils.createFolder(new File(srcMain, "resources"), getLog());

        //Create src/test/java
        File srcTest = GenerateUtils.createFolder(new File(pluginFolder, "src" + File.separator + "test"), getLog());
        GenerateUtils.createFolder(new File(srcTest, "java"), getLog());

        //Create manifest
        GenerateUtils.createManifest(new File(nbmFolder, "manifest.mf"), branding, shortDescription, longDescription, category);
        getLog().debug("Created 'manifest.mf' file at '" + nbmFolder.getAbsolutePath() + "'");

        //Create img folder
        GenerateUtils.createFolder(new File(pluginFolder, "src" + File.separator + "img"), getLog());

        //Add module to pom
        GenerateUtils.addModuleToPom(pomFile, folder, getLog());
        getLog().debug("Inserted '" + folder + "' into the list of modules in 'pom.xml'");

        //Info
        getLog().info("The configuration is successful. All values can be changed afterwards by editing the following configurations files:\n"
                + "  - pom.xml: Module path listed in <modules></modules>, need to be updated if module folder is renamed\n"
                + "  - modules" + File.separator + folder + File.separator + "pom.xml: Organization, version, name, author, license\n"
                + "  - modules" + File.separator + folder + File.separator + "src" + File.separator + "main" + File.separator + "nbm: Branding name, short description, long description, category");
        getLog().info("Finished.");
    }

    private void createReadme(File folder, String brandingName) throws MojoExecutionException {
        FileWriter writer = null;
        try {
            File readmeFile = new File(folder, "README.md");
            writer = new FileWriter(readmeFile);
            writer.append("## " + brandingName + "\n\n");
            writer.append("This README supports Markdown, see [syntax](https://help.github.com/articles/markdown-basics/)\n\n");
        } catch (IOException ex) {
            throw new MojoExecutionException("Error while writing 'README.md'", ex);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ex) {
            }
        }
    }
}
