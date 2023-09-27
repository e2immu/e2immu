package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Any of [value, markAssignment, linkedVariables]
 * can be used independently: possibly we want to mark assignment, but still have NO_VALUE for the value.
 * The stateOnAssignment can also still be NO_VALUE while the value is known, and vice versa.
 * <p>
 * Link1 goes from the argument (the owner of the changeData) to the variable in the scope of a method
 * that features a @Dependent1 parameter. (collection.add(t) will have "collection" in the linked1Variables
 * of the changeData of "t").
 */
public record ChangeData(Expression value,
                         CausesOfDelay delays,
                         CausesOfDelay stateIsDelayed,
                         boolean markAssignment,
                         Set<Integer> readAtStatementTime,
                         LinkedVariables linkedVariables,
                         LinkedVariables toRemoveFromLinkedVariables,
                         Map<Property, DV> properties,
                         int modificationTimeIncrement) {
    public ChangeData {
        Objects.requireNonNull(readAtStatementTime);
        Objects.requireNonNull(properties);
    }

    @Override
    public String toString() {
        return "ChangeData{" +
                "value=" + value +
                ", delays=" + delays +
                ", stateIsDelayed=" + stateIsDelayed +
                ", markAssignment=" + markAssignment +
                ", readAtStatementTime=" + readAtStatementTime.stream().map(Object::toString).sorted().collect(Collectors.joining(",")) +
                ", linkedVariables=" + linkedVariables +
                ", toRemoveFromLinkedVariables=" + toRemoveFromLinkedVariables +
                ", properties=" + properties.entrySet().stream().map(e -> e.getKey() + "->" + e.getValue()).sorted().collect(Collectors.joining(",")) +
                ", modificationTimeIncrement=" + modificationTimeIncrement +
                '}';
    }

    public ChangeData merge(ChangeData other) {
        LinkedVariables combinedLinkedVariables = linkedVariables == null ? other.linkedVariables : linkedVariables.merge(other.linkedVariables);
        LinkedVariables combinedToRemove = toRemoveFromLinkedVariables.merge(other.toRemoveFromLinkedVariables);
        Set<Integer> combinedReadAtStatementTime = SetUtil.immutableUnion(readAtStatementTime, other.readAtStatementTime);
        Map<Property, DV> combinedProperties = VariableInfo.mergeIgnoreAbsent(properties, other.properties);
        return new ChangeData(other.value == null ? value : other.value,
                delays.merge(other.delays),
                other.stateIsDelayed, // and not a merge!
                other.markAssignment || markAssignment,
                combinedReadAtStatementTime,
                combinedLinkedVariables,
                combinedToRemove,
                combinedProperties,
                Math.max(modificationTimeIncrement, other.modificationTimeIncrement));
    }

    public DV getProperty(Property property) {
        return properties.getOrDefault(property, property.falseDv);
    }

    public boolean isMarkedRead() {
        return !readAtStatementTime.isEmpty();
    }

    public ChangeData translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translatedValue = value == null ? null : value.translate(inspectionProvider, translationMap);
        CausesOfDelay translatedDelays = delays == null ? null : delays.translate(inspectionProvider, translationMap);
        CausesOfDelay translatedStateIsDelayed = stateIsDelayed == null ? null : stateIsDelayed.translate(inspectionProvider, translationMap);
        LinkedVariables translatedLv = linkedVariables == null ? null
                : linkedVariables.translate(inspectionProvider, translationMap);
        LinkedVariables translatedToRemove = toRemoveFromLinkedVariables == null ? null
                : toRemoveFromLinkedVariables.translate(inspectionProvider, translationMap);
        if (translatedValue == value && translatedDelays == delays && translatedStateIsDelayed == stateIsDelayed
                && translatedLv == linkedVariables && translatedToRemove == toRemoveFromLinkedVariables) {
            return this;
        }
        return new ChangeData(translatedValue, translatedDelays, translatedStateIsDelayed, markAssignment,
                readAtStatementTime, translatedLv, translatedToRemove, properties, modificationTimeIncrement);
    }

    public ChangeData incrementModificationTime() {
        return new ChangeData(value, delays, stateIsDelayed, markAssignment, readAtStatementTime, linkedVariables,
                toRemoveFromLinkedVariables, properties, modificationTimeIncrement + 1);
    }
}
