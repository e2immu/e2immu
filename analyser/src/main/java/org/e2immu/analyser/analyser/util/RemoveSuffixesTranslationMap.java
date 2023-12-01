package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.ExpandedVariable;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.InspectionProvider;

public class RemoveSuffixesTranslationMap implements TranslationMap {

    private final InspectionProvider inspectionProvider;

    public RemoveSuffixesTranslationMap(InspectionProvider inspectionProvider) {
        this.inspectionProvider = inspectionProvider;
    }

    @Override
    public Expression translateExpression(Expression expression) {
        if (expression instanceof ExpandedVariable ev) {
            return new VariableExpression(ev.identifier, ev.getVariable()).translate(inspectionProvider, this);
        }
        if (expression instanceof VariableExpression ve && ve.getSuffix() != VariableExpression.NO_SUFFIX) {
            return new VariableExpression(ve.identifier, ve.variable()).translate(inspectionProvider, this);
        }
        return expression;
    }

    @Override
    public boolean recurseIntoScopeVariables() {
        return true;
    }
}
