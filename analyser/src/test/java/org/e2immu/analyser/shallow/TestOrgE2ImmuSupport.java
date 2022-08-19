package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.support.EventuallyFinal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestOrgE2ImmuSupport extends CommonAnnotatedAPI {

    @Test
    public void testEventuallyFinal() {
        TypeInfo typeInfo = typeContext.getFullyQualified(EventuallyFinal.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(MultiLevel.INDEPENDENT_1_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, typeAnalysis.immutableDeterminedByTypeParameters());
    }

    @Test
    public void testEventuallyFinalSetFinal() {
        TypeInfo typeInfo = typeContext.getFullyQualified(EventuallyFinal.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setFinal", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals("[isFinal]", methodAnalysis.getPreconditionForEventual().expression().toString());
        assertTrue(methodAnalysis.getEventual().mark());
    }

    @Test
    public void testEventuallyFinalSetVariable() {
        TypeInfo typeInfo = typeContext.getFullyQualified(EventuallyFinal.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setVariable", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertFalse(methodAnalysis.getEventual().mark());
        assertEquals(Boolean.FALSE, methodAnalysis.getEventual().after());
    }
}
