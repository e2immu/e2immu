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

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.e2immu.analyser.inspector.InspectionState.BY_HAND;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTypeInfoStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeInfoStream.class);
    private static final String TEST_PACKAGE = "org.e2immu.analyser.test";
    private static final String GENERATED_PACKAGE = "org.e2immu.analyser.generatedannotation";
    public static final String JAVA_UTIL = "java.util";
    public static final String MODEL = "org.e2immu.analyser.model";

    private static Identifier newId() {
        return Identifier.generate("test");
    }

    @Test
    public void test() {
        Primitives primitives = new PrimitivesImpl();
        primitives.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));

        InspectionProvider IP = InspectionProvider.DEFAULT;
        TypeInfo genericContainer = new TypeInfo(MODEL, "GenericContainer");
        TypeParameter genericContainerTypeParameterT = new TypeParameterImpl(genericContainer, "T", 0)
                .noTypeBounds();
        ParameterizedType genericContainerT = new ParameterizedType(genericContainerTypeParameterT, 0, ParameterizedType.WildCard.NONE);

        genericContainer.typeInspection.set(new TypeInspectionImpl.Builder(genericContainer, BY_HAND)
                .addTypeParameter(genericContainerTypeParameterT)
                .setAccess(Inspection.Access.PUBLIC)
                .noParent(primitives).build(null));

        TypeInfo testTypeInfo = new TypeInfo(MODEL, "TestTypeInfoStream");
        TypeInfo loggerTypeInfo = new TypeInfo("org.slf4j", "Logger");
        TypeInfo containerTypeInfo = new TypeInfo(testTypeInfo, "Container");

        TypeParameter typeParameterT = new TypeParameterImpl(containerTypeInfo, "T", 0).noTypeBounds();

        FieldInfo logger = new FieldInfo(newId(),
                loggerTypeInfo.asSimpleParameterizedType(), "LOGGER", testTypeInfo);

        ParameterizedType typeT = new ParameterizedType(typeParameterT, 0, ParameterizedType.WildCard.NONE);

        MethodInfo emptyTestConstructor = new MethodInspectionImpl.Builder(testTypeInfo)
                .setAccess(Inspection.Access.PUBLIC)
                .build(IP).getMethodInfo();

        MethodInfo emptyContainerConstructor = new MethodInspectionImpl.Builder(containerTypeInfo)
                .setAccess(Inspection.Access.PACKAGE)
                .build(IP).getMethodInfo();
        emptyContainerConstructor.methodResolution.set(new MethodResolution.Builder().build());

        MethodInfo toStringMethodInfo = new MethodInspectionImpl.Builder(testTypeInfo, "toString")
                .setAccess(Inspection.Access.PUBLIC)
                .setReturnType(primitives.stringParameterizedType()).build(IP).getMethodInfo();
        toStringMethodInfo.methodResolution.set(new MethodResolution.Builder().build());

        TypeInfo hashMap = new TypeInfo(JAVA_UTIL, "HashMap");
        TypeInfo exception = new TypeInfo(GENERATED_PACKAGE, "MyException");

        TypeInspection.Builder hashMapInspection = new TypeInspectionImpl.Builder(hashMap, BY_HAND)
                .noParent(primitives);
        TypeInspection.Builder exceptionInspection = new TypeInspectionImpl.Builder(exception, BY_HAND)
                .noParent(primitives);

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
                .noParent(primitives)
                .setTypeNature(TypeNature.INTERFACE)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null)
        );
        TypeInfo mapEntry = new TypeInfo(map, "Entry");
        mapEntry.typeInspection.set(new TypeInspectionImpl.Builder(mapEntry, BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .noParent(primitives)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));

        logger.fieldInspection.set(new FieldInspectionImpl.Builder(logger)
                .addModifier(FieldModifier.PRIVATE)
                .addModifier(FieldModifier.STATIC)
                .addModifier(FieldModifier.FINAL)
                .setAccess(Inspection.Access.PRIVATE)
                .build(null));
        LocalVariable mapLocalVariable = new LocalVariable.Builder()
                .setOwningType(testTypeInfo)
                .setName("map")
                .setParameterizedType(new ParameterizedType(map, List.of(primitives.stringParameterizedType(), typeT)))
                .build();
        MethodInfo hashMapConstructor = new MethodInspectionImpl.Builder(hashMap)
                .setAccess(Inspection.Access.PUBLIC)
                .build(IP).getMethodInfo();
        Expression creationExpression = ConstructorCall.objectCreation(newId(), hashMapConstructor,
                hashMapParameterizedType, Diamond.NO, List.of());
        ParameterInspectionImpl.Builder p0 = new ParameterInspectionImpl.Builder(newId(), typeT, "value", 0);
        LocalVariableCreation lvc = new LocalVariableCreation(newId(),
                new LocalVariableReference(mapLocalVariable, creationExpression));
        LocalVariable lv = new LocalVariable.Builder()
                .setOwningType(testTypeInfo)
                .setName("entry")
                .setParameterizedType(new ParameterizedType(mapEntry, List.of(primitives.stringParameterizedType(), typeT)))
                .build();
        MethodInfo put = new MethodInspectionImpl.Builder(testTypeInfo, "put")
                .setReturnType(typeT)
                .addParameter(p0)
                .setAccess(Inspection.Access.PUBLIC)
                //.addAnnotation(new AnnotationExpression(jdk.override))
                //.addExceptionType(new ParameterizedType(jdk.ioException))
                .setInspectedBlock(
                        new Block.BlockBuilder(newId())
                                .addStatement(new ExpressionAsStatement(newId(), lvc))
                                .addStatement(
                                        new ForEachStatement(newId(), null,
                                                new LocalVariableCreation(Identifier.generate("lvc"), lv),
                                                new VariableExpression(new LocalVariableReference(mapLocalVariable, creationExpression)),
                                                null,
                                                new Block.BlockBuilder(newId())
                                                        .addStatement(new IfElseStatement(newId(),
                                                                new BooleanConstant(primitives, true),
                                                                new Block.BlockBuilder(newId()).build(),
                                                                Block.emptyBlock(newId()),
                                                                null
                                                        ))
                                                        .build(),
                                                null
                                        )
                                )
                                .build())
                .build(IP).getMethodInfo();
        put.methodResolution.set(new MethodResolution.Builder().build());

        FieldInfo intFieldInContainer = new FieldInfo(newId(), primitives.intParameterizedType(), "i", containerTypeInfo);
        intFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder(intFieldInContainer)
                .setInspectedInitialiserExpression(new IntConstant(primitives, 27))
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));

        FieldInfo doubleFieldInContainer = new FieldInfo(newId(), primitives.doubleParameterizedType(), "d", containerTypeInfo);
        doubleFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder(doubleFieldInContainer)
                .addModifier(FieldModifier.PRIVATE)
                .setAccess(Inspection.Access.PRIVATE)
                .build(null));

        FieldInfo stringFieldInContainer = new FieldInfo(newId(), primitives.stringParameterizedType(), "s", containerTypeInfo);
        stringFieldInContainer.fieldInspection.set(new FieldInspectionImpl.Builder(stringFieldInContainer)
                .addModifier(FieldModifier.FINAL)
                .setAccess(Inspection.Access.PROTECTED)
                .setInspectedInitialiserExpression(new StringConstant(primitives, "first value"))
                .build(null));

        FieldInfo tInContainer = new FieldInfo(newId(), typeT, "t", containerTypeInfo);
        tInContainer.fieldInspection.set(new FieldInspectionImpl.Builder(tInContainer)
                .setAccess(Inspection.Access.PRIVATE)
                .setInspectedInitialiserExpression(NullConstant.NULL_CONSTANT)
                .build(null));

        TypeInspection containerTypeInspection = new TypeInspectionImpl.Builder(containerTypeInfo, BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .noParent(primitives)
                .addTypeModifier(TypeModifier.STATIC)
                .addField(intFieldInContainer)
                .addField(stringFieldInContainer)
                .addField(doubleFieldInContainer)
                .addField(tInContainer)
                .addMethod(put)
                .addMethod(emptyContainerConstructor)
                .addTypeParameter(typeParameterT)
                .addInterfaceImplemented(new ParameterizedType(genericContainer, List.of(genericContainerT)))
                .setAccess(Inspection.Access.PUBLIC)
                .build(null);
        containerTypeInfo.typeInspection.set(containerTypeInspection);

        TypeInfo commutative = new TypeInfo(GENERATED_PACKAGE, "Commutative");
        TypeInfo testEquivalent = new TypeInfo(TEST_PACKAGE, "TestEquivalent");
        MethodInfo referenceMethodInfo = new MethodInspectionImpl.Builder(testEquivalent, "reference")
                .setReturnType(primitives.stringParameterizedType())
                .setInspectedBlock(new Block.BlockBuilder(newId()).build())
                .setAccess(Inspection.Access.PACKAGE)
                .build(IP).getMethodInfo();
        testEquivalent.typeInspection.set(new TypeInspectionImpl.Builder(testEquivalent, BY_HAND)
                .noParent(primitives)
                .setTypeNature(TypeNature.ANNOTATION)
                .addMethod(referenceMethodInfo)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));

        MethodInspection.Builder intSumBuilder = new MethodInspectionImpl.Builder(testTypeInfo, "sum")
                .setReturnType(primitives.intParameterizedType()).setStatic(true);

        ParameterInspectionImpl.Builder xb = new ParameterInspectionImpl.Builder(newId(),
                primitives.intParameterizedType(), "x", 0);
        ParameterInspectionImpl.Builder yb = new ParameterInspectionImpl.Builder(newId(),
                primitives.intParameterizedType(), "y", 1);

        ParameterizedType exceptionType = exception.asParameterizedType(inspectionProvider);

        intSumBuilder
                .addModifier(MethodModifier.PUBLIC)
                .addExceptionType(exceptionType)
                .setReturnType(primitives.intParameterizedType())
                .addParameter(xb)
                .addParameter(yb);
        intSumBuilder.readyToComputeFQN(IP);
        ParameterInfo x = intSumBuilder.getParameters().get(0);
        ParameterInfo y = intSumBuilder.getParameters().get(0);
        MethodInfo intSum = intSumBuilder
                .addAnnotation(new AnnotationExpressionImpl(commutative, List.of()))
                .addAnnotation(new AnnotationExpressionImpl(testEquivalent, List.of(
                        new MemberValuePair(MemberValuePair.VALUE, new StringConstant(primitives, "hello")))))
                .setAccess(Inspection.Access.PRIVATE)
                .setInspectedBlock(
                        new Block.BlockBuilder(newId()).addStatement(
                                new ReturnStatement(newId(),
                                        new BinaryOperator(newId(), primitives,
                                                new VariableExpression(x), primitives.plusOperatorInt(),
                                                new VariableExpression(y), Precedence.ADDITIVE))).build())
                .build(IP).getMethodInfo();
        intSum.methodResolution.set(new MethodResolution.Builder().build());
        TypeInspection testTypeInspection = new TypeInspectionImpl.Builder(testTypeInfo, BY_HAND)
                .addTypeModifier(TypeModifier.PUBLIC)
                .noParent(primitives)
                .addField(logger)
                .addSubType(containerTypeInfo)
                .addConstructor(emptyTestConstructor)
                .addMethod(toStringMethodInfo)
                .addMethod(intSum)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null);
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
        assertTrue(stream.contains("private static int sum(int x,int y) throws MyException{"));
        assertTrue(stream.contains("T put(T value)"));
    }
}
