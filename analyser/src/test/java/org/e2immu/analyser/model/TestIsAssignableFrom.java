package org.e2immu.analyser.model;

import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.inspector.TypeContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class TestIsAssignableFrom {

    private static TypeContext typeContext;
    private static Primitives primitives;

    @BeforeClass
    public static void beforeClass() throws IOException {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.INSPECT);
        Parser parser = new Parser();
        typeContext = parser.getTypeContext();
        primitives = typeContext.getPrimitives();
        parser.getByteCodeInspector().inspectFromPath("java/util/List");
    }

    // int <- String should fail, int <- Integer should not
    @Test
    public void test() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String").asParameterizedType());
        ParameterizedType integerPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.Integer").asParameterizedType());

        Assert.assertTrue(integerPt.isAssignableFrom(typeContext, primitives.intParameterizedType));
        Assert.assertTrue(primitives.intParameterizedType.isAssignableFrom(typeContext, primitives.intParameterizedType));
        Assert.assertTrue(primitives.intParameterizedType.isAssignableFrom(typeContext, integerPt));

        Assert.assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, stringPt));
        Assert.assertFalse(stringPt.isAssignableFrom(typeContext, primitives.intParameterizedType));
    }

    // CharSequence[] <- String[] should be allowed
    @Test
    public void testArray() {
        ParameterizedType stringArrayPt = new ParameterizedType(Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String")), 1);
        ParameterizedType charSeqArrayPt = new ParameterizedType(Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.CharSequence")), 1);

        String[] strings = {"a", "b"};
        CharSequence[] sequences = strings;
        for (CharSequence sequence : sequences) {
            Assert.assertEquals(1, sequence.length());
        }
        Assert.assertFalse(stringArrayPt.isAssignableFrom(typeContext, charSeqArrayPt));
        Assert.assertTrue(charSeqArrayPt.isAssignableFrom(typeContext, stringArrayPt));
    }

    // String <- null should be allowed, but int <- null should fail
    @Test
    public void testNull() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String").asParameterizedType());

        Assert.assertFalse(ParameterizedType.NULL_CONSTANT.isAssignableFrom(typeContext, stringPt));
        Assert.assertTrue(stringPt.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));

        Assert.assertFalse(ParameterizedType.NULL_CONSTANT.isAssignableFrom(typeContext, primitives.intParameterizedType));
        Assert.assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));
    }

    // E <- String, E <- Integer, E <- int, E <- int[] should work
    @Test
    public void testBoxing() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String").asParameterizedType());
        ParameterizedType integerPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.Integer").asParameterizedType());
        ParameterizedType listPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.util.List").asParameterizedType());
        ParameterizedType typeParam = listPt.parameters.get(0);
        Assert.assertNotNull(typeParam);

        Assert.assertTrue(typeParam.isAssignableFrom(typeContext, stringPt));
        Assert.assertFalse(stringPt.isAssignableFrom(typeContext, typeParam));

        Assert.assertTrue(typeParam.isAssignableFrom(typeContext, integerPt));
        Assert.assertFalse(integerPt.isAssignableFrom(typeContext, typeParam));

        List<int[]> intArrayList;
        Assert.assertTrue(typeParam.isAssignableFrom(typeContext, primitives.intParameterizedType));
        Assert.assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, typeParam));
    }
}
