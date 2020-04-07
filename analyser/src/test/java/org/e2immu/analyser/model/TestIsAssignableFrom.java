package org.e2immu.analyser.model;

import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public class TestIsAssignableFrom {

    private static TypeContext typeContext;

    @BeforeClass
    public static void beforeClass() throws IOException {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.INSPECT);
        Parser parser = new Parser();
        typeContext = parser.getTypeContext();
    }

    @Test
    public void test() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeStore.get("java.lang.String").asParameterizedType());

        Assert.assertFalse(Primitives.PRIMITIVES.intParameterizedType.isAssignableFrom(stringPt));
        Assert.assertFalse(stringPt.isAssignableFrom(Primitives.PRIMITIVES.intParameterizedType));
    }
}
