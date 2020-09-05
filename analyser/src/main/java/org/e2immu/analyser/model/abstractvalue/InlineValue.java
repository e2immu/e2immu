package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;

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

    public final MethodInfo methodInfo;
    public final Value value;

    public InlineValue(MethodInfo methodInfo, Value value) {
        this.methodInfo = methodInfo;
        this.value = value;
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
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if(VariableProperty.METHOD_PROPERTIES_IN_INLINE_SAM.contains(variableProperty)) {
            return methodInfo.methodAnalysis.get().getProperty(variableProperty);
        }
        return value.getPropertyOutsideContext(variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if(VariableProperty.METHOD_PROPERTIES_IN_INLINE_SAM.contains(variableProperty)) {
            return methodInfo.methodAnalysis.get().getProperty(variableProperty);
        }
        return value.getProperty(evaluationContext, variableProperty);
    }

    @Override
    public Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        return value.reEvaluate(evaluationContext, translation);
    }

    @Override
    public String toString() {
        return "inline " + methodInfo.name + " on " + value.toString();
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
}
