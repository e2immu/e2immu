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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/*
  See discussion in manual for why the type is eventually an @E2Container.
 */

public class E2ImmuAnnotationExpressions {

    public final AnnotationExpression allowsInterrupt = create(AllowsInterrupt.class);
    public final AnnotationExpression beforeMark = create(BeforeMark.class);
    public final AnnotationExpression constant = create(Constant.class);
    public final AnnotationExpression constantContainer = create(ConstantContainer.class);
    public final AnnotationExpression container = create(Container.class);
    public final AnnotationExpression dependent = create(Dependent.class);
    public final AnnotationExpression extensionClass = create(ExtensionClass.class);
    public final AnnotationExpression effectivelyFinal = create(Final.class);
    public final AnnotationExpression finalFields = create(FinalFields.class);
    public final AnnotationExpression finalizer = create(Finalizer.class);
    public final AnnotationExpression fluent = create(Fluent.class);
    public final AnnotationExpression identity = create(Identity.class);
    public final AnnotationExpression ignoreModifications = create(IgnoreModifications.class);
    public final AnnotationExpression immutable = create(Immutable.class);
    public final AnnotationExpression immutableContainer = create(ImmutableContainer.class);
    public final AnnotationExpression independent = create(Independent.class);
    public final AnnotationExpression mark = create(Mark.class);
    public final AnnotationExpression modified = create(Modified.class);
    public final AnnotationExpression notLinked = create(NotLinked.class);
    public final AnnotationExpression notModified = create(NotModified.class);
    public final AnnotationExpression notNull = create(NotNull.class);
    public final AnnotationExpression notNull1 = create(NotNull1.class);
    public final AnnotationExpression nullable = create(Nullable.class);
    public final AnnotationExpression only = create(Only.class);
    public final AnnotationExpression singleton = create(Singleton.class);
    public final AnnotationExpression staticSideEffects = create(StaticSideEffects.class);
    public final AnnotationExpression testMark = create(TestMark.class);
    public final AnnotationExpression utilityClass = create(UtilityClass.class);
    public final AnnotationExpression variableField = create(Variable.class);

    private final Map<String, TypeInfo> annotationTypes;

    public E2ImmuAnnotationExpressions() {
        Map<String, TypeInfo> builder = new HashMap<>();
        add(builder, allowsInterrupt, beforeMark, constant, container, dependent, independent,
                immutableContainer, constantContainer, extensionClass, finalFields, immutable,
                effectivelyFinal, fluent, finalizer, identity, ignoreModifications, notLinked,
                mark, modified);
        add(builder, notModified, notNull, notNull1, nullable, only, singleton, staticSideEffects, testMark,
                utilityClass, variableField);
        annotationTypes = Map.copyOf(builder);
    }

    private static void add(Map<String, TypeInfo> builder, AnnotationExpression... aes) {
        for (AnnotationExpression ae : aes) {
            builder.put(ae.typeInfo().fullyQualifiedName, ae.typeInfo());
        }
    }

    /**
     * create an annotation for a given class, without parameters (contract=false, absent=false)
     *
     * @param clazz must have a method called type of Enum type AnnotationType
     * @return an annotation expression
     */
    @NotModified
    private AnnotationExpression create(Class<?> clazz) {
        return new AnnotationExpressionImpl(new TypeInfo(clazz.getPackageName(), clazz.getSimpleName()), List.of());
    }

    /*
    Important: do not create a new typeInfo here, but rely on the original, because there is identity checking with '=='
    in fromAnnotationsIntoProperties
    */
    public static AnnotationExpression createContract(Primitives primitives, AnnotationExpression original) {
        return new AnnotationExpressionImpl.Builder()
                .setTypeInfo(original.typeInfo())
                .addExpression(new MemberValuePair("contract", new BooleanConstant(primitives, true)))
                .build();
    }

    public TypeInfo immutableAnnotation(Class<?> key) {
        return Objects.requireNonNull(annotationTypes.get(key.getCanonicalName()));
    }

    public TypeInfo get(String name) {
        return Objects.requireNonNull(annotationTypes.get(name));
    }

    public Stream<TypeInfo> streamTypes() {
        return annotationTypes.values().stream();
    }
}
