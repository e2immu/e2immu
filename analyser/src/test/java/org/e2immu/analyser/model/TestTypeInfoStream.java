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

import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.e2immu.analyser.model.TypeInspectionImpl.InspectionState.BY_HAND;

public class TestTypeInfoStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeInfoStream.class);
    private static final String TEST_PACKAGE = "org.e2immu.analyser.test";
    private static final String GENERATED_PACKAGE = "org.e2immu.analyser.generatedannotation";
    public static final String JAVA_UTIL = "java.util";

    final TypeInfo genericContainer = TypeInfo.createFqnOrPackageNameDotSimpleName("org.e2immu.analyser.model",
            "GenericContainer");

    final TypeInfo typeInfo = TypeInfo.createFqnOrPackageNameDotSimpleName("org.e2immu.analyser.model",
            "TestTypeInfoStream");
    final TypeInfo loggerTypeInfo = TypeInfo.createFqnOrPackageNameDotSimpleName("org.slf4j", "Logger");
    final TypeInfo containerTypeInfo = TypeInfo.createFqnOrPackageNameDotSimpleName("org.e2immu.analyser.model.TestTypeInfoStream", "Container");
    final FieldInfo logger = new FieldInfo(loggerTypeInfo, "LOGGER", typeInfo);

    final Primitives primitives = new Primitives();

    TypeParameter typeParameterT = new TypeParameter(containerTypeInfo, "T", 0);
    ParameterizedType typeT = new ParameterizedType(typeParameterT, 0, ParameterizedType.WildCard.NONE);

    final MethodInfo genericContainerPutMethod = new MethodInfo(containerTypeInfo, "put",
            List.of(),
            typeT, false);

    final MethodInfo emptyConstructor = new MethodInfo(typeInfo, List.of());
    final MethodInfo toStringMethodInfo = new MethodInfo(typeInfo, "toString", List.of(),
            primitives.stringParameterizedType, false);

    final MethodInfo intSum = new MethodInfo(typeInfo, "sum", List.of(), primitives.intParameterizedType, true);

    @Test
    public void test() {
        final TypeInfo hashMap = TypeInfo.createFqnOrPackageNameDotSimpleName(JAVA_UTIL, "HashMap");
        final ParameterizedType hashMapParameterizedType = hashMap.asParameterizedType();
        final TypeInfo map = TypeInfo.createFqnOrPackageNameDotSimpleName(JAVA_UTIL, "Map");
        final TypeInfo mapEntry = TypeInfo.createFqnOrPackageNameDotSimpleName(JAVA_UTIL, "Entry");

        logger.fieldInspection.set(new FieldInspectionImpl.Builder()
                .addModifier(FieldModifier.PRIVATE)
                .addModifier(FieldModifier.STATIC)
                .addModifier(FieldModifier.FINAL)
                .build());
        LocalVariable mapLocalVariable = new LocalVariable.LocalVariableBuilder()
                .setName("map")
                .setParameterizedType(new ParameterizedType(map, List.of(primitives.stringParameterizedType, typeT)))
                .build();
        MethodInfo hashMapConstructor = new MethodInfo(hashMap, List.of());
        Expression creationExpression = new NewObject(hashMapConstructor, hashMapParameterizedType, List.of(), null);
        ParameterInfo p0 = new ParameterInfo(hashMapConstructor, typeT, "value", 0);
        genericContainerPutMethod.methodInspection.set(new MethodInspectionImpl.Builder(genericContainerPutMethod)
                .setReturnType(typeT)
                .addParameterFluently(p0)
                //.addAnnotation(new AnnotationExpression(jdk.override))
                //.addExceptionType(new ParameterizedType(jdk.ioException))
                .setInspectedBlock(
                        new Block.BlockBuilder()
                                .addStatement(
                                        new ExpressionAsStatement(
                                                new LocalVariableCreation(primitives,
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
                                                new VariableExpression(new LocalVariableReference(mapLocalVariable, List.of(creationExpression))),
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
                .build());

        FieldInfo intFieldInContainer = new FieldInfo(primitives.intTypeInfo, "i", containerTypeInfo);
        intFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder()
                .setInspectedInitialiserExpression(new IntConstant(primitives, 27))
                .build());

        FieldInfo doubleFieldInContainer = new FieldInfo(primitives.doubleTypeInfo, "d", containerTypeInfo);
        doubleFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder()
                .addModifier(FieldModifier.PRIVATE).build());

        FieldInfo stringFieldInContainer = new FieldInfo(primitives.stringTypeInfo, "s", containerTypeInfo);
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
                .setEnclosingType(typeInfo)
                .addTypeModifier(TypeModifier.STATIC)
                .addField(intFieldInContainer)
                .addField(stringFieldInContainer)
                .addField(doubleFieldInContainer)
                .addField(tInContainer)
                .addMethod(genericContainerPutMethod)
                .addTypeParameter(typeParameterT)
                .addInterfaceImplemented(new ParameterizedType(genericContainer, List.of(typeT)))
                .build();
        containerTypeInfo.typeInspection.set(containerTypeInspection);

        TypeInfo commutative = TypeInfo.createFqnOrPackageNameDotSimpleName(GENERATED_PACKAGE, "Commutative");
        TypeInfo testEquivalent = TypeInfo.createFqnOrPackageNameDotSimpleName(TEST_PACKAGE, "TestEquivalent");
        MethodInfo referenceMethodInfo = new MethodInfo(testEquivalent, "reference", List.of(),
                primitives.stringParameterizedType, false);
        referenceMethodInfo.methodInspection.set(new MethodInspectionImpl.Builder(referenceMethodInfo)
                .setReturnType(primitives.stringTypeInfo)
                .setInspectedBlock(new Block.BlockBuilder().build())
                .build());
        testEquivalent.typeInspection.set(new TypeInspectionImpl.Builder(testEquivalent, BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .setPackageName(TEST_PACKAGE)
                .setTypeNature(TypeNature.ANNOTATION)
                .addMethod(referenceMethodInfo)
                .build());

        toStringMethodInfo.methodInspection.set(new MethodInspectionImpl.Builder(toStringMethodInfo)
                .addModifier(MethodModifier.PUBLIC)
                .setReturnType(primitives.stringTypeInfo)
                .build());
        emptyConstructor.methodInspection.set(new MethodInspectionImpl.Builder(emptyConstructor)
                .build());
        ParameterInfo x = new ParameterInfo(intSum, primitives.intTypeInfo, "x", 0);
        ParameterInfo y = new ParameterInfo(intSum, primitives.intTypeInfo, "y", 1);

        TypeInfo exception = TypeInfo.createFqnOrPackageNameDotSimpleName(GENERATED_PACKAGE, "MyException");
        ParameterizedType exceptionType = exception.asParameterizedType();

        intSum.methodInspection.set(new MethodInspectionImpl.Builder(intSum)
                .addModifier(MethodModifier.PUBLIC)
                .addExceptionType(exceptionType)
                .setReturnType(primitives.intTypeInfo)
                .addParameterFluently(x)
                .addParameterFluently(y)
                .addAnnotation(new AnnotationExpressionImpl(commutative, List.of()))
                .addAnnotation(new AnnotationExpressionImpl(testEquivalent, List.of(new StringConstant(primitives, "hello"))))
                .setInspectedBlock(
                        new Block.BlockBuilder().addStatement(
                                new ReturnStatement(false,
                                        new BinaryOperator(
                                                new VariableExpression(x), primitives.plusOperatorInt, new VariableExpression(y), BinaryOperator.ADDITIVE_PRECEDENCE
                                        ))
                        ).build())
                .build());

        TypeInspection typeInspection = new TypeInspectionImpl.Builder(typeInfo, BY_HAND)
                .addTypeModifier(TypeModifier.PUBLIC)
                .setParentClass(primitives.objectParameterizedType)
                .setPackageName("org.e2immu.analyser.model")
                .addField(logger)
                .addSubType(containerTypeInfo)
                .addConstructor(emptyConstructor)
                .addMethod(toStringMethodInfo)
                .addMethod(intSum)
                .build();
        typeInfo.typeInspection.set(typeInspection);

        String stream = typeInfo.stream();
        LOGGER.info("stream is\n\n{}", stream);

        Assert.assertTrue(stream.contains("import org.slf4j.Logger"));
        Assert.assertTrue(stream.contains("private static final Logger LOGGER"));
        Assert.assertTrue(stream.contains("public class TestTypeInfoStream"));
        Assert.assertTrue(stream.contains("static class Container<T> implements GenericContainer<T>"));
        Assert.assertTrue(stream.contains("int i"));
        Assert.assertTrue(stream.contains("private double d"));
        Assert.assertTrue(stream.contains("final String s"));
        Assert.assertFalse(stream.contains("import java.lang"));
        Assert.assertTrue(stream.contains("  TestTypeInfoStream() {"));
        Assert.assertTrue(stream.contains("public static int sum(int x, int y) throws MyException {"));
        Assert.assertTrue(stream.contains("T put(T value)"));// throws IOException {"));
    }
}
