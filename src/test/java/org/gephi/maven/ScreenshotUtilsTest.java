package org.gephi.maven;

import java.io.File;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ScreenshotUtilsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testHasScreenshotsWhenFolderMissing() throws Exception {
    MavenProject project = newProject(temporaryFolder.newFolder("MyPlugin"));
    Assert.assertFalse(ScreenshotUtils.hasScreenshots(project));
  }

  @Test
  public void testHasScreenshotsWhenFolderEmpty() throws Exception {
    File basedir = temporaryFolder.newFolder("MyPlugin");
    new File(basedir, "src/img").mkdirs();

    Assert.assertFalse(ScreenshotUtils.hasScreenshots(newProject(basedir)));
  }

  @Test
  public void testHasScreenshotsWhenOnlyThumbnailPresent() throws Exception {
    File basedir = temporaryFolder.newFolder("MyPlugin");
    File imgFolder = new File(basedir, "src/img");
    imgFolder.mkdirs();
    new File(imgFolder, "screenshot-thumbnail.png").createNewFile();

    Assert.assertFalse(ScreenshotUtils.hasScreenshots(newProject(basedir)));
  }

  @Test
  public void testHasScreenshotsWhenImagePresent() throws Exception {
    File basedir = temporaryFolder.newFolder("MyPlugin");
    File imgFolder = new File(basedir, "src/img");
    imgFolder.mkdirs();
    new File(imgFolder, "screenshot.png").createNewFile();

    Assert.assertTrue(ScreenshotUtils.hasScreenshots(newProject(basedir)));
  }

  private static MavenProject newProject(File basedir) {
    Model model = new Model();
    model.setName("MyPlugin");
    MavenProject project = new MavenProject(model);
    project.setFile(new File(basedir, "pom.xml"));
    return project;
  }
}
