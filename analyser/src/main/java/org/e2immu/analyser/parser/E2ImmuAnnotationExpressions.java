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
import org.e2immu.annotation.eventual.BeforeMark;
import org.e2immu.annotation.eventual.Mark;
import org.e2immu.annotation.eventual.Only;
import org.e2immu.annotation.eventual.TestMark;
import org.e2immu.annotation.method.GetSet;
import org.e2immu.annotation.rare.*;
import org.e2immu.annotation.type.ExtensionClass;
import org.e2immu.annotation.type.Singleton;
import org.e2immu.annotation.type.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/*
Why is this type an immutable container?
1. the non-private fields are immutable themselves: OK, because AnnotationExpression is immutable.
2. there are no modifying methods: OK
3. no accessible content leaks; this is not a problem because TypeInfo is immutable.
 */
@ImmutableContainer
public class E2ImmuAnnotationExpressions {
    public final static String IMPLIED = "implied";
    public final static String ABSENT = "absent";
    public final static String AFTER = "after";
    public final static String BEFORE = "before";
    public final static String CONSTRUCTION = "construction";
    public final static String CONTENT = "content";
    public final static String CONTRACT = "contract";
    public final static String HIDDEN_CONTENT = "hc";
    public final static String INCONCLUSIVE = "inconclusive";
    public final static String HC_PARAMETERS = "hcParameters";
    public final static String VALUE = "value";
    public final static String PAR = "par";
    public final static String SEQ = "seq";
    public final static String MULTI = "multi";

    public final AnnotationExpression allowsInterrupt = create(AllowsInterrupt.class);
    public final AnnotationExpression beforeMark = create(BeforeMark.class);
    public final AnnotationExpression commutable = create(Commutable.class);
    public final AnnotationExpression container = create(Container.class);
    public final AnnotationExpression extensionClass = create(ExtensionClass.class);
    public final AnnotationExpression effectivelyFinal = create(Final.class);
    public final AnnotationExpression finalFields = create(FinalFields.class);
    public final AnnotationExpression finalizer = create(Finalizer.class);
    public final AnnotationExpression fluent = create(Fluent.class);
    public final AnnotationExpression getSet = create(GetSet.class);
    public final AnnotationExpression identity = create(Identity.class);
    public final AnnotationExpression ignoreModifications = create(IgnoreModifications.class);
    public final AnnotationExpression immutable = create(Immutable.class);
    public final AnnotationExpression immutableContainer = create(ImmutableContainer.class);
    public final AnnotationExpression independent = create(Independent.class);
    public final AnnotationExpression mark = create(Mark.class);
    public final AnnotationExpression modified = create(Modified.class);
    public final AnnotationExpression notModified = create(NotModified.class);
    public final AnnotationExpression notNull = create(NotNull.class);
    public final AnnotationExpression nullable = create(Nullable.class);
    public final AnnotationExpression only = create(Only.class);
    public final AnnotationExpression singleton = create(Singleton.class);
    public final AnnotationExpression staticSideEffects = create(StaticSideEffects.class);
    public final AnnotationExpression testMark = create(TestMark.class);
    public final AnnotationExpression utilityClass = create(UtilityClass.class);

    @ImmutableContainer // result of Map.copyOf
    private final Map<String, TypeInfo> annotationTypes;

    public E2ImmuAnnotationExpressions() {
        Map<String, TypeInfo> builder = new HashMap<>();
        add(builder, allowsInterrupt, beforeMark, commutable, container, independent,
                immutableContainer, extensionClass, finalFields, getSet, immutable,
                effectivelyFinal, fluent, finalizer, identity, ignoreModifications, mark, modified);
        add(builder, notModified, notNull, nullable, only, singleton, staticSideEffects, testMark,
                utilityClass);
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

    public static AnnotationExpression create(Primitives primitives, Class<?> clazz, String key, boolean value) {
        return new AnnotationExpressionImpl(new TypeInfo(clazz.getPackageName(), clazz.getSimpleName()),
                List.of(new MemberValuePair(key, new BooleanConstant(primitives, value))));
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
        return Objects.requireNonNull(annotationTypes.get(name), name);
    }

    public Stream<TypeInfo> streamTypes() {
        return annotationTypes.values().stream();
    }
}
