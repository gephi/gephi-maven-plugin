package org.gephi.maven;

import org.apache.maven.plugin.MojoExecutionException;
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
}
