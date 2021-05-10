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

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTypeInfoStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeInfoStream.class);
    private static final String TEST_PACKAGE = "org.e2immu.analyser.test";
    private static final String GENERATED_PACKAGE = "org.e2immu.analyser.generatedannotation";
    public static final String JAVA_UTIL = "java.util";
    public static final String MODEL = "org.e2immu.analyser.model";

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate();
    }

    @Test
    public void test() {
        Primitives primitives = new Primitives();
        primitives.objectTypeInfo.typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo, BY_HAND).build());

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
        emptyContainerConstructor.methodResolution.set(new MethodResolution.Builder().build());

        MethodInfo toStringMethodInfo = new MethodInspectionImpl.Builder(testTypeInfo, "toString")
                .addModifier(MethodModifier.PUBLIC)
                .setReturnType(primitives.stringParameterizedType).build(IP).getMethodInfo();
        toStringMethodInfo.methodResolution.set(new MethodResolution.Builder().build());

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
        LocalVariable mapLocalVariable = new LocalVariable.Builder()
                .setOwningType(testTypeInfo)
                .setName("map").setSimpleName("map")
                .setParameterizedType(new ParameterizedType(map, List.of(primitives.stringParameterizedType, typeT)))
                .build();
        MethodInfo hashMapConstructor = new MethodInspectionImpl.Builder(hashMap).build(IP).getMethodInfo();
        Expression creationExpression = NewObject.objectCreation("-", primitives, hashMapConstructor,
                hashMapParameterizedType, Diamond.NO, List.of());
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
                                                new LocalVariableCreation(inspectionProvider,
                                                        new LocalVariable.Builder()
                                                                .setOwningType(testTypeInfo)
                                                                .setName("entry").setSimpleName("entry")
                                                                .setParameterizedType(new ParameterizedType(mapEntry, List.of(primitives.stringParameterizedType, typeT)))
                                                                .build()),
                                                new VariableExpression(new LocalVariableReference(mapLocalVariable, creationExpression)),
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
        put.methodResolution.set(new MethodResolution.Builder().build());

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
                                new ReturnStatement(new BinaryOperator(primitives,
                                        new VariableExpression(x), primitives.plusOperatorInt, new VariableExpression(y), Precedence.ADDITIVE
                                ))
                        ).build())
                .build(IP).getMethodInfo();
        intSum.methodResolution.set(new MethodResolution.Builder().build());
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

        assertTrue(stream.contains("import org.slf4j.Logger"));
        assertTrue(stream.contains("private static final Logger LOGGER"));
        assertTrue(stream.contains("public class TestTypeInfoStream"));
        assertTrue(stream.contains("static class Container<T> implements GenericContainer<T>"));
        assertTrue(stream.contains("int i"));
        assertTrue(stream.contains("private double d"));
        assertTrue(stream.contains("final String s"));
        assertFalse(stream.contains("import java.lang"));
        assertTrue(stream.contains("TestTypeInfoStream(){"));
        assertTrue(stream.contains("public static int sum(int x,int y) throws MyException{"));
        assertTrue(stream.contains("T put(T value)"));
    }
}
