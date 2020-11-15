package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;

import java.util.Map;
import java.util.function.Consumer;

/*
 can only be created as the single result value of a method

 will be substituted at any time in MethodCall

 Big question: do properties come from the expression, or from the method??
 In the case of Supplier.get() (which is modifying by default), the expression may for example be a parameterized string (t + "abc").
 The string expression is Level 2 immutable, hence not-modified.

 Properties that rely on the return value, should come from the Value. Properties to do with modification, should come from the method.
 */
public class InlineValue implements Value {

    public enum Applicability {
        EVERYWHERE(0), // no references to fields, static or otherwise, unless they are public
        PROTECTED(1), // reference to protected fields
        PACKAGE(2),  // reference to package-private fields
        TYPE(3),   // can only be applied in the same type (reference to private fields)
        METHOD(4), // can only be applied in the same method (reference to local variables)

        NONE(5); // cannot be expressed properly

        public final int order;

        Applicability(int order) {
            this.order = order;
        }

        public Applicability mostRestrictive(Applicability other) {
            return order < other.order ? other : this;
        }
    }

    public final MethodInfo methodInfo;
    public final Value value;
    public final Applicability applicability;

    public InlineValue(MethodInfo methodInfo, Value value, Applicability applicability) {
        this.methodInfo = methodInfo;
        this.value = value;
        this.applicability = applicability;
    }

    @Override
    public ParameterizedType type() {
        return value.type(); // maybe better than methodInfo.returnType()
    }

    @Override
    public boolean isNumeric() {
        return value.isNumeric();
    }

    @Override
    public int order() {
        return ORDER_INLINE_METHOD;
    }

    @Override
    public int internalCompareTo(Value v) {
        InlineValue mv = (InlineValue) v;
        return methodInfo.distinguishingName().compareTo(mv.methodInfo.distinguishingName());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.METHOD_PROPERTIES_IN_INLINE_SAM.contains(variableProperty)) {
            return evaluationContext.getMethodAnalysis(methodInfo).getProperty(variableProperty);
        }
        return value.getProperty(evaluationContext, variableProperty);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        return value.reEvaluate(evaluationContext, translation);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "inline " + methodInfo.name + " on " + value.print(printMode);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return value.getObjectFlow();
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        value.visit(consumer);
        consumer.accept(this);
    }

    public boolean canBeApplied(EvaluationContext evaluationContext) {
        return switch (applicability) {
            case EVERYWHERE -> true;
            case NONE -> false;
            case TYPE -> evaluationContext.getCurrentType().equals(methodInfo.typeInfo);
            case METHOD -> methodInfo.equals(evaluationContext.getCurrentMethod().methodInfo);
            case PACKAGE -> evaluationContext.getCurrentType().packageName().equals(methodInfo.typeInfo.packageName());
            default -> throw new UnsupportedOperationException("TODO");
        };
    }
}
