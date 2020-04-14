package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Value;

import java.util.List;
import java.util.stream.Collectors;

public class ArrayValue implements Value {

    public final List<Value> values;

    public ArrayValue(List<Value> values) {
        this.values = ImmutableList.copyOf(values);
    }

    @Override
    public String asString() {
        return "{" + values.stream().map(Value::asString).collect(Collectors.joining(",")) + "}";
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return true;
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof ArrayValue) {
            // TODO
        }
        return 0;
    }
}
