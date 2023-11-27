package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.analyser.util.ParSeq;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

class HardCodedMethodAnalysis implements MethodAnalysis {
    private final String fullyQualifiedName;
    private final Map<String, ParameterAnalysis> hardCodedParameters;
    private final Primitives primitives;

    public HardCodedMethodAnalysis(Primitives primitives,
                                   Map<String, ParameterAnalysis> hardCodedParameters,
                                   String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
        this.primitives = primitives;
        this.hardCodedParameters = hardCodedParameters;
    }

    @Override
    public MethodInfo getMethodInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression getSingleReturnValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ParameterAnalysis> getParameterAnalyses() {
        List<String> fqns = GlobalAnalyserContext.PARAMETER_ANALYSES.get(fullyQualifiedName);
        if (fqns == null) {
            throw new UnsupportedOperationException("Expect hardcoded parameter analyses for " + fullyQualifiedName);
        }
        for (String fqn : fqns) {
            ParameterAnalysis pa = hardCodedParameters.get(fqn);
            if (pa == null) throw new UnsupportedOperationException("Cannot find " + fqn);
        }
        return fqns.stream().map(hardCodedParameters::get).toList();
    }

    @Override
    public Precondition getPreconditionForEventual() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Precondition getPrecondition() {
        return Precondition.empty(primitives);
    }

    @Override
    public Set<PostCondition> getPostConditions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> indicesOfEscapesNotInPreOrPostConditions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CommutableData getCommutableData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DV valueFromOverrides(AnalysisProvider analysisProvider, Property property) {
        return MethodAnalysisImpl.valueFromOverrides(this, analysisProvider, property);
    }

    @Override
    public CausesOfDelay eventualStatus() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CausesOfDelay preconditionStatus() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParSeq<ParameterInfo> getParallelGroups() {
        return null;
    }

    @Override
    public List<Expression> sortAccordingToParallelGroupsAndNaturalOrder(List<Expression> parameterExpressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markFirstIteration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasBeenAnalysedUpToIteration0() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FieldInfo getSetField() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetSetEquivalent getSetEquivalent() {
        throw new UnsupportedOperationException();
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
        return switch (property) {
            case FLUENT, IDENTITY, MODIFIED_METHOD, TEMP_MODIFIED_METHOD, MODIFIED_METHOD_ALT_TEMP,
                    FINALIZER, CONSTANT, STATIC_SIDE_EFFECTS -> DV.FALSE_DV;
            case IGNORE_MODIFICATIONS -> MultiLevel.NOT_IGNORE_MODS_DV;
            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case CONTAINER -> MultiLevel.CONTAINER_DV;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
            default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
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
