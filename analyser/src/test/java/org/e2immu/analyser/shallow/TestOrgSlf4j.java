package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOrgSlf4j extends CommonAnnotatedAPI {

    @Test
    public void testLoggerError() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Logger.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("error", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
    }

    @Test
    public void testLoggerFactoryGetLogger() {
        TypeInfo typeInfo = typeContext.getFullyQualified(LoggerFactory.class);
        TypeInfo clazz = typeContext.getFullyQualified(Class.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("getLogger", clazz);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
    }
}
