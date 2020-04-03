package org.e2immu.intellij.highlighter;

import org.e2immu.annotation.Container;
import org.junit.Assert;
import org.junit.Test;

public class TestConstants {

    @Test
    public void test() {
        Assert.assertEquals("org.e2immu.annotation.Container", Container.class.getCanonicalName());
        Assert.assertEquals("container", Constants.HARDCODED_ANNOTATION_MAP.get(Container.class.getCanonicalName()));
        Assert.assertEquals("container", Constants.HARDCODED_ANNOTATION_MAP.get("org.e2immu.annotation.Container"));
    }
}
