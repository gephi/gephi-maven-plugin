package org.gephi.maven;

import java.io.File;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GenerateUtilsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGetPackageName() {
    Assert.assertEquals("my.company.myplugin", GenerateUtils.getPackageName("my.company", "my-plugin"));
    Assert.assertEquals("my.company.plugin", GenerateUtils.getPackageName("my.company", "plugin"));
  }

  @Test
  public void testCreatePackageFolder() throws Exception {
    File srcJava = temporaryFolder.newFolder("java");

    File packageFolder = GenerateUtils.createPackageFolder(srcJava, "my.company.myplugin", new SystemStreamLog());

    File expected = new File(srcJava, "my" + File.separator + "company" + File.separator + "myplugin");
    Assert.assertEquals(expected, packageFolder);
    Assert.assertTrue(expected.exists());
    Assert.assertTrue(expected.isDirectory());
  }
}
