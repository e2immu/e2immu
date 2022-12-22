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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.DelayedWrappedExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.IGNORE_STATEMENT_TIME;
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

    private final SetOnce<Integer> modificationTime = new SetOnce<>();

    // used for returning delayed values
    public VariableInfoImpl(Location location, Variable variable, int statementTime) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), null, statementTime);
        assert location != Location.NOT_YET_SET;
    }

    // used to break initialisation delay
    public VariableInfoImpl(Location location, Variable variable, Expression value) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), value, IGNORE_STATEMENT_TIME);
        assert location != Location.NOT_YET_SET;
    }

    // used as a temp in MergeHelper; make sure that this one is not used to generate VI objects for inclusion
    // in DelayedWrappedExpression: they need to be the original ones that will be updated in subsequent iterations
    public VariableInfoImpl(Location location, Variable variable, Expression value, Properties properties) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), value, IGNORE_STATEMENT_TIME);
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
        int statementTime;
        if (variable instanceof FieldReference fr && fr.scope instanceof DelayedVariableExpression dve) {
            statementTime = dve.statementTime;
        } else {
            statementTime = IGNORE_STATEMENT_TIME;
        }
        value.setVariable(DelayedVariableExpression.forVariable(variable, statementTime, causesOfDelay));
        linkedVariables.setVariable(LinkedVariables.NOT_YET_SET);
    }

    // normal one for creating an initial or evaluation
    VariableInfoImpl(Location location,
                     Variable variable,
                     AssignmentIds assignmentIds,
                     String readId,
                     Set<Integer> readAtStatementTimes,
                     Expression delayedValue,
                     int statementTime) {
        this.location = Objects.requireNonNull(location);
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = Objects.requireNonNull(assignmentIds);
        this.readId = Objects.requireNonNull(readId);
        this.readAtStatementTimes = Objects.requireNonNull(readAtStatementTimes);
        CausesOfDelay causesOfDelay = initialValue(location, variable);
        value.setVariable(delayedValue == null ? DelayedVariableExpression.forVariable(variable, statementTime, causesOfDelay) : delayedValue);
        linkedVariables.setVariable(LinkedVariables.NOT_YET_SET);
    }

    private static CausesOfDelay initialValue(Location location, Variable variable) {
        assert Stage.isPresent(location.statementIdentifierOrNull()) : "no stage in location" + location;
        return DelayFactory.createDelay(new VariableCause(variable, location, CauseOfDelay.Cause.INITIAL_VALUE));
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

    private static final List<Property> CONTEXT_PROPERTIES = List.of(CONTEXT_IMMUTABLE, CONTEXT_CONTAINER, CONTEXT_MODIFIED,
            CONTEXT_NOT_NULL);

    @Override
    public Properties contextProperties() {
        return Properties.of(CONTEXT_PROPERTIES.stream().collect(
                Collectors.toUnmodifiableMap(p -> p, this::getProperty)));
    }

    @Override
    public Properties properties() {
        return properties;
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
            return DelayFactory.createDelay(new VariableCause(variable, location, property.causeOfDelay()));
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

    @Override
    public int getModificationTimeOrNegative() {
        return modificationTime.getOrDefault(-2);
    }

    public void setModificationTimeIfNotYetSet(int modificationTime) {
        if (!this.modificationTime.isSet()) {
            this.modificationTime.set(modificationTime);
        }
    }

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    // return progress
    boolean setProperty(Property property, DV value) {
        try {
            return properties.put(property, value);
        } catch (RuntimeException e) {
            LOGGER.error("Error setting property {} of {} to {}", property, variable.fullyQualifiedName(), value);
            throw e;
        }
    }

    boolean setLinkedVariables(LinkedVariables linkedVariables) {
        assert linkedVariables != null;
        assert this.linkedVariables.get() != null : "Please initialize LVs";
        assert !linkedVariables.contains(variable) : "Self references are not allowed";
        if (this.linkedVariables.isFinal()) {
            if (!this.linkedVariables.get().equals(linkedVariables)) {
                throw new IllegalStateException("Variable " + variable.fullyQualifiedName()
                        + ": not allowed to change LVs anymore: old: " + this.linkedVariables.get()
                        + ", new " + linkedVariables);
            }
            return false;
        }
        if (this.linkedVariables.get() != LinkedVariables.NOT_YET_SET) {
            // the first time, there are no restrictions on statically assigned values
            // as soon as we have a real value, we cannot change SA anymore

            if (!this.linkedVariables.get().identicalStaticallyAssigned(linkedVariables)) {
                throw new IllegalStateException("Cannot change statically assigned for variable "
                        + variable.fullyQualifiedName() + "\nold: " + this.linkedVariables.get()
                        + "\nnew: " + linkedVariables + "\n");
            }
        }
        if (linkedVariables.isDelayed()) {
            this.linkedVariables.setVariable(linkedVariables);
            return false;
        }
        this.linkedVariables.setFinal(linkedVariables);
        return true;
    }

    // return progress
    boolean setValue(Expression value) {
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null && ve.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        // FIXME this second clause was added to prevent Test_Output_03_Formatter from writing a delayed after a real value...
        // this is probably not the solution?
        if (value.isDelayed() || variable instanceof FieldReference fr && fr.scope.isDelayed()) {
            try {
                this.value.setVariable(value);
                return false;
            } catch (IllegalStateException ise) {
                LOGGER.error("Variable {}: value '{}' is delayed, but final value '{}' already present",
                        variable.fullyQualifiedName(), value, this.value.get());
                throw ise;
            }
        }
        assert !(value.isInstanceOf(DelayedExpression.class)); // simple safeguard, others are more difficult to check
        assert !(value.isInstanceOf(DelayedVariableExpression.class));
        assert !(value.isInstanceOf(DelayedWrappedExpression.class));
        try {
            return setFinalAllowEquals(this.value, value);
        } catch (IllegalStateException ise) {
            LOGGER.error("Variable {}: overwriting final value", variable.fullyQualifiedName());
            throw ise;
        }
    }

    /*
    things to set for a new variable
     */
    public void newVariable() {
        setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);
        setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        setProperty(EXTERNAL_NOT_NULL, EXTERNAL_NOT_NULL.valueWhenAbsent());
        setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV); // even if the variable is a primitive...
        setProperty(CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        setProperty(EXTERNAL_IMMUTABLE, EXTERNAL_IMMUTABLE.valueWhenAbsent());
        setProperty(CONTAINER_RESTRICTION, CONTAINER_RESTRICTION.valueWhenAbsent());
        setProperty(EXTERNAL_IGNORE_MODIFICATIONS, EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent());
        modificationTime.set(0);
    }
}
