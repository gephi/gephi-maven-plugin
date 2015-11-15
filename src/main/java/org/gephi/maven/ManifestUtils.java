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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.codehaus.mojo.nbm.utils.ExamineManifest;
import org.codehaus.plexus.util.IOUtil;
import org.apache.maven.plugin.logging.Log;

/**
 * Utilities to read and check values in modules manifest.
 */
public class ManifestUtils {

    /**
     * List of allowed plugin categories
     */
    private final static String CATEGORIES[] = new String[]{
        "Layout", "Export", "Import", "Data Laboratory",
        "Filter", "Generator", "Metric", "Preview", "Tool",
        "Appearance", "Clustering", "Other Category"
    };

    private final Log log;
    private final String sourceManifestFile;

    public ManifestUtils(String sourceManifestFile, Log log) {
        this.sourceManifestFile = sourceManifestFile;
        this.log = log;
    }

    /**
     * Check that the given project has the <em>AutoUpdate-Show-In-Client</em>
     * entry set to <em>false</em> in its manifest.
     *
     * @param proj project
     * @throws MojoExecutionException if an error occurs
     */
    protected void checkManifestShowClientFalse(MavenProject proj) throws MojoExecutionException {
        Manifest manifest = getManifest(proj);

        Manifest.Section mainSection = manifest.getMainSection();
        String showInClient = mainSection.getAttributeValue("AutoUpdate-Show-In-Client");
        if (showInClient == null || showInClient.isEmpty() || !showInClient.equals("false")) {
            throw new MojoExecutionException("The manifest.mf file for project '" + proj.getName() + "' should contain a 'AutoUpdate-Show-In-Client' entry set at 'false'");
        }
    }

    protected void readManifestMetadata(MavenProject proj, PluginMetadata metadata) throws MojoExecutionException {
        Manifest manifest = getManifest(proj);

        // Read branding attributes
        Manifest.Section mainSection = manifest.getMainSection();
        String brandingName = mainSection.getAttributeValue("OpenIDE-Module-Name");
        String brandingShortDescription = mainSection.getAttributeValue("OpenIDE-Module-Short-Description");
        String brandingLongDescrption = mainSection.getAttributeValue("OpenIDE-Module-Long-Description");
        String brandingDisplayCategory = mainSection.getAttributeValue("OpenIDE-Module-Display-Category");

        if (brandingName == null || brandingName.isEmpty()) {
            throw new MojoExecutionException("The manifest.mf file for project '" + proj.getName() + "' should contain a 'OpenIDE-Module-Name' entry");
        }
        if (brandingShortDescription == null || brandingShortDescription.isEmpty()) {
            throw new MojoExecutionException("The manifest.mf file for project '" + proj.getName() + "' should contain a 'OpenIDE-Module-Short-Description' entry");
        }
        if (brandingLongDescrption == null || brandingLongDescrption.isEmpty()) {
            throw new MojoExecutionException("The manifest.mf file for project '" + proj.getName() + "' should contain a 'OpenIDE-Module-Long-Description' entry");
        }
        if (brandingDisplayCategory == null || brandingDisplayCategory.isEmpty()) {
            throw new MojoExecutionException("The manifest.mf file for project '" + proj.getName() + "' should contain a 'OpenIDE-Module-Display-Category' entry");
        }

        Set<String> allowedCategories = new HashSet<String>(Arrays.asList(CATEGORIES));
        if (!allowedCategories.contains(brandingDisplayCategory)) {
            throw new MojoExecutionException("The manifest entry 'OpenIDE-Module-Display-Category' should be one of the following values: " + Arrays.toString(CATEGORIES).replace("[", "").replace("]", ""));
        }

        metadata.name = brandingName;
        metadata.shortDescription = brandingShortDescription;
        metadata.longDescription = brandingLongDescrption;
        metadata.category = brandingDisplayCategory;
    }

    /**
     * Find and return the Manifest for the given project.
     *
     * @param proj project
     * @return manifest
     * @throws MojoExecutionException if an error occurs
     */
    protected Manifest getManifest(MavenProject proj) throws MojoExecutionException {
        // Read project manifest file
        File manifestFile = new File(proj.getBasedir(), sourceManifestFile);
        if (!manifestFile.exists()) {
            throw new MojoExecutionException("Cannot locate a manifest.mf file at " + manifestFile.getAbsolutePath() + " for project " + proj.getName());
        }

        // Check validity
        ExamineManifest examinator = new ExamineManifest(log);
        examinator.setManifestFile(manifestFile);
        examinator.checkFile();

        // Read manifest
        Manifest manifest = null;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(manifestFile));
            manifest = new Manifest(reader);
        } catch (IOException exc) {
            throw new MojoExecutionException("Error reading manifest at " + manifestFile, exc);
        } catch (ManifestException ex) {
            throw new MojoExecutionException("Error reading manifest at " + manifestFile, ex);
        } finally {
            IOUtil.close(reader);
        }
        return manifest;
    }

}
