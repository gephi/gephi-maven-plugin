package org.gephi.maven;

import java.util.IdentityHashMap;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.gephi.maven.json.PluginMetadata;
import org.junit.Assert;
import org.junit.Test;

public class ModuleUtilsTest {

  @Test
  public void testCheckNoDuplicatePluginsPasses() throws Exception {
    Map<MavenProject, PluginMetadata> metadata = new IdentityHashMap<MavenProject, PluginMetadata>();
    metadata.put(newProject("org.example", "plugin-one"), newMetadata("Plugin One"));
    metadata.put(newProject("org.example", "plugin-two"), newMetadata("Plugin Two"));

    ModuleUtils.checkNoDuplicatePlugins(metadata, new SystemStreamLog());
  }

  @Test
  public void testCheckNoDuplicatePluginsFailsOnDuplicateIdentity() throws Exception {
    Map<MavenProject, PluginMetadata> metadata = new IdentityHashMap<MavenProject, PluginMetadata>();
    metadata.put(newProject("org.example", "my-plugin"), newMetadata("First Plugin"));
    metadata.put(newProject("org.example", "my-plugin"), newMetadata("Second Plugin"));

    try {
      ModuleUtils.checkNoDuplicatePlugins(metadata, new SystemStreamLog());
      Assert.fail("Expected a MojoExecutionException");
    } catch (MojoExecutionException ex) {
      Assert.assertTrue(ex.getMessage().contains("org.example:my-plugin"));
    }
  }

  @Test
  public void testCheckNoDuplicatePluginsWarnsOnDuplicateNameWithoutFailing() throws Exception {
    Map<MavenProject, PluginMetadata> metadata = new IdentityHashMap<MavenProject, PluginMetadata>();
    metadata.put(newProject("org.example", "plugin-one"), newMetadata("Same Name"));
    metadata.put(newProject("org.example", "plugin-two"), newMetadata("Same Name"));

    // Should not throw, only warn.
    ModuleUtils.checkNoDuplicatePlugins(metadata, new SystemStreamLog());
  }

  private static MavenProject newProject(String groupId, String artifactId) {
    Model model = new Model();
    model.setGroupId(groupId);
    model.setArtifactId(artifactId);
    model.setVersion("1.0.0");
    model.setName(artifactId);
    return new MavenProject(model);
  }

  private static PluginMetadata newMetadata(String name) {
    PluginMetadata metadata = new PluginMetadata();
    metadata.name = name;
    return metadata;
  }
}
