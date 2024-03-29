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
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnnotationXmlConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLinkingExpression {

    protected static TypeContext typeContext;
    private static EvaluationResult context;

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
                .addDebugLogTargets("analyser")
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.preload("java.util");
        parser.run();
        typeContext = parser.getTypeContext();
        EvaluationContext ec = new EvaluationContext() {
            @Override
            public Primitives getPrimitives() {
                return typeContext.getPrimitives();
            }

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
            public int getDepth() {
                return 0;
            }

            @Override
            public DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
                return property.falseDv;
            }
        };
        context = EvaluationResult.from(ec);
    }

    @Test
    public void testNewObject1() {
        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo arrayListConstructor = arrayList.findConstructor(0);

        // new ArrayList<>()
        ConstructorCall newObject = ConstructorCall.objectCreation(Identifier.constant("cc"),
                arrayListConstructor, arrayList.asParameterizedType(typeContext.typeMap),
                Diamond.YES, List.of());
        LinkedVariables linkedVariables = newObject.linkedVariables(context);
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
        ConstructorCall newObject = ConstructorCall.objectCreation(Identifier.constant("cc"),
                arrayListConstructor, arrayList.asParameterizedType(typeContext.typeMap),
                Diamond.YES, List.of(ve));
        LinkedVariables linkedVariables = newObject.linkedVariables(context);
        assertEquals("v:2", linkedVariables.toString());

        // new ArrayList<>(v).get(0)
        //TypeInfo list = typeContext.getFullyQualified(List.class);
        MethodInfo listGet = arrayList.findUniqueMethod("get", 1);
        assertEquals(MultiLevel.INDEPENDENT_1_DV, listGet.methodAnalysis.get().getProperty(Property.INDEPENDENT));

        MethodCall get0 = new MethodCall(Identifier.constant("mc"), newObject, listGet,
                List.of(newInt(0)));

        LinkedVariables lvGet = get0.linkedVariables(context);
        assertEquals("v:3", lvGet.toString());

        // new ArrayList<>(v).subList(1, 2)
        MethodInfo listSubList = arrayList.findUniqueMethod("subList", 2);
        assertEquals(MultiLevel.DEPENDENT_DV, listSubList.methodAnalysis.get().getProperty(Property.INDEPENDENT));
        MethodCall subList12 = new MethodCall(Identifier.constant("mc"), newObject, listSubList,
                List.of(newInt(1), newInt(2)));

        LinkedVariables lvSubList = subList12.linkedVariables(context);
        assertEquals("v:2", lvSubList.toString());

        // (Collection<Integer>) new ArrayList<>(v)
        Cast collectionIntCast = new Cast(newObject, collectionInteger);
        LinkedVariables lvCast = collectionIntCast.linkedVariables(context);
        assertEquals("v:2", lvCast.toString());
    }

    private ParameterizedType integer() {
        return typeContext.getPrimitives().integerTypeInfo().asSimpleParameterizedType();
    }

    @Test
    public void testModifyingMethod() {
        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);
        // see XML file
        MethodInfo addIndex = arrayList.findUniqueMethod("add", 2);
        assertEquals(DV.TRUE_DV, addIndex.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

        ParameterAnalysis p1 = addIndex.parameterAnalysis(1);
        assertEquals(MultiLevel.INDEPENDENT_1_DV, p1.getProperty(Property.INDEPENDENT));

        ParameterizedType arrayListInteger = new ParameterizedType(arrayList, List.of(integer()));
        Variable v = new LocalVariableReference(new LocalVariable("v", arrayListInteger));
        VariableExpression ve = new VariableExpression(v);

        Variable i = new LocalVariableReference(new LocalVariable("i", integer()));
        VariableExpression vi = new VariableExpression(i);

        // v.add(0, i)
        MethodCall add12 = new MethodCall(Identifier.constant("add12"), ve, addIndex, List.of(newInt(0), vi));
        LinkedVariables lvAdd12 = add12.linkedVariables(context);
        assertTrue(lvAdd12.isEmpty()); // because void method!

        LinkedVariables lvAdd12Scope = add12.linked1VariablesScope(context);
        assertEquals("i:3", lvAdd12Scope.toString());
    }

    @Test
    public void testCollectionsAddAll() {
        TypeInfo collections = typeContext.getFullyQualified(Collections.class);
        MethodInfo addAll = collections.findUniqueMethod("addAll", 2);


        assertEquals("{java.util.Collections.addAll(java.util.Collection<? super T>,T...):0:c=elements:3}",
                addAll.crossLinks(context.getAnalyserContext()).toString());

        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        ParameterizedType collectionInteger = new ParameterizedType(collection, List.of(integer()));

        Variable v = new LocalVariableReference(new LocalVariable("v", collectionInteger));
        VariableExpression ve = new VariableExpression(v);

        Variable i = new LocalVariableReference(new LocalVariable("i", integer()));
        VariableExpression vi = new VariableExpression(i);

        Variable j = new LocalVariableReference(new LocalVariable("j", integer()));
        VariableExpression vj = new VariableExpression(j);

        // Collections.addAll(v, i, j)
        List<Expression> parameterValues = List.of(ve, vi, vj);
        MethodCall methodCall = new MethodCall(Identifier.constant("addAll"),
                new TypeExpression(Identifier.CONSTANT, collectionInteger, Diamond.NO), addAll, parameterValues);
        assertEquals("Collection.addAll(v,i,j)", methodCall.toString());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        methodCall.linksBetweenParameters(builder, context, methodCall.methodInfo, parameterValues,
                List.of(LinkedVariables.of(Map.of(i, LinkedVariables.INDEPENDENT1_DV, j, LinkedVariables.INDEPENDENT1_DV)),
                        LinkedVariables.of(Map.of(v, LinkedVariables.INDEPENDENT1_DV)),
                        LinkedVariables.of(Map.of(v, LinkedVariables.INDEPENDENT1_DV))));
        // v links @Independent1 to i and j
        assertEquals("i:3,j:3",
                builder.build().changeData().get(v).linkedVariables().toString());
    }

    private IntConstant newInt(int i) {
        return new IntConstant(typeContext.getPrimitives(), i);
    }
}
