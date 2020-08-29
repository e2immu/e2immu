package org.e2immu.analyser.model;

public class AnnotationToProperties {
/*
    Set<AnnotationExpression> dynamicTypeAnnotations(EvaluationContext evaluationContext) {
        int container = getProperty(evaluationContext, VariableProperty.CONTAINER);
        int immutable = getProperty(evaluationContext, VariableProperty.IMMUTABLE);
        boolean noContainer = container == Level.UNDEFINED;
        boolean noImmutable = immutable == Level.UNDEFINED;

        if (noContainer && noImmutable) return Set.of();
        if (noContainer) return Set.of(AnnotationExpression.immutable(evaluationContext.getTypeContext(), immutable));
        return Set.of(AnnotationExpression.container(evaluationContext.getTypeContext(), noImmutable ? 0 : immutable));
    }

    private Set<ElementType> where(AnnotationExpression isNotNull) {
        Optional<AnnotationExpression> opt = typeInspection.get().annotations.stream()
                .filter(e -> e.typeInfo.equals(isNotNull.typeInfo)).findFirst();
        if (opt.isEmpty()) return Set.of();
        AnnotationExpression found = opt.get();
        ElementType[] elements = found.extract("where", NOT_NULL_WHERE);
        return Arrays.stream(elements).collect(Collectors.toSet());
    }

    private int highestIsNotNull(TypeContext typeContext, ElementType elementType) {
        Set<ElementType> set2 = where(typeContext.notNull2.get());
        if (set2 != null && set2.contains(elementType)) return Level.compose(Level.TRUE, Level.NOT_NULL_2);
        Set<ElementType> set1 = where(typeContext.notNull1.get());
        if (set1 != null && set1.contains(elementType)) return Level.compose(Level.TRUE, Level.NOT_NULL_1);
        Set<ElementType> set = where(typeContext.notNull.get());
        if (set != null && set.contains(elementType)) return Level.compose(Level.TRUE, Level.NOT_NULL);
        return Level.FALSE;
    }

    public int isNotNullForParameters(TypeContext typeContext) {
        return highestIsNotNull(typeContext, ElementType.PARAMETER);
    }

    public int isNotNullForFields(TypeContext typeContext) {
        return highestIsNotNull(typeContext, ElementType.FIELD);
    }

    public int isNotNullForMethods(TypeContext typeContext) {
        return highestIsNotNull(typeContext, ElementType.METHOD);
    }


    default Integer getContainer(TypeContext typeContext) {
        Boolean container = annotatedWith(typeContext.container.get());
        if (container == null) return null; // delay
        if (container) return 1;
        Boolean e1container = annotatedWith(typeContext.e1Container.get());
        if (e1container != null) return 1;
        Boolean e2container = annotatedWith(typeContext.e2Container.get());
        if (e2container != null) return 1;
        return 0; // not a container
    }

    default Integer getImmutable(TypeContext typeContext) {
        Boolean e1Immutable = annotatedWith(typeContext.e1Immutable.get());
        if (e1Immutable == null) return null;
        Boolean e2Immutable = annotatedWith(typeContext.e2Immutable.get());
        if (e2Immutable == null) return null;
        Boolean e2Container = annotatedWith(typeContext.e2Container.get());
        if (e2Container == null) return null;
        Boolean e1Container = annotatedWith(typeContext.e1Container.get());
        if (e1Container == null) return null;
        if (e2Immutable || e2Container) return 2;
        if (e1Immutable || e1Container) return 1;
        return 0;
    }

 */
}
