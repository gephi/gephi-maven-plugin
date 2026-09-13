package org.gephi.maven;

import org.junit.Assert;
import org.junit.Test;

public class LicenseUtilsTest {

  @Test
  public void testResolveSpdxIdKnownAliases() {
    Assert.assertEquals("Apache-2.0", LicenseUtils.resolveSpdxId("Apache 2.0"));
    Assert.assertEquals("Apache-2.0", LicenseUtils.resolveSpdxId("Apache License, Version 2.0"));
    Assert.assertEquals("MIT", LicenseUtils.resolveSpdxId("MIT"));
    Assert.assertEquals("MIT", LicenseUtils.resolveSpdxId("The MIT License"));
    Assert.assertEquals("BSD-3-Clause", LicenseUtils.resolveSpdxId("New BSD"));
    Assert.assertEquals("GPL-3.0-only", LicenseUtils.resolveSpdxId("GPL v3"));
    Assert.assertEquals("LGPL-2.1-only", LicenseUtils.resolveSpdxId("LGPL v2.1"));
    Assert.assertEquals("MPL-2.0", LicenseUtils.resolveSpdxId("Mozilla Public License 2.0"));
  }

  @Test
  public void testResolveSpdxIdIsCaseAndPunctuationInsensitive() {
    Assert.assertEquals("Apache-2.0", LicenseUtils.resolveSpdxId("apache-2.0"));
    Assert.assertEquals("Apache-2.0", LicenseUtils.resolveSpdxId("  APACHE 2.0  "));
  }

  @Test
  public void testResolveSpdxIdUnknownOrNull() {
    Assert.assertNull(LicenseUtils.resolveSpdxId("My Custom Proprietary License"));
    Assert.assertNull(LicenseUtils.resolveSpdxId(null));
    Assert.assertNull(LicenseUtils.resolveSpdxId(""));
  }
}
