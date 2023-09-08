package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCommonJavaUtilConcurrentAtomic extends CommonAnnotatedAPI {

    @Test
    public void testAtomicIntegerGet() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AtomicInteger.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("get", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
    }
}
