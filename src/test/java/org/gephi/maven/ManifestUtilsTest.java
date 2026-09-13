package org.gephi.maven;

import java.io.File;
import java.io.FileWriter;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.gephi.maven.json.PluginMetadata;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ManifestUtilsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testValidateCategory() {
    Assert.assertTrue(ManifestUtils.validateCategory("Layout"));
    Assert.assertTrue(ManifestUtils.validateCategory("Other Category"));
    Assert.assertFalse(ManifestUtils.validateCategory("Bogus"));
    Assert.assertFalse(ManifestUtils.validateCategory(null));
  }

  @Test
  public void testReadManifestMetadataFailsWithClearErrorWhenLineTooLong() throws Exception {
    MavenProject project = newProject("MyPlugin", writeManifest(
        "OpenIDE-Module-Name: My Plugin",
        "OpenIDE-Module-Short-Description: " + repeat("A", 500),
        "OpenIDE-Module-Long-Description: A long description",
        "OpenIDE-Module-Display-Category: Layout"));

    ManifestUtils manifestUtils = new ManifestUtils("src/main/nbm/manifest.mf", new SystemStreamLog());
    try {
      manifestUtils.readManifestMetadata(project, new PluginMetadata());
      Assert.fail("Expected a MojoExecutionException");
    } catch (MojoExecutionException ex) {
      Assert.assertTrue("Error message should point at the offending entry: " + ex.getMessage(),
          ex.getMessage().contains("OpenIDE-Module-Short-Description"));
      Assert.assertTrue("Error message should mention the byte limit: " + ex.getMessage(),
          ex.getMessage().contains("512-byte limit"));
    }
  }

  @Test
  public void testReadManifestMetadataSucceedsWhenLinesAreShort() throws Exception {
    MavenProject project = newProject("MyPlugin", writeManifest(
        "OpenIDE-Module-Name: My Plugin",
        "OpenIDE-Module-Short-Description: A short tagline",
        "OpenIDE-Module-Long-Description: A reasonably short long description",
        "OpenIDE-Module-Display-Category: Layout"));

    ManifestUtils manifestUtils = new ManifestUtils("src/main/nbm/manifest.mf", new SystemStreamLog());
    PluginMetadata metadata = new PluginMetadata();
    manifestUtils.readManifestMetadata(project, metadata);

    Assert.assertEquals("My Plugin", metadata.name);
    Assert.assertEquals("Layout", metadata.category);
  }

  private File writeManifest(String... lines) throws Exception {
    File moduleFolder = temporaryFolder.newFolder("MyPlugin");
    File nbmFolder = new File(moduleFolder, "src" + File.separator + "main" + File.separator + "nbm");
    nbmFolder.mkdirs();
    File manifestFile = new File(nbmFolder, "manifest.mf");

    FileWriter writer = new FileWriter(manifestFile);
    writer.write("Manifest-Version: 1.0\n");
    for (String line : lines) {
      writer.write(line);
      writer.write("\n");
    }
    writer.close();
    return moduleFolder;
  }

  private static MavenProject newProject(String name, File basedir) {
    Model model = new Model();
    model.setName(name);
    MavenProject project = new MavenProject(model);
    project.setFile(new File(basedir, "pom.xml"));
    return project;
  }

  private static String repeat(String s, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
  }
}
