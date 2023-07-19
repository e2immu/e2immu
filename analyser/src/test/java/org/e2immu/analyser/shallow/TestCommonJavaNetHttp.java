/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonJavaNetHttp extends CommonAnnotatedAPI {

    @Test
    public void testHttpRequestNewBuilder() {
        TypeInfo typeInfo = typeContext.getFullyQualified(HttpRequest.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("newBuilder", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testHttpRequestNewBuilderUri() {
        TypeInfo uri = typeContext.getFullyQualified(URI.class);
        TypeInfo typeInfo = typeContext.getFullyQualified(HttpRequest.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("newBuilder", uri);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        MethodInfo zeroParam = typeInfo.findUniqueMethod("newBuilder", 0);
        MethodAnalysis.GetSetEquivalent getSetEquivalent = methodAnalysis.getSetEquivalent();
        assertSame(zeroParam, getSetEquivalent.methodInfo());
        ParameterInfo firstParam = methodInfo.methodInspection.get().getParameters().get(0);
        assertEquals(1, getSetEquivalent.convertToGetSet().size());
        assertSame(firstParam, getSetEquivalent.convertToGetSet().stream().findFirst().orElseThrow());
    }

    @Test
    public void testHttpRequestBuilderGET() {
        TypeInfo typeInfo = typeContext.getFullyQualified(HttpRequest.Builder.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("GET", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.FLUENT));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertTrue(methodAnalysis.getCommutableData().isDefault());
    }

    @Test
    public void testHttpRequestBuilderUri() {
        TypeInfo typeInfo = typeContext.getFullyQualified(HttpRequest.Builder.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("uri", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.FLUENT));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertTrue(methodAnalysis.getCommutableData().isDefault());
    }

    @Test
    public void testHttpRequestBuilderTimeout() {
        TypeInfo typeInfo = typeContext.getFullyQualified(HttpRequest.Builder.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("timeout", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.FLUENT));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertTrue(methodAnalysis.getCommutableData().isDefault());
    }
}
