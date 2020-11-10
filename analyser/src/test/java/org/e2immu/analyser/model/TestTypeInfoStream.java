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

public class TestTypeInfoStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeInfoStream.class);
    private static final String TEST_PACKAGE = "com.wrc.equivalent.testannotation";
    private static final String GENERATED_PACKAGE = "com.wrc.equivalent.generatedannotation";
    public static final String JAVA_UTIL = "java.util";

    final TypeInfo genericContainer = new TypeInfo("com.wrc.equivalent.model",
            "GenericContainer");

    final TypeInfo typeInfo = new TypeInfo("com.wrc.equivalent.model",
            "TestTypeInfoStream");
    final TypeInfo loggerTypeInfo = new TypeInfo("org.slf4j", "Logger");
    final TypeInfo containerTypeInfo = new TypeInfo("com.wrc.equivalent.model.TestTypeInfoStream.Container");
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
    public void test2() {
        final TypeInfo hashMap = new TypeInfo(JAVA_UTIL, "HashMap");
        final ParameterizedType hashMapParameterizedType = hashMap.asParameterizedType();
        final TypeInfo map = new TypeInfo(JAVA_UTIL, "Map");
        final TypeInfo mapEntry = new TypeInfo(JAVA_UTIL, "Entry");

        logger.fieldInspection.set(new FieldInspection.FieldInspectionBuilder()
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
        p0.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
        genericContainerPutMethod.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .setReturnType(typeT)
                .addParameter(p0)
                //.addAnnotation(new AnnotationExpression(jdk.override))
                //.addExceptionType(new ParameterizedType(jdk.ioException))
                .setBlock(
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
                .build(genericContainerPutMethod));

        FieldInfo intFieldInContainer = new FieldInfo(primitives.intTypeInfo, "i", containerTypeInfo);
        intFieldInContainer.fieldInspection.set(new FieldInspection.FieldInspectionBuilder()
                .setInitializer(new IntConstant(primitives, 27))
                .build());

        FieldInfo doubleFieldInContainer = new FieldInfo(primitives.doubleTypeInfo, "d", containerTypeInfo);
        doubleFieldInContainer.fieldInspection.set(new FieldInspection.FieldInspectionBuilder()
                .addModifier(FieldModifier.PRIVATE).build());

        FieldInfo stringFieldInContainer = new FieldInfo(primitives.stringTypeInfo, "s", containerTypeInfo);
        stringFieldInContainer.fieldInspection.set(new FieldInspection.FieldInspectionBuilder()
                .addModifier(FieldModifier.FINAL)
                .setInitializer(new StringConstant(primitives, "first value"))
                .build());

        FieldInfo tInContainer = new FieldInfo(typeT, "t", containerTypeInfo);
        tInContainer.fieldInspection.set(new FieldInspection.FieldInspectionBuilder()
                .setInitializer(new NullConstant())
                .build());

        TypeInspection containerTypeInspection = new TypeInspection.TypeInspectionBuilder()
                .setTypeNature(TypeNature.CLASS)
                .setEnclosingType(typeInfo)
                .addTypeModifier(TypeModifier.STATIC)
                .addField(intFieldInContainer)
                .addField(stringFieldInContainer)
                .addField(doubleFieldInContainer)
                .addField(tInContainer)
                .addMethod(genericContainerPutMethod)
                .addTypeParameter(typeParameterT)
                .addInterfaceImplemented(new ParameterizedType(genericContainer, List.of(typeT)))
                .build(true, containerTypeInfo);
        containerTypeInfo.typeInspection.set(containerTypeInspection);

        TypeInfo commutative = new TypeInfo(GENERATED_PACKAGE, "Commutative");
        TypeInfo testEquivalent = new TypeInfo(TEST_PACKAGE, "TestEquivalent");
        MethodInfo referenceMethodInfo = new MethodInfo(testEquivalent, "reference", List.of(),
                primitives.stringParameterizedType, false);
        referenceMethodInfo.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .setReturnType(primitives.stringTypeInfo)
                .setBlock(new Block.BlockBuilder().build())
                .build(referenceMethodInfo));
        testEquivalent.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                .setPackageName(TEST_PACKAGE)
                .setTypeNature(TypeNature.ANNOTATION)
                .addMethod(referenceMethodInfo)
                .build(true, testEquivalent));

        toStringMethodInfo.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .addModifier(MethodModifier.PUBLIC)
                .setReturnType(primitives.stringTypeInfo)
                .build(toStringMethodInfo));
        emptyConstructor.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .build(emptyConstructor));
        ParameterInfo x = new ParameterInfo(intSum, primitives.intTypeInfo, "x", 0);
        x.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
        ParameterInfo y = new ParameterInfo(intSum, primitives.intTypeInfo, "y", 1);
        y.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
        intSum.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .addModifier(MethodModifier.PUBLIC)
                .setReturnType(primitives.intTypeInfo)
                .addParameter(x)
                .addParameter(y)
                .addAnnotation(AnnotationExpression.fromAnalyserExpressions(commutative, List.of()))
                .addAnnotation(AnnotationExpression.fromAnalyserExpressions(testEquivalent, List.of(new StringConstant(primitives, "hello"))))
                .setBlock(
                        new Block.BlockBuilder().addStatement(
                                new ReturnStatement(false,
                                        new BinaryOperator(
                                                new VariableExpression(x), primitives.plusOperatorInt, new VariableExpression(y), BinaryOperator.ADDITIVE_PRECEDENCE
                                        ))
                        ).build())
                .build(intSum));

        TypeInspection typeInspection = new TypeInspection.TypeInspectionBuilder()
                .addTypeModifier(TypeModifier.PUBLIC)
                .setPackageName("com.wrc.equivalent.model")
                .addField(logger)
                .addSubType(containerTypeInfo)
                .addConstructor(emptyConstructor)
                .addMethod(toStringMethodInfo)
                .addMethod(intSum)
                .build(true, typeInfo);
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
        Assert.assertTrue(stream.contains("public static int sum(int x, int y) {"));
        Assert.assertTrue(stream.contains("T put(T value)"));// throws IOException {"));
    }
}
