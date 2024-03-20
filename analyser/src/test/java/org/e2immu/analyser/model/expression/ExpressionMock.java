package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.function.Predicate;

public class ExpressionMock implements Expression {

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT;
    }

    @Override
    public void visit(Predicate<Element> predicate) {

    }

    @Override
    public void visit(Visitor visitor) {

    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("mock"));
    }

    @Override
    public int getComplexity() {
        return 0;
    }

    @Override
    public ParameterizedType returnType() {
        return null;
    }

    @Override
    public Precedence precedence() {
        return null;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean isReturnValue() {
        return false;
    }

    @Override
    public int compareTo(Expression v) {
        return 0;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        return 0;
    }

    @Override
    public boolean equalsNull() {
        return false;
    }

    @Override
    public boolean equalsNotNull() {
        return false;
    }

    @Override
    public boolean isBoolValueTrue() {
        return false;
    }

    @Override
    public boolean isBoolValueFalse() {
        return false;
    }

    @Override
    public boolean isInitialReturnExpression() {
        return false;
    }

    @Override
    public boolean isBooleanConstant() {
        return false;
    }

    @Override
    public Expression stateTranslateThisTo(InspectionProvider inspectionProvider, FieldReference fieldReference) {
        return null;
    }

    @Override
    public Expression createDelayedValue(Identifier identifier, EvaluationResult context, CausesOfDelay causes) {
        return null;
    }
}
