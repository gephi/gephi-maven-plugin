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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * Generate utilities.
 */
public class GenerateUtils {
    
    protected static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
    
    protected static VelocityEngine initVelocity() {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        ve.init();
        return ve;
    }
    
    protected static String validateDescription(String val) {
        return (val != null && !val.isEmpty()) ? val : null;
    }
    
    protected static String validatePomEntry(String val) {
        if (val != null) {
            if (!val.matches("[^><]+")) {
                System.err.println("The text can't contain '<' or '>' characters.");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateYesNoChoice(String val) {
        if (val != null && !val.isEmpty()) {
            if (!val.matches("(?i)y|n|yes|no")) {
                System.err.println("The answer should either be 'yes' or 'no'");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateCategory(String val) {
        if (val != null) {
            if (!ManifestUtils.validateCategory(val)) {
                System.err.println("The category should be one of the following options: " + Arrays.toString(ManifestUtils.CATEGORIES).replace("[", "").replace("]", ""));
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateAuthor(String val) {
        if (val != null && !val.isEmpty()) {
            if (!val.matches("[^><]+")) {
                System.err.println("The author name contains invalid characters");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateAuthorUrl(String val) {
        if (val != null && !val.isEmpty()) {
            if (!val.matches("[^><]+")) {
                System.err.println("The author url contains invalid characters");
                return null;
            } else {
                return val;
            }
        }
        return val;
    }
    
    protected static String validateAuthorEmail(String val) {
        if (val != null && !val.isEmpty()) {
            if (!VALID_EMAIL_ADDRESS_REGEX.matcher(val).find()) {
                System.err.println("The email format is not valid");
                return null;
            } else {
                return val;
            }
        }
        return val;
    }
    
    protected static String validateFolderName(String val) {
        if (val != null) {
            if (!val.matches("[a-zA-Z0-9]+")) {
                System.err.println("The plugin folder name can't contain spaces or special characters");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateBrandingName(String val) {
        if (val != null) {
            if (!val.matches("[a-zA-Z0-9 -]+")) {
                System.err.println("The branding name should only contain regular characters.");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateOrganization(String val) {
        if (val != null) {
            if (!val.matches("[a-z0-9.-]+")) {
                System.err.println("The organization should only contain lowercase characters. The '.' and '-' characters are also allowed.");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateArtifact(String val) {
        if (val != null) {
            if (!val.matches("[a-z0-9-]+")) {
                System.err.println("The artifact should only contain lowercase characters or '-'. It' also recommended to end it with a '-plugin' suffix.");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String validateVersion(String val) {
        if (val != null) {
            if (!val.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
                System.err.println("The version should match the semantic verisonning scheme: MAJOR.MINOR.PATCH (e.g. 1.0.1)");
            } else {
                return val;
            }
        }
        return null;
    }
    
    protected static String getPluginVersion(MavenProject project) {
        Set pluginArtifacts = project.getPluginArtifacts();
        for (Iterator iterator = pluginArtifacts.iterator(); iterator.hasNext();) {
            Artifact artifact = (Artifact) iterator.next();
            String groupId = artifact.getGroupId();
            String artifactId = artifact.getArtifactId();
            if (groupId.equals("org.gephi") && artifactId.equals("gephi-maven-plugin")) {
                return artifact.getVersion();
            }
        }
        return null;
    }
    
    protected static void addModuleToPom(File pomFile, String moduleName, Log log) throws MojoExecutionException {
        try {
            StringBuilder fileContent = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(pomFile));
            String toAdd = "        <module>modules/" + moduleName + "</module>";
            String line;
            boolean skip = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(toAdd)) {
                    log.debug("Found the module path, skipping to avoid duplicate");
                    skip = true;
                }
                if (!skip && line.contains("</modules>")) {
                    fileContent.append(toAdd).append("\n");
                    log.debug("Found '</modules>' string, inserting module path");
                }
                fileContent.append(line).append("\n");
            }
            reader.close();
            
            FileWriter writer = new FileWriter(pomFile);
            writer.append(fileContent.toString());
            writer.close();
        } catch (IOException ex) {
            throw new MojoExecutionException("Error while reading/writing 'pom.xml' at '" + pomFile.getAbsolutePath() + "'", ex);
        }
    }
    
    protected static void createManifest(File file, String branding, String shortDescription, String longDescription, String category) throws MojoExecutionException {
        createManifest(file, branding, shortDescription, longDescription, category, null);
    }
    
    protected static void createManifest(File file, String localizingBundle) throws MojoExecutionException {
        createManifest(file, null, null, null, null, localizingBundle);
    }
    
    protected static void createManifest(File file, String branding, String shortDescription, String longDescription, String category, String localizingBundle) throws MojoExecutionException {
        VelocityEngine ve = initVelocity();
        Template t = ve.getTemplate("org/gephi/maven/templates/plugin-manifest.mf", "UTF-8");
        
        VelocityContext context = new VelocityContext();
        context.put("branding_name", branding);
        context.put("short_description", shortDescription);
        context.put("long_description", longDescription);
        context.put("category", category);
        context.put("localizing_bundle", localizingBundle);
        
        try {
            FileWriter writer = new FileWriter(file);
            t.merge(context, writer);
            writer.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing manifest.mf file", e);
        }
    }
    
    protected static void createTopPomFile(File file, String gephiVersion, String orgId, String artifactId, String version, String brandingName, String authorName, String authorEmail, String authorUrl, String licenseName, String licenseFile, String sourceCodeUrl, String homepageUrl) throws MojoExecutionException {
        VelocityEngine ve = GenerateUtils.initVelocity();
        Template t = ve.getTemplate("org/gephi/maven/templates/top-plugin-pom.xml", "UTF-8");
        
        VelocityContext context = new VelocityContext();
        context.put("gephi_version", gephiVersion);
        context.put("org_id", orgId);
        context.put("artifact_id", artifactId);
        context.put("version", version);
        context.put("branding_name", brandingName);
        context.put("license_name", licenseName);
        context.put("license_file", licenseFile);
        context.put("author_name", authorName);
        context.put("author_email", authorEmail);
        context.put("author_url", authorUrl);
        context.put("sourcecode_url", sourceCodeUrl);
        context.put("homepage_url", homepageUrl);
        
        try {
            FileWriter writer = new FileWriter(file);
            t.merge(context, writer);
            writer.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing pom.xml file", e);
        }
    }
    
    protected static File createFolder(File folder, Log log) {
        if (folder.mkdirs()) {
            log.debug("Created folder at '" + folder.getAbsolutePath() + "'");
        }
        return folder;
    }
}
