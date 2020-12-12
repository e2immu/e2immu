/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;

public class TestTypeInfoStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeInfoStream.class);
    private static final String TEST_PACKAGE = "org.e2immu.analyser.test";
    private static final String GENERATED_PACKAGE = "org.e2immu.analyser.generatedannotation";
    public static final String JAVA_UTIL = "java.util";
    public static final String MODEL = "org.e2immu.analyser.model";

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate();
    }

    @Test
    public void test() {
        Primitives primitives = new Primitives();
        InspectionProvider IP = InspectionProvider.DEFAULT;
        TypeInfo genericContainer = new TypeInfo(MODEL, "GenericContainer");
        TypeParameter genericContainerTypeParameterT = new TypeParameterImpl(genericContainer, "T", 0);
        ParameterizedType genericContainerT = new ParameterizedType(genericContainerTypeParameterT, 0, ParameterizedType.WildCard.NONE);

        genericContainer.typeInspection.set(new TypeInspectionImpl.Builder(genericContainer, BY_HAND)
                .addTypeParameter(genericContainerTypeParameterT)
                .setParentClass(primitives.objectParameterizedType).build());

        TypeInfo testTypeInfo = new TypeInfo(MODEL, "TestTypeInfoStream");
        TypeInfo loggerTypeInfo = new TypeInfo("org.slf4j", "Logger");
        TypeInfo containerTypeInfo = new TypeInfo(testTypeInfo, "Container");

        TypeParameter typeParameterT = new TypeParameterImpl(containerTypeInfo, "T", 0);

        FieldInfo logger = new FieldInfo(loggerTypeInfo.asSimpleParameterizedType(), "LOGGER", testTypeInfo);

        ParameterizedType typeT = new ParameterizedType(typeParameterT, 0, ParameterizedType.WildCard.NONE);

        MethodInfo emptyTestConstructor = new MethodInspectionImpl.Builder(testTypeInfo).build(IP).getMethodInfo();

        MethodInfo emptyContainerConstructor = new MethodInspectionImpl.Builder(containerTypeInfo).build(IP).getMethodInfo();
        MethodInfo toStringMethodInfo = new MethodInspectionImpl.Builder(testTypeInfo, "toString")
                .addModifier(MethodModifier.PUBLIC)
                .setReturnType(primitives.stringParameterizedType).build(IP).getMethodInfo();

        TypeInfo hashMap = new TypeInfo(JAVA_UTIL, "HashMap");
        TypeInfo exception = new TypeInfo(GENERATED_PACKAGE, "MyException");

        TypeInspectionImpl.Builder hashMapInspection = new TypeInspectionImpl.Builder(hashMap, BY_HAND)
                .setParentClass(primitives.objectParameterizedType);
        TypeInspectionImpl.Builder exceptionInspection = new TypeInspectionImpl.Builder(exception, BY_HAND)
                .setParentClass(primitives.objectParameterizedType);

        InspectionProvider inspectionProvider = new InspectionProvider() {
            @Override
            public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeInspection getTypeInspection(TypeInfo typeInfo) {
                if (typeInfo == hashMap) return hashMapInspection;
                if (typeInfo == exception) return exceptionInspection;
                throw new UnsupportedOperationException("Cannot find inspection for " + typeInfo.fullyQualifiedName);
            }

            @Override
            public MethodInspection getMethodInspection(MethodInfo methodInfo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Primitives getPrimitives() {
                return primitives;
            }
        };
        ParameterizedType hashMapParameterizedType = hashMap.asParameterizedType(inspectionProvider);
        TypeInfo map = new TypeInfo(JAVA_UTIL, "Map");
        map.typeInspection.set(new TypeInspectionImpl.Builder(map, BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .setTypeNature(TypeNature.INTERFACE).build()
        );
        TypeInfo mapEntry = new TypeInfo(map, "Entry");
        mapEntry.typeInspection.set(new TypeInspectionImpl.Builder(mapEntry, BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .setParentClass(primitives.objectParameterizedType)
                .build());

        logger.fieldInspection.set(new FieldInspectionImpl.Builder()
                .addModifier(FieldModifier.PRIVATE)
                .addModifier(FieldModifier.STATIC)
                .addModifier(FieldModifier.FINAL)
                .build());
        LocalVariable mapLocalVariable = new LocalVariable.LocalVariableBuilder()
                .setName("map")
                .setParameterizedType(new ParameterizedType(map, List.of(primitives.stringParameterizedType, typeT)))
                .build();
        MethodInfo hashMapConstructor = new MethodInspectionImpl.Builder(hashMap).build(IP).getMethodInfo();
        Expression creationExpression = new NewObject(hashMapConstructor,
                hashMapParameterizedType, List.of(), new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
        ParameterInspectionImpl.Builder p0 = new ParameterInspectionImpl.Builder(typeT, "value", 0);
        MethodInfo put = new MethodInspectionImpl.Builder(testTypeInfo, "put")
                .setReturnType(typeT)
                .addParameter(p0)
                //.addAnnotation(new AnnotationExpression(jdk.override))
                //.addExceptionType(new ParameterizedType(jdk.ioException))
                .setInspectedBlock(
                        new Block.BlockBuilder()
                                .addStatement(
                                        new ExpressionAsStatement(
                                                new LocalVariableCreation(inspectionProvider,
                                                        mapLocalVariable,
                                                        creationExpression)
                                        )
                                )
                                .addStatement(
                                        new ForEachStatement(null,
                                                new LocalVariable.LocalVariableBuilder()
                                                        .setName("entry")
                                                        .setParameterizedType(new ParameterizedType(mapEntry, List.of(primitives.stringParameterizedType, typeT)))
                                                        .build(),
                                                new VariableExpression(new LocalVariableReference(inspectionProvider,
                                                        mapLocalVariable, List.of(creationExpression))),
                                                new Block.BlockBuilder()
                                                        .addStatement(new IfElseStatement(
                                                                new BooleanConstant(primitives, true),
                                                                new Block.BlockBuilder().build(),
                                                                Block.EMPTY_BLOCK
                                                        ))
                                                        .build()
                                        )
                                )
                                .build())
                .build(IP).getMethodInfo();

        FieldInfo intFieldInContainer = new FieldInfo(primitives.intParameterizedType, "i", containerTypeInfo);
        intFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder()
                .setInspectedInitialiserExpression(new IntConstant(primitives, 27))
                .build());

        FieldInfo doubleFieldInContainer = new FieldInfo(primitives.doubleParameterizedType, "d", containerTypeInfo);
        doubleFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder()
                .addModifier(FieldModifier.PRIVATE).build());

        FieldInfo stringFieldInContainer = new FieldInfo(primitives.stringParameterizedType, "s", containerTypeInfo);
        stringFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder()
                .addModifier(FieldModifier.FINAL)
                .setInspectedInitialiserExpression(new StringConstant(primitives, "first value"))
                .build());

        FieldInfo tInContainer = new FieldInfo(typeT, "t", containerTypeInfo);
        tInContainer.fieldInspection.set(new FieldInspectionImpl.Builder()
                .setInspectedInitialiserExpression(new NullConstant())
                .build());

        TypeInspection containerTypeInspection = new TypeInspectionImpl.Builder(containerTypeInfo, BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(primitives.objectParameterizedType)
                .addTypeModifier(TypeModifier.STATIC)
                .addField(intFieldInContainer)
                .addField(stringFieldInContainer)
                .addField(doubleFieldInContainer)
                .addField(tInContainer)
                .addMethod(put)
                .addMethod(emptyContainerConstructor)
                .addTypeParameter(typeParameterT)
                .addInterfaceImplemented(new ParameterizedType(genericContainer, List.of(genericContainerT)))
                .build();
        containerTypeInfo.typeInspection.set(containerTypeInspection);

        TypeInfo commutative = new TypeInfo(GENERATED_PACKAGE, "Commutative");
        TypeInfo testEquivalent = new TypeInfo(TEST_PACKAGE, "TestEquivalent");
        MethodInfo referenceMethodInfo = new MethodInspectionImpl.Builder(testEquivalent, "reference")
                .setReturnType(primitives.stringParameterizedType)
                .setInspectedBlock(new Block.BlockBuilder().build())
                .build(IP).getMethodInfo();
        testEquivalent.typeInspection.set(new TypeInspectionImpl.Builder(testEquivalent, BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .setTypeNature(TypeNature.ANNOTATION)
                .addMethod(referenceMethodInfo)
                .build());

        MethodInspectionImpl.Builder intSumBuilder = new MethodInspectionImpl.Builder(testTypeInfo, "sum")
                .setReturnType(primitives.intParameterizedType).setStatic(true);

        ParameterInspectionImpl.Builder xb = new ParameterInspectionImpl.Builder(primitives.intParameterizedType, "x", 0);
        ParameterInspectionImpl.Builder yb = new ParameterInspectionImpl.Builder(primitives.intParameterizedType, "y", 1);

        ParameterizedType exceptionType = exception.asParameterizedType(inspectionProvider);

        intSumBuilder
                .addModifier(MethodModifier.PUBLIC)
                .addExceptionType(exceptionType)
                .setReturnType(primitives.intParameterizedType)
                .addParameter(xb)
                .addParameter(yb);
        intSumBuilder.readyToComputeFQN(IP);
        ParameterInfo x = intSumBuilder.getParameters().get(0);
        ParameterInfo y = intSumBuilder.getParameters().get(0);
        MethodInfo intSum = intSumBuilder
                .addAnnotation(new AnnotationExpressionImpl(commutative, List.of()))
                .addAnnotation(new AnnotationExpressionImpl(testEquivalent, List.of(new StringConstant(primitives, "hello"))))
                .setInspectedBlock(
                        new Block.BlockBuilder().addStatement(
                                new ReturnStatement(false,
                                        new BinaryOperator(primitives,
                                                new VariableExpression(x), primitives.plusOperatorInt, new VariableExpression(y), Precedence.ADDITIVE
                                        ))
                        ).build())
                .build(IP).getMethodInfo();

        TypeInspection testTypeInspection = new TypeInspectionImpl.Builder(testTypeInfo, BY_HAND)
                .addTypeModifier(TypeModifier.PUBLIC)
                .setParentClass(primitives.objectParameterizedType)
                .addField(logger)
                .addSubType(containerTypeInfo)
                .addConstructor(emptyTestConstructor)
                .addMethod(toStringMethodInfo)
                .addMethod(intSum)
                .build();
        testTypeInfo.typeInspection.set(testTypeInspection);

        String stream = testTypeInfo.output().toString();
        LOGGER.info("stream is\n\n{}", stream);

        Assert.assertTrue(stream.contains("import org.slf4j.Logger"));
        Assert.assertTrue(stream.contains("private static final Logger LOGGER"));
        Assert.assertTrue(stream.contains("public class TestTypeInfoStream"));
        Assert.assertTrue(stream.contains("static class Container<T> implements GenericContainer<T>"));
        Assert.assertTrue(stream.contains("int i"));
        Assert.assertTrue(stream.contains("private double d"));
        Assert.assertTrue(stream.contains("final String s"));
        Assert.assertFalse(stream.contains("import java.lang"));
        Assert.assertTrue(stream.contains("TestTypeInfoStream(){"));
        Assert.assertTrue(stream.contains("public static int sum(int x,int y) throws MyException{"));
        Assert.assertTrue(stream.contains("T put(T value)"));
    }
}
