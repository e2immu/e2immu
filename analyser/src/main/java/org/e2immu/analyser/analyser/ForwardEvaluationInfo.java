/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;
import java.util.stream.Collectors;

public class ForwardEvaluationInfo {

    private final Map<Property, DV> properties;
    private final boolean doNotReevaluateVariableExpressions;
    private final boolean isAssignmentTarget;
    private final Variable assignmentTarget;
    private final boolean doNotComplainInlineConditional;
    private final boolean inCompanionExpression;
    private final boolean ignoreValueFromState;
    private final Set<MethodInfo> inlining;
    private final Set<Variable> evaluating;
    private final boolean evaluatingFieldExpression;
    private final boolean noSwitchingToConcreteMethod;
    private final boolean onlySort;

    private ForwardEvaluationInfo(Map<Property, DV> properties,
                                  boolean doNotReevaluateVariableExpressions,
                                  boolean isAssignmentTarget,
                                  Variable assignmentTarget,
                                  boolean doNotComplainInlineConditional,
                                  boolean inCompanionExpression,
                                  boolean ignoreValueFromState,
                                  boolean evaluatingFieldExpression,
                                  boolean noSwitchingToConcreteMethod,
                                  Set<MethodInfo> inlining,
                                  Set<Variable> evaluating,
                                  boolean onlySort) {
        this.properties = Map.copyOf(properties);
        this.isAssignmentTarget = isAssignmentTarget;
        this.assignmentTarget = assignmentTarget;
        this.doNotComplainInlineConditional = doNotComplainInlineConditional;
        this.inCompanionExpression = inCompanionExpression;
        this.doNotReevaluateVariableExpressions = doNotReevaluateVariableExpressions;
        this.ignoreValueFromState = ignoreValueFromState;
        this.inlining = inlining;
        this.evaluating = evaluating;
        this.evaluatingFieldExpression = evaluatingFieldExpression;
        this.noSwitchingToConcreteMethod = noSwitchingToConcreteMethod;
        this.onlySort = onlySort;
    }

    public boolean assignToField() {
        return assignmentTarget instanceof FieldReference;
    }

    public DV getProperty(Property property) {
        return properties.getOrDefault(property, property.falseDv);
    }

    public boolean isAssignmentTarget() {
        return isAssignmentTarget;
    }

    public String toString() {
        return new StringJoiner(", ", ForwardEvaluationInfo.class.getSimpleName() + "[", "]")
                .add("isAssignmentTarget=" + isAssignmentTarget)
                .add("properties=" + properties)
                .toString();
    }

    public boolean allowInline(MethodInfo methodInfo) {
        Set<MethodInfo> top = topOfOverloadingHierarchy(methodInfo);
        return Collections.disjoint(inlining, top);
    }

    public boolean isComplainInlineConditional() {
        return !doNotComplainInlineConditional;
    }

    public boolean isDoNotReevaluateVariableExpressions() {
        return doNotReevaluateVariableExpressions;
    }

    public boolean isInCompanionExpression() {
        return inCompanionExpression;
    }

    public Set<Variable> getEvaluating() {
        return evaluating;
    }

    public static ForwardEvaluationInfo DEFAULT = new Builder().build();
    public static ForwardEvaluationInfo ASSIGNMENT_TARGET = new Builder().setAssignmentTarget().build();
    public static ForwardEvaluationInfo NOT_NULL = new Builder()
            .addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV).build();

    public boolean isEvaluatingFieldExpression() {
        return evaluatingFieldExpression;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public boolean isIgnoreValueFromState() {
        return ignoreValueFromState;
    }

    public Variable getAssignmentTarget() {
        return assignmentTarget;
    }

    public boolean allowSwitchingToConcreteMethod() {
        return !noSwitchingToConcreteMethod;
    }

    public boolean isOnlySort() {
        return onlySort;
    }

    public static class Builder {
        private final Map<Property, DV> properties = new HashMap<>();
        private boolean doNotReevaluateVariableExpressions;
        private boolean isAssignmentTarget;
        private Variable assignmentTarget;
        private boolean doNotComplainInlineConditional;
        private boolean inCompanionExpression;
        private boolean ignoreValueFromState;
        private boolean evaluatingFieldExpression;
        private final Set<MethodInfo> inlining = new HashSet<>();
        private final Set<Variable> evaluating = new HashSet<>();
        private boolean noSwitchingToConcreteMethod;
        private boolean onlySort;

        public Builder() {
            addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);
        }

        public Builder(ForwardEvaluationInfo fwd) {
            properties.putAll(fwd.properties);
            this.doNotReevaluateVariableExpressions = fwd.doNotReevaluateVariableExpressions;
            this.isAssignmentTarget = fwd.isAssignmentTarget;
            this.assignmentTarget = fwd.assignmentTarget;
            this.doNotComplainInlineConditional = fwd.doNotComplainInlineConditional;
            this.inCompanionExpression = fwd.inCompanionExpression;
            this.ignoreValueFromState = fwd.ignoreValueFromState;
            this.evaluatingFieldExpression = fwd.evaluatingFieldExpression;
            this.noSwitchingToConcreteMethod = fwd.noSwitchingToConcreteMethod;
            this.inlining.addAll(fwd.inlining);
            this.evaluating.addAll(fwd.evaluating);
            this.onlySort = fwd.onlySort;
        }

        public ForwardEvaluationInfo build() {
            return new ForwardEvaluationInfo(Map.copyOf(properties), doNotReevaluateVariableExpressions,
                    isAssignmentTarget, assignmentTarget, doNotComplainInlineConditional, inCompanionExpression,
                    ignoreValueFromState, evaluatingFieldExpression, noSwitchingToConcreteMethod,
                    Set.copyOf(inlining), Set.copyOf(evaluating), onlySort);
        }

        public Builder setOnlySort(boolean onlySort) {
            this.onlySort = onlySort;
            return this;
        }

        public Builder addProperty(Property property, DV value) {
            properties.put(property, value);
            return this;
        }

        public Builder setEvaluatingFieldExpression() {
            evaluatingFieldExpression = true;
            return this;
        }

        public Builder setAssignmentTarget() {
            this.isAssignmentTarget = true;
            return this;
        }

        public Builder setCnnNullable() {
            addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);
            return this;
        }

        public Builder notNullNotAssignment() {
            properties.clear();
            addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            isAssignmentTarget = false;
            return this;
        }

        public Builder notNullNotAssignment(DV notNull) {
            properties.clear();
            addProperty(Property.CONTEXT_NOT_NULL, notNull);
            isAssignmentTarget = false;
            return this;
        }

        public void setCnnNotNull() {
            addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        }

        public Builder ensureModificationSetNotNull() {
            addProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            return ensureModificationSet();
        }

        public Builder ensureModificationSet() {
            if (properties.containsKey(Property.CONTEXT_MODIFIED)) {
                addProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
            }
            isAssignmentTarget = false;
            return this;
        }

        public Builder setAssignmentTarget(Variable variable) {
            assignmentTarget = variable;
            return this;
        }

        public Builder doNotComplainInlineConditional() {
            doNotComplainInlineConditional = true;
            return this;
        }

        public Builder setComplainInlineConditional() {
            doNotComplainInlineConditional = false;
            return this;
        }

        public Builder setIgnoreValueFromState() {
            ignoreValueFromState = true;
            return this;
        }

        public Builder doNotIgnoreValueFromState() {
            ignoreValueFromState = false;
            return this;
        }

        public Builder doNotReevaluateVariableExpressionsDoNotComplain() {
            doNotReevaluateVariableExpressions = true;
            doNotComplainInlineConditional = false;
            return this;
        }

        public Builder setReevaluateVariableExpressions() {
            doNotReevaluateVariableExpressions = false;
            return this;
        }

        /*
        if the condition contains a boolean method call expression, such as "this.contains("a")", an we are not
        in companion expression mode, then evaluating this.contains("a") will result in TRUE.
        In companion expression mode, we work symbolically, and must leave this.contains("a") as an informational clause.
         */
        public Builder setInCompanionExpression() {
            inCompanionExpression = true;
            return this;
        }

        public Builder removeContextNotNull() {
            properties.remove(Property.CONTEXT_NOT_NULL);
            return this;
        }

        public Builder addMethod(MethodInfo methodInfo) {
            Set<MethodInfo> top = topOfOverloadingHierarchy(methodInfo);
            assert Collections.disjoint(inlining, top);
            inlining.addAll(top);
            return this;
        }

        public Builder addEvaluating(Variable variable) {
            evaluating.add(variable);
            return this;
        }

        public Builder setNotAssignmentTarget() {
            isAssignmentTarget = false;
            return this;
        }

        public Builder addProperties(Map<Property, DV> map) {
            properties.putAll(map);
            return this;
        }

        public Builder clearProperties() {
            properties.clear();
            return this;
        }

        public Builder removeContextContainer() {
            properties.remove(Property.CONTEXT_CONTAINER);
            return this;
        }

        public Builder setNotSwitchingToConcreteMethod() {
            noSwitchingToConcreteMethod = true;
            return this;
        }

        public Builder copyNotComplainInlineConditional(ForwardEvaluationInfo forwardEvaluationInfo) {
            doNotComplainInlineConditional = forwardEvaluationInfo.doNotComplainInlineConditional;
            return this;
        }
    }

    private static Set<MethodInfo> topOfOverloadingHierarchy(MethodInfo methodInfo) {
        MethodResolution methodResolution = methodInfo.methodResolution.get();
        if (methodResolution.overrides().isEmpty()) return Set.of(methodInfo);
        return methodResolution.overrides().stream().flatMap(mi -> topOfOverloadingHierarchy(mi).stream()).collect(Collectors.toUnmodifiableSet());
    }
}
