package org.gephi.maven;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
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

  @Test
  public void testEscapeXml() {
    Assert.assertEquals("John &quot;Nickname&quot; Doe &lt;john.doe@example.com&gt;",
            GenerateUtils.escapeXml("John \"Nickname\" Doe <john.doe@example.com>"));
    Assert.assertEquals("Tom &amp; Jerry", GenerateUtils.escapeXml("Tom & Jerry"));
    Assert.assertNull(GenerateUtils.escapeXml(null));
  }

  @Test
  public void testCreateTopPomFileEscapesAuthorAngleBrackets() throws Exception {
    File pomFile = new File(temporaryFolder.newFolder("plugin"), "pom.xml");

    GenerateUtils.createTopPomFile(pomFile, "1.0.0", "1.0.0", "org.example", "my-plugin", "1.0.0",
            "My Plugin", "John \"Nickname\" Doe <john.doe@example.com>", null, null, null, null, null, null);

    Assert.assertTrue(pomFile.exists());

    // The generated file must be well-formed XML despite the angle brackets in the author name.
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile);
  }
}
