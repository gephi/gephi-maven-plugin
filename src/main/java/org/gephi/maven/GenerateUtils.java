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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
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
}
