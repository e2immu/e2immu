package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

record HardCodedTypeAnalysis(String fullyQualifiedName,
                             TypeInfo.HardCoded hardCoded) implements TypeAnalysis {


    @Override
    public TypeInfo getTypeInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsFinalFields() {
        return Map.of();
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsImmutable() {
        return Map.of();
    }

    @Override
    public boolean containsApprovedPreconditionsImmutable(FieldReference fieldReference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean approvedPreconditionsImmutableIsEmpty() {
        return true;
    }

    @Override
    public Expression getApprovedPreconditions(boolean e2, FieldReference fieldInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CausesOfDelay approvedPreconditionsStatus(boolean e2) {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
        return false;
    }

    @Override
    public Set<FieldInfo> getEventuallyImmutableFields() {
        return Set.of();
    }

    @Override
    public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
        return Set.of();
    }

    @Override
    public FieldInfo translateToVisibleField(FieldReference fieldReference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, MethodInfo> getAspects() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DV immutableDeterminedByTypeParameters() {
        return DV.fromBoolDv(hardCoded == TypeInfo.HardCoded.IMMUTABLE_HC);
    }

    @Override
    public SetOfTypes getHiddenContentTypes() {
        return SetOfTypes.EMPTY;
    }

    @Override
    public CausesOfDelay hiddenContentDelays() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public CausesOfDelay guardedForContainerPropertyDelays() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public Set<FieldInfo> guardedForContainerProperty() {
        return Set.of();
    }

    @Override
    public CausesOfDelay guardedForInheritedContainerPropertyDelays() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public Set<FieldInfo> guardedForInheritedContainerProperty() {
        return Set.of();
    }

    @Override
    public Set<FieldInfo> visibleFields() {
        return Set.of();
    }

    @Override
    public AnnotationExpression annotationGetOrDefaultNull(AnnotationExpression expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void internalAllDoneCheck() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPropertyDelayWhenNotFinal(Property property, CausesOfDelay causes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DV getProperty(Property property) {
        return getPropertyFromMapNeverDelay(property);
    }

    @Override
    public DV getPropertyFromMapDelayWhenAbsent(Property property) {
        return getPropertyFromMapNeverDelay(property);
    }

    @Override
    public DV getPropertyFromMapNeverDelay(Property property) {
        TypeInfo.HardCoded hc = TypeInfo.HARDCODED_TYPES.get(fullyQualifiedName);
        return switch (hc) {
            case IMMUTABLE -> switch (property) {
                case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
                case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                case CONTAINER -> MultiLevel.CONTAINER_DV;
                case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
            };
            case IMMUTABLE_HC -> switch (property) {
                case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
                case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                case CONTAINER -> MultiLevel.CONTAINER_DV;
                case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
            };
        };
    }

    @Override
    public Location location(Stage stage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification,
                                                  boolean acceptVerifyAsContracted,
                                                  Collection<AnnotationExpression> annotations,
                                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException();
    }
}
