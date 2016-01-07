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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.Manifest.Section;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Migrate ant-based plugin.
 */
@Mojo(name = "migrate", aggregator = true)
public class Migrate extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(required = true, readonly = true, property = "project")
    private MavenProject project;

    //Set of Gephi + Netbeans dependencies
    private Set<Dependency> gephiDependencies;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File baseDir = project.getBasedir();

        //Info
        getLog().info("Start migrating ant-based plugin folder.");

        //Root pom file
        File pomFile = new File(baseDir, "pom.xml");
        if (!pomFile.exists()) {
            throw new MojoExecutionException("The 'pom.xml' file doesn' exist at '" + pomFile.getAbsolutePath() + "'");
        }

        //Get gephi.version
        String gephiVersion = project.getProperties().getProperty("gephi.version");
        if (gephiVersion == null) {
            throw new MojoExecutionException("Can't obtain gephi version number");
        }
        getLog().info("Gephi version is defined as '" + gephiVersion + "'");

        //Get modules folder
        List<String> folders = collectFolders();
        if (folders.isEmpty()) {
            throw new MojoExecutionException("No plugin modules have been found this folder");
        }
        getLog().info("Found " + folders.size() + " modules to migrate:");
        for (String moduleStr : folders) {
            getLog().info("  '" + moduleStr + "' will be migrated to 'modules/" + moduleStr + "'");
        }

        //Collect modules data
        Map<String, ProjectMetadata> foldersMetadata = new HashMap<String, ProjectMetadata>();
        for (String moduleFolder : folders) {
            ProjectMetadata projectMetadata = new ProjectMetadata();
            File folder = new File(baseDir, moduleFolder);

            //Check nbproject
            File nbProjectFolder = checkNbproject(folder);

            //Process project.xml
            File projectXMLFile = new File(nbProjectFolder, "project.xml");
            if (!projectXMLFile.exists()) {
                throw new MojoExecutionException("The 'project.xml' file can't be found at " + projectXMLFile.getAbsolutePath());
            }
            projectMetadata.codeNameBase = collectCodeName(projectXMLFile);
            getLog().debug("Module: '" + moduleFolder + "' has code name '" + projectMetadata.codeNameBase + "'");

            //Process dependencies
            projectMetadata.dependencies = collectDependencies(projectXMLFile, moduleFolder);

            //Collect public packages
            projectMetadata.publicPackages = collectPublicPackages(projectXMLFile);

            //Collect metadata
            collectManifest(folder, projectMetadata);

            //Collect project props
            collectProjectProperties(nbProjectFolder, projectMetadata);

            foldersMetadata.put(moduleFolder, projectMetadata);
        }

        //Generate
        for (Map.Entry<String, ProjectMetadata> entry : foldersMetadata.entrySet()) {
            String folder = entry.getKey();
            ProjectMetadata metadata = entry.getValue();

            //Create folder (if needed)
            File pluginFolder = new File(baseDir, "modules" + File.separator + folder);
            if (pluginFolder.exists()) {
                try {
                    getLog().warn("The plugin folder 'modules" + File.separator + folder + "' already exists, the configuration will be overriden");
                    FileUtils.deleteDirectory(pluginFolder);
                    getLog().debug("Deleted folder '" + pluginFolder.getAbsolutePath() + "'");
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error while deleting previous '" + folder + "' directory", ex);
                }
            }
            GenerateUtils.createFolder(new File(baseDir, "modules" + File.separator + folder), getLog());

            //Get sourcode
            String sourceCodeUrl = MetadataUtils.getSourceCodeUrlFromGit(project, getLog());
            getLog().debug("Obtained source code url from Git: " + sourceCodeUrl);

            // Compute org and artifact
            String org = metadata.codeNameBase;
            String artifact = metadata.codeNameBase;
            if (org.contains(".")) {
                artifact = org.substring(org.lastIndexOf(".") + 1);
                org = org.substring(0, org.lastIndexOf("."));
            }

            //License
            String licenseFile = null;
            String license = null;
            if (metadata.licenseFile != null) {
                File file = new File(baseDir, folder + File.separator + metadata.licenseFile.replace('/', File.separatorChar));
                if (file.exists()) {
                    try {
                        File dest = new File(pluginFolder, file.getName());
                        FileUtils.copyFile(file, dest);
                        licenseFile = dest.getName();
                        license = licenseFile.contains(".") ? licenseFile.substring(0, licenseFile.lastIndexOf('.')) : licenseFile;
                    } catch (IOException ex) {
                        getLog().error("Error while copying license file for module'" + folder + "'", ex);
                    }
                }
            }

            //Author
            if (metadata.author == null) {
                metadata.author = "Unknown";
            }

            //Create pom.xml
            File modulePomFile = new File(pluginFolder, "pom.xml");
            GenerateUtils.createTopPomFile(modulePomFile, gephiVersion, org, artifact, "1.0.0", folder, metadata.author, null, null, license, licenseFile, sourceCodeUrl, metadata.homepageUrl);
            getLog().debug("Created 'pom.xml' file at '" + pluginFolder.getAbsolutePath() + "'");

            //Insert deps
            insertDependencies(modulePomFile, metadata.dependencies);
            getLog().debug("Inserted " + metadata.dependencies.size() + " dependencies into 'pom.xml'");

            //Insert public packages
            if (!metadata.publicPackages.isEmpty()) {
                insertPublicPackages(modulePomFile, metadata.publicPackages);
                getLog().debug("Inserted " + metadata.publicPackages.size() + " public packages into 'pom.xml'");
            }

            //Create nbm, java and resources in src/main folder
            File srcMain = GenerateUtils.createFolder(new File(pluginFolder, "src" + File.separator + "main"), getLog());
            File nbmFolder = GenerateUtils.createFolder(new File(srcMain, "nbm"), getLog());
            File javaFolder = new File(srcMain, "java");
            File resourcesFolder = new File(srcMain, "resources");
            GenerateUtils.createFolder(javaFolder, getLog());
            GenerateUtils.createFolder(resourcesFolder, getLog());

            //Copy source code
            File srcFolder = new File(folder, "src");
            if (srcFolder.exists()) {
                try {
                    String[] pattern = new String[]{"**\\*.java", "**\\*.form"};
                    copyFiles(srcFolder, javaFolder, pattern, null);
                    getLog().debug("Copied source folders to '" + javaFolder.getAbsolutePath() + "'");
                    copyFiles(srcFolder, resourcesFolder, null, pattern);
                    getLog().debug("Copied resources folders to '" + resourcesFolder.getAbsolutePath() + "'");
                } catch (IOException e) {
                    throw new MojoExecutionException("Error while copying source files", e);
                }
            } else {
                getLog().warn("The 'src' folder at '" + srcFolder.getAbsolutePath() + "' doesn't exists");
            }

            //Copy test code
            File testFolder = new File(folder, "test");
            if (testFolder.exists()) {
                if (new File(testFolder, "unit" + File.separator + "src").exists()) {
                    testFolder = new File(testFolder, "unit" + File.separator + "src");
                }
                File srcTest = GenerateUtils.createFolder(new File(pluginFolder, "src" + File.separator + "test"), getLog());
                File testJavaFolder = new File(srcTest, "java");
                File testResourcesFolder = new File(srcTest, "resources");
                try {
                    String[] pattern = new String[]{"**\\*.java", "**\\*.form"};
                    copyFiles(testFolder, testJavaFolder, pattern, null);
                    getLog().debug("Copied test source folders to '" + testJavaFolder.getAbsolutePath() + "'");
                    copyFiles(testFolder, testResourcesFolder, null, pattern);
                    getLog().debug("Copied test resources folders to '" + testResourcesFolder.getAbsolutePath() + "'");
                } catch (IOException e) {
                    throw new MojoExecutionException("Error while copying test source files", e);
                }
            }

            //Manifest
            if (metadata.category == null) {
                metadata.category = "Other Category";
            }

            //Create manifest
            if (metadata.localizingBundle != null) {
                GenerateUtils.createManifest(new File(nbmFolder, "manifest.mf"), metadata.localizingBundle);
            } else {
                GenerateUtils.createManifest(new File(nbmFolder, "manifest.mf"), metadata.brandingName, metadata.shortDescription, metadata.longDescription, metadata.category);
            }
            getLog().debug("Created 'manifest.mf' file at '" + nbmFolder.getAbsolutePath() + "'");

            //Create img folder
            GenerateUtils.createFolder(new File(pluginFolder, "src" + File.separator + "img"), getLog());

            //Add module to pom
            GenerateUtils.addModuleToPom(pomFile, folder, getLog());
            getLog().debug("Inserted '" + folder + "' into the list of modules in 'pom.xml'");
        }
    }

    /**
     * Collect project properties in <em>project.properties</em> file.
     *
     * @param nbProjectFolder nbproject folder
     * @param projectMetadata project metadata
     * @throws MojoExecutionException if an error occurs
     */
    private void collectProjectProperties(File nbProjectFolder, ProjectMetadata projectMetadata) throws MojoExecutionException {
        File projectPropertiesFile = new File(nbProjectFolder, "project.properties");
        if (!projectPropertiesFile.exists()) {
            throw new MojoExecutionException("The 'project.properties' can't be found in '" + nbProjectFolder.getAbsolutePath() + "'");
        }
        Properties prop = new Properties();
        FileReader bundleReader = null;
        try {
            bundleReader = new FileReader(projectPropertiesFile);
            prop.load(bundleReader);
            projectMetadata.homepageUrl = prop.getProperty("nbm.homepage", null);
            projectMetadata.author = prop.getProperty("nbm.module.author", null);
            projectMetadata.licenseFile = prop.getProperty("license.file", null);
        } catch (IOException e) {
            throw new MojoExecutionException("Error while reading '" + projectPropertiesFile.getAbsolutePath() + "'", e);
        } finally {
            if (bundleReader != null) {
                try {
                    bundleReader.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    /**
     * Collect branding name, short description, long description and category
     * from manifest file.
     *
     * @param folder module folder
     * @param projectMetadata project metadata
     * @throws MojoExecutionException if an error occurs
     */
    private void collectManifest(File folder, ProjectMetadata projectMetadata) throws MojoExecutionException {
        File manifestFile = new File(folder, "manifest.mf");
        if (!manifestFile.exists()) {
            throw new MojoExecutionException("The 'manifest.mf' can't be found in '" + folder.getAbsolutePath() + "'");
        }
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

        // Get section
        Section section = null;
        if (manifest == null) {
            throw new MojoExecutionException("The manifest is malformed at '" + manifestFile.getAbsolutePath() + "'");
        } else {
            section = manifest.getMainSection();
        }

        //Metadata
        if (section.getAttributeValue("OpenIDE-Module-Localizing-Bundle") != null) {
            projectMetadata.localizingBundle = section.getAttributeValue("OpenIDE-Module-Localizing-Bundle");
        } else {
            projectMetadata.brandingName = section.getAttributeValue("OpenIDE-Module-Name");
            projectMetadata.shortDescription = section.getAttributeValue("OpenIDE-Module-Short-Description");
            projectMetadata.longDescription = section.getAttributeValue("OpenIDE-Module-Long-Description");
            projectMetadata.category = section.getAttributeValue("OpenIDE-Module-Display-Category");
        }
    }

    /**
     * Checks the given folder has a <em>nbproject</em> folder.
     *
     * @param folder folder to check
     * @return the found nbproject folder
     * @throws MojoExecutionException if an error occurs
     */
    private File checkNbproject(File folder) throws MojoExecutionException {
        File result = new File(folder, "nbproject");
        if (!result.exists()) {
            throw new MojoExecutionException("The 'nbproject' can't be found in '" + folder.getAbsolutePath() + "'");
        }
        return result;
    }

    /**
     * Collect list of module folders. A module folder is recognized based on
     * whether it has a <em>nbproject</em> folder.
     *
     * @return list of folders
     * @throws MojoExecutionException if an error occurs
     */
    private List<String> collectFolders() throws MojoExecutionException {
        List<String> result = new ArrayList<String>();
        for (File child : project.getBasedir().listFiles()) {
            if (child.isDirectory() && new File(child, "nbproject").exists()) {
                result.add(child.getName());
                getLog().debug("Found module folder '" + child.getAbsolutePath() + "'");
            }
        }
        return result;
    }

    /**
     * Collect list of public packages from <em>project.xml</em>.
     *
     * @param projectXMLFile project xml file
     * @return list of public packages
     * @throws MojoExecutionException if an error occurs
     */
    private List<String> collectPublicPackages(File projectXMLFile) throws MojoExecutionException {
        return getStringsFromXML(projectXMLFile, "/project/configuration/data/public-packages/package/text()");
    }

    /**
     * Collect dependencies from the <em>project.xml</em> file.
     * <p>
     * Only gephi dependencies defined in <em>modules/pom.xml</em> are kept.
     *
     * @param projectXMLFile project.xml dependencies
     * @param moduleName name of module
     * @return set of dependencies
     * @throws MojoExecutionException if an error occurs
     */
    private Set<Dependency> collectDependencies(File projectXMLFile, String moduleName) throws MojoExecutionException {
        if (gephiDependencies == null) {
            //Collect Gephi dependencies
            gephiDependencies = collectGephiDependencies();
            getLog().debug(gephiDependencies.size() + " dependencies collected from 'modules/pom.xml'");
        }

        Set<Dependency> result = new HashSet<Dependency>();
        List<String> deps = getStringsFromXML(projectXMLFile, "/project/configuration/data/module-dependencies/dependency/code-name-base/text()");
        getLog().debug(deps.size() + " dependencies found in '" + projectXMLFile.getAbsolutePath() + "'");
        for (String dep : deps) {
            Dependency dependency = null;
            if (dep.startsWith("org.gephi")) {
                String artifactId = dep.substring("org.gephi.".length()).replace('.', '-');
                dependency = new Dependency("org.gephi", artifactId);
            } else if (dep.startsWith("org.openide")) {
                dependency = new Dependency("org.netbeans.api", dep.replace('.', '-'));
            } else if (dep.startsWith("org.netbeans")) {
                dependency = new Dependency("org.netbeans.api", dep.replace('.', '-'));
            }

            if (dependency != null && gephiDependencies.contains(dependency)) {
                getLog().debug("Found dependency '" + dependency + "' in '" + moduleName + "'");
                result.add(dependency);
            } else {
                getLog().warn("The '" + dep + "' dependency in '" + moduleName + "' couldn't be migrated, please add it manually in 'modules/" + moduleName + "'");
            }
        }
        return result;
    }

    /**
     * Collect the <em>code-name</em> in the <em>project.xml</em> file.
     *
     * @param projectXMLFile project.xml file
     * @return code name
     * @throws MojoExecutionException if an error occurs
     */
    private String collectCodeName(File projectXMLFile) throws MojoExecutionException {
        return getStringFromXML(projectXMLFile, "/project/configuration/data/code-name-base/text()");
    }

    /**
     * Execute <em>XPath</em> query on the given XML file and retrieve the
     * singular text value.
     *
     * @param file xml file
     * @param path xpath query
     * @return text value
     * @throws MojoExecutionException if an error occurs
     */
    private String getStringFromXML(File file, String path) throws MojoExecutionException {
        NodeList nl = getNodeListFromXml(file, path);
        if (nl.getLength() != 1) {
            throw new MojoExecutionException("The expected number of items is 1, but " + nl.getLength() + " was found");
        }
        Node node = nl.item(0);
        return node.getTextContent();
    }

    /**
     * Execute <em>XPath</em> query on the given XML file and return the list of
     * text values.
     *
     * @param file xml file
     * @param path xpath query
     * @return list of text values
     * @throws MojoExecutionException if an error occurs
     */
    private List<String> getStringsFromXML(File file, String path) throws MojoExecutionException {
        NodeList nl = getNodeListFromXml(file, path);
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            result.add(node.getTextContent());
        }
        return result;
    }

    /**
     * Execute <em>XPath</em> query on the given XML file and return the list of
     * nodes.
     *
     * @param file xml file
     * @param path xpath query
     * @return list of nodes
     * @throws MojoExecutionException if an error occurs
     */
    private NodeList getNodeListFromXml(File file, String path) throws MojoExecutionException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile(path);
            NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            return nl;
        } catch (Exception ex) {
            throw new MojoExecutionException("Error while reading XML file '" + file.getAbsolutePath() + "' with XPath='" + path + "'", ex);
        }
    }

    /**
     * Inspect the <em>modules/pom.xml</em> to collect all the gephi and
     * netbeans dependencies in the parent pom.
     * <p>
     * These dependencies are the set plugins can use.
     *
     * @return set of dependencies
     * @throws MojoExecutionException if an error occurs
     */
    private Set<Dependency> collectGephiDependencies() throws MojoExecutionException {
        Set<Dependency> result = new HashSet<Dependency>();
        File modulesDir = new File(project.getBasedir(), "modules");
        if (!modulesDir.exists()) {
            throw new MojoExecutionException("The 'modules' folder doesn't exists at '" + modulesDir + "'");
        }
        File pomFile = new File(modulesDir, "pom.xml");
        if (!pomFile.exists()) {
            throw new MojoExecutionException("The 'pom.xml' file doesn't exists at '" + pomFile + "'");
        }
        NodeList nl = getNodeListFromXml(pomFile, "/project/dependencyManagement/dependencies/dependency");
        for (int i = 0; i < nl.getLength(); i++) {
            String groupId = null, artifactId = null;
            Node n = nl.item(i);
            NodeList children = n.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node m = children.item(j);
                if (m.getNodeName().equals("groupId")) {
                    groupId = m.getTextContent();
                } else if (m.getNodeName().equals("artifactId")) {
                    artifactId = m.getTextContent();
                }
            }
            if (groupId != null && artifactId != null) {
                if ("${project.groupId}".equals(groupId)) {
                    groupId = "org.gephi";
                }
                result.add(new Dependency(groupId, artifactId));
                getLog().debug("Found dependency groupId=" + groupId + " artifactId=" + artifactId);
            }
        }
        return result;
    }

    private void insertDependencies(File xmlFile, Set<Dependency> dependencies) throws MojoExecutionException {
        try {
            Document doc = parseXMLDocument(xmlFile);

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("/project/dependencies");
            NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

            for (Dependency d : dependencies) {
                Element dep = doc.createElement("dependency");
                Element group = doc.createElement("groupId");
                Element artifact = doc.createElement("artifactId");
                group.appendChild(doc.createTextNode(d.groupId));
                artifact.appendChild(doc.createTextNode(d.artifactId));
                dep.appendChild(group);
                dep.appendChild(artifact);
                nodes.item(0).appendChild(dep);
            }

            FileWriter writer = new FileWriter(xmlFile);
            StreamResult result = new StreamResult(writer);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), result);
            writer.close();
        } catch (Exception e) {
            throw new MojoExecutionException("Error while inserting dependencies in XML document '" + xmlFile + "'", e);
        }
    }

    private void insertPublicPackages(File xmlFile, List<String> packages) throws MojoExecutionException {
        try {
            Document doc = parseXMLDocument(xmlFile);

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("/project/build/plugins/plugin[artifactId = 'nbm-maven-plugin']/configuration/publicPackages");
            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);

            for (String p : packages) {
                Element pp = doc.createElement("publicPackage");
                pp.appendChild(doc.createTextNode(p));
                node.appendChild(pp);
            }

            FileWriter writer = new FileWriter(xmlFile);
            StreamResult result = new StreamResult(writer);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), result);
            writer.close();
        } catch (Exception e) {
            throw new MojoExecutionException("Error while inserting public packages in XML document '" + xmlFile + "'", e);
        }
    }

    private Document parseXMLDocument(File xmlFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setIgnoringComments(true);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        return doc;
    }

    /**
     * Simple <em>groupId</em> + <em>artifactId</em> internal class.
     */
    private static class Dependency {

        private final String groupId;
        private final String artifactId;

        public Dependency(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + (this.groupId != null ? this.groupId.hashCode() : 0);
            hash = 37 * hash + (this.artifactId != null ? this.artifactId.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Dependency other = (Dependency) obj;
            if ((this.groupId == null) ? (other.groupId != null) : !this.groupId.equals(other.groupId)) {
                return false;
            }
            if ((this.artifactId == null) ? (other.artifactId != null) : !this.artifactId.equals(other.artifactId)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "[groupId=" + groupId + " artifactId=" + artifactId + "]";
        }
    }

    private static class ProjectMetadata {

        private String brandingName;
        private String category;
        private String shortDescription;
        private String longDescription;
        private String author;
        private String homepageUrl;
        private String codeNameBase;
        private String localizingBundle;
        private List<String> publicPackages;
        private Set<Dependency> dependencies;
        private String licenseFile;
    }

    private void copyFiles(File srcFolder, File destFolder, String[] includes, String[] excludes) throws IOException {

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setBasedir(srcFolder);

        if (includes != null && includes.length >= 1) {
            scanner.setIncludes(includes);
        } else {
            scanner.setIncludes(new String[]{"**"});
        }

        if (excludes != null && excludes.length >= 1) {
            scanner.setExcludes(excludes);
        }

        scanner.addDefaultExcludes();
        scanner.scan();
        List files = Arrays.asList(scanner.getIncludedFiles());

        for (Iterator i = files.iterator(); i.hasNext();) {
            String name = (String) i.next();

            File source = new File(srcFolder, name);

            if (source.equals(srcFolder)) {
                continue;
            }

            File target = new File(destFolder, name);

            getLog().debug("Copying file '" + source.getAbsolutePath() + "' to '" + target.getAbsolutePath() + "'");
            FileUtils.copyFile(source, target);
        }
    }
}
