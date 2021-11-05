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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.config.AnnotationXmlConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLinkingExpression {

    protected static TypeContext typeContext;

    // NO annotated APIs, but an XML file
    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath("src/test/resources/org/e2immu/analyser/model/expression")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi");
        AnnotationXmlConfiguration annotationXmlConfiguration = new AnnotationXmlConfiguration.Builder()
                .addAnnotationXmlReadPackages("java.util")
                .build();
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotationXmConfiguration(annotationXmlConfiguration)
                .addDebugLogTargets(Stream.of(DELAYED, ANALYSER).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.preload("java.util");
        parser.run();
        typeContext = parser.getTypeContext();
    }

    private static final EvaluationContext evaluationContext = new EvaluationContext() {
        @Override
        public AnalyserContext getAnalyserContext() {
            return new AnalyserContext() {
                @Override
                public Primitives getPrimitives() {
                    return typeContext.getPrimitives();
                }

                @Override
                public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
                    return AnalysisProvider.DEFAULT_PROVIDER.getParameterAnalysis(parameterInfo);
                }
            };
        }

        @Override
        public Stream<DelayDebugNode> streamNodes() {
            return null;
        }
    };

    @Test
    public void testNewObject1() {
        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo arrayListConstructor = arrayList.findConstructor(0);

        // new ArrayList<>()
        ConstructorCall newObject = ConstructorCall.objectCreation(Identifier.CONSTANT,
                arrayListConstructor, arrayList.asParameterizedType(typeContext.typeMapBuilder),
                Diamond.YES, List.of());
        LinkedVariables linkedVariables = newObject.linkedVariables(evaluationContext);
        assertTrue(linkedVariables.isEmpty());
    }

    @Test
    public void testNewObject2() {
        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        ParameterizedType collectionInteger = new ParameterizedType(collection, List.of(integer()));
        MethodInfo arrayListConstructor = arrayList.findConstructor(collection);

        Variable v = new LocalVariableReference(new LocalVariable("v", collectionInteger));
        VariableExpression ve = new VariableExpression(v);

        // new ArrayList<>(v), with v a local collection variable
        // because no AnnotatedAPI, v is @Dependent
        ConstructorCall newObject = ConstructorCall.objectCreation(Identifier.CONSTANT,
                arrayListConstructor, arrayList.asParameterizedType(typeContext.typeMapBuilder),
                Diamond.YES, List.of(ve));
        LinkedVariables linkedVariables = newObject.linkedVariables(evaluationContext);
        assertEquals("v:1", linkedVariables.toString());

        // new ArrayList<>(v).get(0)
        //TypeInfo list = typeContext.getFullyQualified(List.class);
        MethodInfo listGet = arrayList.findUniqueMethod("get", 1);
        assertEquals(MultiLevel.INDEPENDENT_1, listGet.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));

        MethodCall get0 = new MethodCall(Identifier.CONSTANT, newObject, listGet,
                List.of(newInt(0)));

        LinkedVariables lvGet = get0.linkedVariables(evaluationContext);
        assertEquals("v:2", lvGet.toString());

        // new ArrayList<>(v).subList(1, 2)
        MethodInfo listSubList = arrayList.findUniqueMethod("subList", 2);
        assertEquals(MultiLevel.DEPENDENT, listSubList.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));
        MethodCall subList12 = new MethodCall(Identifier.CONSTANT, newObject, listSubList,
                List.of(newInt(1), newInt(2)));

        LinkedVariables lvSubList = subList12.linkedVariables(evaluationContext);
        assertEquals("v:1", lvSubList.toString());

        // (Collection<Integer>) new ArrayList<>(v)
        Cast collectionIntCast = new Cast(newObject, collectionInteger);
        LinkedVariables lvCast = collectionIntCast.linkedVariables(evaluationContext);
        assertEquals("v:1", lvCast.toString());
    }

    private ParameterizedType integer() {
        return typeContext.getPrimitives().integerTypeInfo.asSimpleParameterizedType();
    }

    @Test
    public void testModifyingMethod() {
        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);
        // see XML file
        MethodInfo addIndex = arrayList.findUniqueMethod("add", 2);
        assertEquals(Level.TRUE, addIndex.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

        ParameterAnalysis p1 = addIndex.parameterAnalysis(1);
        assertEquals(MultiLevel.INDEPENDENT_1, p1.getProperty(VariableProperty.INDEPENDENT));

        ParameterizedType arrayListInteger = new ParameterizedType(arrayList, List.of(integer()));
        Variable v = new LocalVariableReference(new LocalVariable("v", arrayListInteger));
        VariableExpression ve = new VariableExpression(v);

        Variable i = new LocalVariableReference(new LocalVariable("i", integer()));
        VariableExpression vi = new VariableExpression(i);

        // v.add(0, i)
        MethodCall add12 = new MethodCall(Identifier.CONSTANT, ve, addIndex, List.of(newInt(0), vi));
        LinkedVariables lvAdd12 = add12.linkedVariables(evaluationContext);
        assertTrue(lvAdd12.isEmpty()); // because void method!

        LinkedVariables lvAdd12Scope = add12.linked1VariablesScope(evaluationContext);
        assertEquals("i:2", lvAdd12Scope.toString());
    }

    @Test
    public void testCollectionsAddAll() {
        TypeInfo collections = typeContext.getFullyQualified(Collections.class);
        MethodInfo addAll = collections.findUniqueMethod("addAll", 2);


        assertEquals("{0={1=2}}", addAll.crossLinks(InspectionProvider.DEFAULT).toString());

        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        ParameterizedType collectionInteger = new ParameterizedType(collection, List.of(integer()));

        Variable v = new LocalVariableReference(new LocalVariable("v", collectionInteger));
        VariableExpression ve = new VariableExpression(v);

        Variable i = new LocalVariableReference(new LocalVariable("i", integer()));
        VariableExpression vi = new VariableExpression(i);

        Variable j = new LocalVariableReference(new LocalVariable("j", integer()));
        VariableExpression vj = new VariableExpression(j);

        // Collections.addAll(v, i, j)
        MethodCall methodCall = new MethodCall(Identifier.CONSTANT,
                new TypeExpression(collectionInteger, Diamond.NO), addAll,
                List.of(ve, vi, vj));
        assertEquals("Collection.addAll(v,i,j)", methodCall.toString());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        methodCall.linksBetweenParameters(builder, evaluationContext);
        // v links @Independent1 to i and j
        assertEquals("i:2,j:2", builder.build().changeData().get(v).linkedVariables().toString());
    }

    private IntConstant newInt(int i) {
        return new IntConstant(typeContext.getPrimitives(), i);
    }
}
