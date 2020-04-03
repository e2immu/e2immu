package org.e2immu.intellij.highlighter.store;

import org.junit.Assert;
import org.junit.Test;

public class TestAnnotationStore {

    @Test
    public void testSelectAndAddSuffix() {
        Assert.assertEquals("e2immutable-t",
                AnnotationStore.select("java.lang.String", "e2immutable,constant"));
        Assert.assertEquals("notmodified-m",
                AnnotationStore.select("java.lang.String.toString()", "notmodified,constant"));
        Assert.assertEquals("notmodified-p",
                AnnotationStore.select("java.util.Set.add(T0)#0", "notmodified,constant"));
        Assert.assertEquals("notmodified-f",
                AnnotationStore.select("java.lang.System:out", "notmodified,constant"));
    }
}
