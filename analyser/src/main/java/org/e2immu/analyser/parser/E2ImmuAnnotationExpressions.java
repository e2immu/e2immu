package org.e2immu.analyser.parser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.AnnotationExpressionImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/*
  See discussion in manual for why the type is eventually an @E2Container.
 */

@E2Container(after = "fields")
public class E2ImmuAnnotationExpressions {

    public final AnnotationExpression beforeMark = create(BeforeMark.class);
    public final AnnotationExpression constant = create(Constant.class);
    public final AnnotationExpression container = create(Container.class);
    public final AnnotationExpression dependent = create(Dependent.class);
    public final AnnotationExpression e1Container = create(E1Container.class);
    public final AnnotationExpression e2Container = create(E2Container.class);
    public final AnnotationExpression extensionClass = create(ExtensionClass.class);
    public final AnnotationExpression e1Immutable = create(E1Immutable.class);
    public final AnnotationExpression e2Immutable = create(E2Immutable.class);
    public final AnnotationExpression effectivelyFinal = create(Final.class);
    public final AnnotationExpression fluent = create(Fluent.class);
    public final AnnotationExpression identity = create(Identity.class);
    public final AnnotationExpression ignoreModifications = create(IgnoreModifications.class);
    public final AnnotationExpression independent = create(Independent.class);
    public final AnnotationExpression linked = create(Linked.class);
    public final AnnotationExpression mark = create(Mark.class);
    public final AnnotationExpression modified = create(Modified.class);
    public final AnnotationExpression mutableModifiesArguments = create(MutableModifiesArguments.class);
    public final AnnotationExpression notModified = create(NotModified.class);
    public final AnnotationExpression notModified1 = create(NotModified1.class);
    public final AnnotationExpression notNull = create(NotNull.class);
    public final AnnotationExpression notNull1 = create(NotNull1.class);
    public final AnnotationExpression notNull2 = create(NotNull2.class);
    public final AnnotationExpression nullable = create(Nullable.class);
    public final AnnotationExpression only = create(Only.class);
    public final AnnotationExpression precondition = create(Precondition.class);
    public final AnnotationExpression singleton = create(Singleton.class);
    public final AnnotationExpression utilityClass = create(UtilityClass.class);
    public final AnnotationExpression variableField = create(Variable.class);

    private final Map<String, TypeInfo> annotationTypes;

    public E2ImmuAnnotationExpressions() {
        ImmutableMap.Builder<String, TypeInfo> builder = new ImmutableMap.Builder<>();
        add(builder, beforeMark, constant, container, dependent, e1Container, e2Container, extensionClass, e1Immutable, e2Immutable,
                effectivelyFinal, fluent, identity, ignoreModifications, independent, linked, mark, modified, mutableModifiesArguments);
        add(builder, notModified, notModified1, notNull, notNull1, notNull2, nullable, only, precondition, singleton
                , utilityClass, variableField);
        annotationTypes = builder.build();
    }

    private static void add(ImmutableMap.Builder<String, TypeInfo> builder, AnnotationExpression... aes) {
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
        return new AnnotationExpressionImpl(TypeInfo.fromFqn(clazz.getCanonicalName()), List.of());
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
