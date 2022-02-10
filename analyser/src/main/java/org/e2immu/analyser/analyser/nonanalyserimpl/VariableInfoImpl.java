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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_YET_READ;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

public class VariableInfoImpl implements VariableInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInfoImpl.class);
    private final Location location;
    private final Variable variable;
    private final AssignmentIds assignmentIds;
    private final String readId;
    // goal, for now: from iteration 0 to iteration 1, when a field has been read, collect the statement times
    // it is too early to know if the field will be variable or nor; if variable, new local copies need
    // creating before iteration 1's evaluation starts
    // ONLY set to values in iteration 0's evaluation
    private final Set<Integer> readAtStatementTimes;

    private final org.e2immu.analyser.analyser.Properties properties = Properties.writable();
    private final EventuallyFinal<Expression> value = new EventuallyFinal<>();

    // 20211023 needs to be frozen explicitly
    private final EventuallyFinal<LinkedVariables> linkedVariables = new EventuallyFinal<>();

    // ONLY for testing!
    public VariableInfoImpl(Variable variable) {
        this(Location.NOT_YET_SET, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), null);
    }

    // used for returning delayed values
    public VariableInfoImpl(Location location, Variable variable) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), null);
        assert location != Location.NOT_YET_SET;
    }

    // used to break initialisation delay
    public VariableInfoImpl(Location location, Variable variable, Expression value) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), value);
        assert location != Location.NOT_YET_SET;
    }

    // used as a temp in MergeHelper
    public VariableInfoImpl(Variable variable, Expression value, Properties properties) {
        this(Location.NOT_YET_SET, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), value);
        properties.stream().forEach(e -> setProperty(e.getKey(), e.getValue()));
    }

    // used by merge code
    VariableInfoImpl(Location location, Variable variable, AssignmentIds assignmentIds, String readId) {
        this.location = Objects.requireNonNull(location);
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = assignmentIds;
        this.readId = readId;
        this.readAtStatementTimes = Set.of();
        CausesOfDelay causesOfDelay = initialValue(location, variable);
        value.setVariable(DelayedVariableExpression.forVariable(variable, causesOfDelay));
        linkedVariables.setVariable(new LinkedVariables(Map.of(), causesOfDelay));
    }

    // normal one for creating an initial or evaluation
    VariableInfoImpl(Location location,
                     Variable variable,
                     AssignmentIds assignmentIds,
                     String readId,
                     Set<Integer> readAtStatementTimes,
                     Expression delayedValue) {
        this.location = Objects.requireNonNull(location);
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = Objects.requireNonNull(assignmentIds);
        this.readId = Objects.requireNonNull(readId);
        this.readAtStatementTimes = Objects.requireNonNull(readAtStatementTimes);
        CausesOfDelay causesOfDelay = initialValue(location, variable);
        value.setVariable(delayedValue == null ? DelayedVariableExpression.forVariable(variable, causesOfDelay) : delayedValue);
        linkedVariables.setVariable(new LinkedVariables(Map.of(), causesOfDelay));
    }

    private static CausesOfDelay initialValue(Location location, Variable variable) {
        return new SimpleSet(new VariableCause(variable, location, CauseOfDelay.Cause.INITIAL_VALUE));
    }

    @Override
    public boolean valueIsSet() {
        return value.isFinal();
    }

    @Override
    public AssignmentIds getAssignmentIds() {
        return assignmentIds;
    }

    @Override
    public String getReadId() {
        return readId;
    }

    @Override
    public Stream<Map.Entry<Property, DV>> propertyStream() {
        return properties.stream();
    }

    @Override
    public String name() {
        return variable.fullyQualifiedName();
    }

    @Override
    public Variable variable() {
        return variable;
    }

    @Override
    public LinkedVariables getLinkedVariables() {
        return linkedVariables.get();
    }

    @Override
    public Expression getValue() {
        return value.get();
    }

    @Override
    public Properties valueProperties() {
        return Properties.of(EvaluationContext.VALUE_PROPERTIES.stream().collect(
                Collectors.toUnmodifiableMap(p -> p, this::getProperty)));
    }

    @Override
    public DV getProperty(Property property, DV defaultValue) {
        if (defaultValue == null) return properties.getOrDefaultNull(property);
        return properties.getOrDefault(property, defaultValue);
    }

    @Override
    public DV getProperty(Property property) {
        DV dv = properties.getOrDefaultNull(property);
        if (dv == null) {
            return new SimpleSet(new VariableCause(variable, location, property.causeOfDelay()));
        }
        return dv;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[name=").append(name()).append(", props=").append(properties);
        if (value.isFinal()) {
            sb.append(", value=").append(value.get());
        }
        return sb.append("]").toString();
    }

    @Override
    public Map<Property, DV> getProperties() {
        return properties.toImmutableMap();
    }

    @Override
    public VariableInfo freeze() {
        return null;
    }

    public Set<Integer> getReadAtStatementTimes() {
        return readAtStatementTimes;
    }

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    void setProperty(Property property, DV value) {
        try {
            properties.put(property, value);
        } catch (RuntimeException e) {
            LOGGER.error("Error setting property {} of {} to {}", property, variable.fullyQualifiedName(), value);
            throw e;
        }
    }

    void setLinkedVariables(LinkedVariables linkedVariables) {
        assert linkedVariables != null;
        this.linkedVariables.setVariable(linkedVariables);
    }

    void setValue(Expression value) {
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null && ve.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        if (value.isDelayed()) {
            try {
                this.value.setVariable(value);
            } catch (IllegalStateException ise) {
                LOGGER.error("Variable {}: value '{}' is delayed, but final value '{}' already present",
                        variable.fullyQualifiedName(), value, this.value.get());
                throw ise;
            }
        } else {
            assert !(value.isInstanceOf(DelayedExpression.class)); // simple safe-guard, others are more difficult to check
            assert !(value.isInstanceOf(DelayedVariableExpression.class));

            try {
                setFinalAllowEquals(this.value, value);
            } catch (IllegalStateException ise) {
                LOGGER.error("Variable {}: overwriting final value", variable.fullyQualifiedName());
                throw ise;
            }
        }
    }

    /*
    things to set for a new variable
     */
    public void newVariable(boolean notNull) {
        setProperty(Property.CONTEXT_NOT_NULL, (notNull ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV)
                .max(AnalysisProvider.defaultNotNull(variable.parameterizedType())));
        setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        setProperty(EXTERNAL_NOT_NULL, EXTERNAL_NOT_NULL.valueWhenAbsent());
        setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV); // even if the variable is a primitive...
        setProperty(CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        setProperty(EXTERNAL_IMMUTABLE, EXTERNAL_IMMUTABLE.valueWhenAbsent());
        setProperty(EXTERNAL_CONTAINER, EXTERNAL_CONTAINER.valueWhenAbsent());
        setProperty(EXTERNAL_IGNORE_MODIFICATIONS, EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent());
    }

    public void ensureProperty(Property property, DV dv) {
        DV inMap = properties.getOrDefaultNull(property);
        if (inMap == null || inMap.isDelayed()) {
            properties.put(property, dv);
        }
    }
}
