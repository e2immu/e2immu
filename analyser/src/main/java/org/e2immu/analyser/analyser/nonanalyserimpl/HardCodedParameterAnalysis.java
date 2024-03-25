package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;

import java.util.Collection;

record HardCodedParameterAnalysis(String fullyQualifiedName) implements ParameterAnalysis {

    @Override
    public ParameterInfo getParameterInfo() {
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
            case MODIFIED_VARIABLE -> DV.FALSE_DV;
            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
            case NOT_NULL_PARAMETER -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case CONTAINER_RESTRICTION -> MultiLevel.NOT_CONTAINER_DV;
            default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
        };
    }

    @Override
    public LV.HiddenContentSelector getHiddenContentSelector() {
        return LV.CS_NONE;
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
