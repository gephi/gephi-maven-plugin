package org.gephi.maven;

import java.util.Collections;
import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gephi.maven.json.Author;
import org.junit.Assert;
import org.junit.Test;

public class MetadataUtilsTest {

  @Test
  public void testMinorVersion() throws Exception {
    Assert.assertEquals("0.9", MetadataUtils.getMinorVersion("0.9.3"));
    Assert.assertEquals("0.9-SNAPSHOT", MetadataUtils.getMinorVersion("0.9.3-SNAPSHOT"));
  }

  @Test(expected = MojoExecutionException.class)
  public void testMinorVersionBadFormat() throws Exception {
    MetadataUtils.getMinorVersion("foo");
  }

  @Test
  public void testGetAuthorsFromGephiPlugin() throws Exception {
    MavenProject project = newProject(
        newPlugin("org.apache.netbeans.utilities", "nbm-maven-plugin", "author", "Jane Doe"),
        newPlugin("org.gephi", "gephi-maven-plugin", "authorEmail", "jane@doe.com", "authorUrl", "https://jane.doe"));

    List<Author> authors = MetadataUtils.getAuthors(project);
    Assert.assertEquals(1, authors.size());
    Assert.assertEquals("Jane Doe", authors.get(0).name);
    Assert.assertEquals("jane@doe.com", authors.get(0).email);
    Assert.assertEquals("https://jane.doe", authors.get(0).link);
  }

  @Test
  public void testGetAuthorsFallsBackToNbmPluginConfiguration() throws Exception {
    MavenProject project = newProject(
        newPlugin("org.apache.netbeans.utilities", "nbm-maven-plugin", "author", "Jane Doe",
            "authorEmail", "jane@doe.com", "authorUrl", "https://jane.doe"));

    List<Author> authors = MetadataUtils.getAuthors(project);
    Assert.assertEquals(1, authors.size());
    Assert.assertEquals("Jane Doe", authors.get(0).name);
    Assert.assertEquals("jane@doe.com", authors.get(0).email);
    Assert.assertEquals("https://jane.doe", authors.get(0).link);
  }

  @Test
  public void testGetLicenseFile() throws Exception {
    MavenProject project = newProject(
        newPlugin("org.apache.netbeans.utilities", "nbm-maven-plugin", "licenseName", "Apache 2.0", "licenseFile", "LICENSE.txt"));

    Assert.assertEquals("LICENSE.txt", MetadataUtils.getLicenseFile(project));
  }

  @Test
  public void testGetLicenseFileWhenNotSet() throws Exception {
    MavenProject project = newProject(
        newPlugin("org.apache.netbeans.utilities", "nbm-maven-plugin", "licenseName", "Apache 2.0"));

    Assert.assertNull(MetadataUtils.getLicenseFile(project));
  }

  @Test
  public void testGetSourceCodeFromGephiPlugin() throws Exception {
    MavenProject project = newProject(
        newPlugin("org.gephi", "gephi-maven-plugin", "sourceCodeUrl", "https://github.com/gephi/my-plugin"));

    Assert.assertEquals("https://github.com/gephi/my-plugin",
        MetadataUtils.getSourceCode(project, new SystemStreamLog()));
  }

  @Test
  public void testGetSourceCodeFallsBackToNbmPluginConfiguration() throws Exception {
    MavenProject project = newProject(
        newPlugin("org.apache.netbeans.utilities", "nbm-maven-plugin", "sourceCodeUrl", "https://github.com/gephi/my-plugin"));

    Assert.assertEquals("https://github.com/gephi/my-plugin",
        MetadataUtils.getSourceCode(project, new SystemStreamLog()));
  }

  private static MavenProject newProject(Plugin... plugins) {
    Model model = new Model();
    Build build = new Build();
    build.setPlugins(Collections.unmodifiableList(java.util.Arrays.asList(plugins)));
    model.setBuild(build);
    return new MavenProject(model);
  }

  private static Plugin newPlugin(String groupId, String artifactId, String... keyValues) {
    Plugin plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);

    Xpp3Dom configuration = new Xpp3Dom("configuration");
    for (int i = 0; i < keyValues.length; i += 2) {
      Xpp3Dom child = new Xpp3Dom(keyValues[i]);
      child.setValue(keyValues[i + 1]);
      configuration.addChild(child);
    }
    plugin.setConfiguration(configuration);
    return plugin;
  }
}
