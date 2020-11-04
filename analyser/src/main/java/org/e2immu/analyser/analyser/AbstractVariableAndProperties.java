/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;

/**
 * Common denominator between VariableInfo and TransferValue.
 * Contains the code that handles a merge between multiple objects.
 * <p>
 * Merge of one = copy
 * <p>
 * Merge of two
 * Situation 1: if(c) x=a; else x=b; -> whatever x was before, it will be overwritten with the merger between a,b
 * Situation 2: starting with x=a, and considering if(c) x=b; -> b will be merged into a
 * <p>
 * Merge of more than two
 * <p>
 * used for merging of return values and all variable info objects.
 */
public abstract class AbstractVariableAndProperties {

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

    /**
     * The values of the map indicate the condition with which the properties count.
     * A value of UnknownValue.EMPTY indicates that the values are a "possibility" at most
     * <p>
     * The new value will be written as the endValue of the statement in case of VariableInfo, and the value
     * in case of TransferValue.
     * <p>
     * What do we do?
     * <p>
     * in some cases, the value can become an InlineValue based on the condition, and two cases.
     * <p>
     * NOT_NULL, SIZE, IMMUTABLE, CONTAINER can be there if all values are such
     */


    protected abstract void writeValue(Value value);

    protected abstract Value currentValue();

    protected abstract void writeProperty(VariableProperty variableProperty, int value);

    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(VariableProperty.NOT_NULL, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.SIZE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IMMUTABLE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.CONTAINER, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.READ, Math::max, Level.DELAY),
            new MergeOp(VariableProperty.ASSIGNED, Math::max, Level.DELAY)
    );

    public void merge(AbstractVariableAndProperties existing,
                      boolean existingValuesWillBeOverwritten,
                      Map<AbstractVariableAndProperties, Value> merge) {

        Value mergedValue = mergeValue(existing, existingValuesWillBeOverwritten, merge);
        writeValue(mergedValue);

        // now common properties

        List<AbstractVariableAndProperties> list = existingValuesWillBeOverwritten ?
                ListUtil.immutableConcat(merge.keySet(), List.of(existing)) : ImmutableList.copyOf(merge.keySet());
        for (MergeOp mergeOp : MERGE) {
            int commonValue = Level.DELAY;

            for (AbstractVariableAndProperties vi : list) {
                int value = vi.properties.getOrDefault(mergeOp.variableProperty, Level.DELAY);
                commonValue = mergeOp.operator.applyAsInt(commonValue, value);
            }
            if (commonValue != mergeOp.initial) {
                writeProperty(mergeOp.variableProperty, commonValue);
            }
        }
    }

    private Value mergeValue(AbstractVariableAndProperties existing,
                             boolean existingValuesWillBeOverwritten,
                             Map<AbstractVariableAndProperties, Value> merge) {
        Value currentValue = existing.currentValue();
        if (!existingValuesWillBeOverwritten && currentValue == NO_VALUE) return NO_VALUE;
        boolean haveANoValue = merge.values().stream().anyMatch(v -> v == NO_VALUE);
        if (haveANoValue) return NO_VALUE;

        // situation: we understand c
        // x=i; if(c) x=a; we do NOT overwrite; result is c?a:i
        // x=i; if(c) x=a; else x=b, overwrite; result is
        // x=i; switch(y) case c1 -> a; case c2 -> b; default -> d; overwrite

        // no need to change the value
        return currentValue;
    }

}
