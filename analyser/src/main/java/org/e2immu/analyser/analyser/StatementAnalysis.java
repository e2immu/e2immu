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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.AddOnceSet;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.*;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.model.MultiLevel.MUTABLE_DV;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container
public class StatementAnalysis extends AbstractAnalysisBuilder implements Comparable<StatementAnalysis>,
        HasNavigationData<StatementAnalysis> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalysis.class);

    public final Statement statement;
    public final String index;
    public final StatementAnalysis parent;
    public final boolean inSyncBlock;
    public final MethodAnalysis methodAnalysis;

    public final AddOnceSet<Message> messages = new AddOnceSet<>();
    public final NavigationData<StatementAnalysis> navigationData = new NavigationData<>();
    public final SetOnceMap<String, VariableInfoContainer> variables = new SetOnceMap<>();

    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData;
    public final FlowData flowData;
    public final AddOnceSet<String> localVariablesAssignedInThisLoop;
    public final AddOnceSet<Variable> candidateVariablesForNullPtrWarning = new AddOnceSet<>();

    public StatementAnalysis(Primitives primitives,
                             MethodAnalysis methodAnalysis,
                             Statement statement, StatementAnalysis parent, String index, boolean inSyncBlock) {
        super(primitives, index);
        this.index = super.simpleName;
        this.statement = statement;
        this.parent = parent;
        this.inSyncBlock = inSyncBlock;
        this.methodAnalysis = Objects.requireNonNull(methodAnalysis);
        localVariablesAssignedInThisLoop = statement instanceof LoopStatement ? new AddOnceSet<>() : null;
        stateData = new StateData(statement instanceof LoopStatement);
        flowData = new FlowData(location());
    }

    public String fullyQualifiedName() {
        return methodAnalysis.getMethodInfo().fullyQualifiedName + ":" + index;
    }

    public String toString() {
        return index + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(StatementAnalysis o) {
        return index.compareTo(o.index);
    }

    @Override
    public NavigationData<StatementAnalysis> getNavigationData() {
        return navigationData;
    }

    public static StatementAnalysis startOfBlock(StatementAnalysis sa, int block) {
        return sa == null ? null : sa.startOfBlock(block);
    }

    private StatementAnalysis startOfBlock(int i) {
        if (!navigationData.blocks.isSet()) return null;
        List<Optional<StatementAnalysis>> list = navigationData.blocks.get();
        return i >= list.size() ? null : list.get(i).orElse(null);
    }

    @Override
    public StatementAnalysis followReplacements() {
        if (navigationData.replacement.isSet()) {
            return navigationData.replacement.get().followReplacements();
        }
        return this;
    }

    @Override
    public String index() {
        return index;
    }

    @Override
    public Statement statement() {
        return statement;
    }

    @Override
    public StatementAnalysis lastStatement() {
        if (flowData.isUnreachable()) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        StatementAnalysis replaced = followReplacements();
        return replaced.navigationData.next.get().map(statementAnalysis -> {
            if (statementAnalysis.flowData.isUnreachable()) {
                return replaced;
            }
            return statementAnalysis.lastStatement();
        }).orElse(replaced);
    }

    public List<StatementAnalysis> lastStatementsOfNonEmptySubBlocks() {
        return navigationData.blocks.get().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sa -> !sa.flowData.isUnreachable())
                .map(StatementAnalysis::lastStatement)
                .collect(Collectors.toList());
    }

    @Override
    public StatementAnalysis parent() {
        return parent;
    }

    @Override
    public void wireNext(StatementAnalysis newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
    }

    @Override
    public void wireDirectly(StatementAnalysis newStatement) {
        navigationData.replacement.set(newStatement);
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalysis> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent(),
                statements, startIndex, false, inSyncBlock);
    }

    public static StatementAnalysis recursivelyCreateAnalysisObjects(
            Primitives primitives,
            MethodAnalysis methodAnalysis,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock) {
        Objects.requireNonNull(methodAnalysis);
        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
        }
        StatementAnalysis first = null;
        StatementAnalysis previous = null;

        for (Statement statement : statements) {
            String iPlusSt = indices + "." + pad(statementIndex, statements.size());
            StatementAnalysis statementAnalysis = new StatementAnalysis(primitives, methodAnalysis, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.navigationData.next.set(Optional.of(statementAnalysis));
            }
            previous = statementAnalysis;
            if (first == null) first = statementAnalysis;

            int blockIndex = 0;
            List<Optional<StatementAnalysis>> analysisBlocks = new ArrayList<>();

            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;
            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                        structure.statements(), indexWithBlock, true, newInSyncBlock);
                analysisBlocks.add(Optional.of(subStatementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements()) {
                if (subStatements.haveStatements()) {
                    String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                            subStatements.statements(), indexWithBlock, true, newInSyncBlock);
                    analysisBlocks.add(Optional.of(subStatementAnalysis));
                } else {
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            }
            statementAnalysis.navigationData.blocks.set(List.copyOf(analysisBlocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.navigationData.next.set(Optional.empty());
        }
        return first;
    }

    public boolean atTopLevel() {
        return index.indexOf('.') == 0;
    }

    public void ensure(Message newMessage) {
        if (!messages.contains(newMessage)) {
            messages.add(newMessage);
        }
    }

    /**
     * this method is meant for reading from VariableInfo only, not for writing!
     *
     * @param variableName the name of the variable
     * @return a variable info object
     * @throws IllegalArgumentException if the variable does not exist
     */

    @NotNull
    public VariableInfo getLatestVariableInfo(String variableName) {
        if (!variables.isSet(variableName)) {
            return null; // statements will not have been analysed yet?
        }
        return variables.get(variableName).current();
    }

    public Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo, boolean allowLocalCopies) {
        return variables.stream()
                .map(e -> e.getValue().current())
                .filter(v -> v.variable() instanceof FieldReference fieldReference &&
                        fieldReference.fieldInfo == fieldInfo ||
                        allowLocalCopies && v.variable() instanceof LocalVariableReference lvr &&
                                lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy &&
                                copy.localCopyOf().fieldInfo == fieldInfo
                );
    }

    public List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo, boolean allowLocalCopies) {
        return streamOfLatestInfoOfVariablesReferringTo(fieldInfo, allowLocalCopies).toList();
    }

    // next to this.field, and local copies, we also have fields with a non-this scope.
    // all values that contain the field itself get blocked;

    public List<VariableInfo> assignmentInfo(FieldInfo fieldInfo) {
        List<VariableInfo> normalValue = latestInfoOfVariablesReferringTo(fieldInfo, false);
        if (normalValue.isEmpty()) return normalValue;
        List<VariableInfo> result = new ArrayList<>();
        VariableInfo viThis = null;
        for (VariableInfo vi : normalValue) {
            if (vi.isAssigned()) {
                if (vi.variable() instanceof FieldReference fr && (fr.isStatic || fr.scopeIsThis())) {
                    assert viThis == null;
                    viThis = vi;
                } else {
                    result.add(vi);
                }
            }
        }
        if (viThis == null) return result;

        FieldReference fieldReference = new FieldReference(InspectionProvider.DEFAULT, fieldInfo);

        DelayedVariableExpression dve;
        boolean viThisHasDelayedValue = (dve = viThis.getValue().asInstanceOf(DelayedVariableExpression.class)) != null &&
                dve.variable() instanceof FieldReference fr && fr.fieldInfo == fieldInfo;
        boolean partialAssignment = viThis.getValue().variables().contains(fieldReference);
        if (viThisHasDelayedValue || !partialAssignment) {
            result.add(viThis);
            return result;
        }
        // else: do not at viThis to the result

        // search for all VI's that did an assignment without self-references
        Set<String> done = new HashSet<>();
        Set<String> added = new HashSet<>();
        for (VariableInfo normal : normalValue) {
            Iterator<String> it = normal.getAssignmentIds().idStream();
            while (it.hasNext()) {
                String assignmentId = it.next();
                String assignmentIndex = StringUtil.stripLevel(assignmentId);
                if (!done.contains(assignmentIndex) && !prefixAdded(assignmentIndex, added)) {
                    StatementAnalysis statementAnalysis = methodAnalysis.getFirstStatement().navigateTo(assignmentIndex);
                    VariableInfoContainer assignmentVic = statementAnalysis.variables.get(fieldInfo.fullyQualifiedName());
                    String level = StringUtil.level(assignmentId);
                    VariableInfo assignmentVi = EVALUATION.label.equals(level) ? assignmentVic.best(EVALUATION) : assignmentVic.best(MERGE);
                    Expression value = assignmentVi.getValue();
                    if (acceptForConditionalInitialization(fieldInfo, value)) {
                        added.add(assignmentIndex);
                        result.add(assignmentVi);
                    }
                    done.add(assignmentIndex);
                }
            }
        }
        return result;
    }

    private boolean prefixAdded(String assignmentIndex, Set<String> added) {
        return added.stream().anyMatch(assignmentIndex::startsWith);
    }

    private boolean acceptForConditionalInitialization(FieldInfo fieldInfo, Expression value) {
        List<Variable> variables = value.variables();
        return variables.stream().noneMatch(v -> v instanceof FieldReference fr && fieldInfo.equals(fr.fieldInfo));
    }

    public boolean containsMessage(Message.Label messageLabel) {
        return localMessageStream().anyMatch(message -> message.message() == messageLabel &&
                message.location().equals(location()));
    }

    @Override
    public DV getProperty(VariableProperty variableProperty) {
        throw new UnsupportedOperationException("? statements have no property");
    }

    @Override
    public Location location() {
        return new Location(methodAnalysis.getMethodInfo(), index, statement.getIdentifier());
    }

    // ****************************************************************************************

    /**
     * Before iteration 0, all statements: create what was already present higher up
     *
     * @param evaluationContext overview object for the analysis of this primary type
     * @param previous          the previous statement, or null if there is none (start of block)
     */
    public void initIteration0(EvaluationContext evaluationContext, MethodInfo currentMethod, StatementAnalysis previous) {
        if (previous == null) {
            // at the beginning of a block
            if (methodAnalysis.getMethodInfo().hasReturnValue()) {
                Variable retVar = new ReturnVariable(methodAnalysis.getMethodInfo());
                createVariable(evaluationContext, retVar, 0, VariableNature.METHOD_WIDE);
            }
            if (parent == null) {
                createParametersThisAndVariablesFromClosure(evaluationContext, currentMethod);
                return;
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.variables.stream()
                // never copy a return variable from the parent
                .filter(e -> previous != null || !(e.getValue().current().variable() instanceof ReturnVariable))
                .forEach(e -> copyVariableFromPreviousInIteration0(e,
                        previous == null, previous == null ? null : previous.index, false));

        flowData.initialiseAssignmentIds(copyFrom.flowData);
    }

    private void createParametersThisAndVariablesFromClosure(EvaluationContext evaluationContext, MethodInfo currentMethod) {
        // at the beginning of a method, we create parameters; also those from closures
        assert evaluationContext != null;
        EvaluationContext closure = evaluationContext;
        boolean inClosure = false;
        while (closure != null) {
            if (closure.getCurrentMethod() != null) {
                for (ParameterInfo parameterInfo : closure.getCurrentMethod().methodInspection.getParameters()) {
                    VariableNature variableNature = inClosure
                            ? VariableNature.FROM_ENCLOSING_METHOD : VariableNature.METHOD_WIDE;
                    createVariable(closure, parameterInfo, 0, variableNature);
                }
            }
            closure = closure.getClosure();
            inClosure = true;
        }
        // for now, other variations on this are not explicitly present at the moment IMPROVE?
        if (!currentMethod.methodInspection.get().isStatic()) {
            This thisVariable = new This(evaluationContext.getAnalyserContext(), currentMethod.typeInfo);
            createVariable(evaluationContext, thisVariable, 0, VariableNature.METHOD_WIDE);
        }

        // we'll copy local variables from outside this method
        // NOT a while statement, because this one will work recursively
        EvaluationContext closure4Local = evaluationContext.getClosure();
        if (closure4Local != null) {
            closure4Local.localVariableStream().forEach(e ->
                    copyVariableFromPreviousInIteration0(e,
                            true, null, true));
        }
    }

    private void copyVariableFromPreviousInIteration0(Map.Entry<String, VariableInfoContainer> entry,
                                                      boolean previousIsParent,
                                                      String indexOfPrevious,
                                                      boolean markCopyOfEnclosingMethod) {
        String fqn = entry.getKey();
        VariableInfoContainer vic = entry.getValue();
        VariableInfo vi = vic.current();
        Variable variable = vi.variable();
        VariableInfoContainer newVic;

        if (markCopyOfEnclosingMethod) {
            newVic = VariableInfoContainerImpl.copyOfExistingVariableInEnclosingMethod(location(),
                    vic, navigationData.hasSubBlocks());
        } else if (!vic.variableNature().isLocalVariableInLoopDefinedOutside()
                && statement instanceof LoopStatement && variable.isLocal()) {
            // as we move into a loop statement, the VariableInLoop is added to obtain local variable in loop defined outside
            // the variable itself will not be used anymore, only its "local copy" associated with the loop
            // however, the loop may turn out to be completely empty, in which case the initial value is kept
            // so we must keep the initial value
            newVic = VariableInfoContainerImpl.existingLocalVariableIntoLoop(vic, index, previousIsParent);
        } else if (vic.variableNature().doNotCopyToNextStatement(previousIsParent, indexOfPrevious, index)) {
            return; // skip
        } else {
            // make a simple reference copy; potentially resetting localVariableInLoopDefinedOutside
            newVic = VariableInfoContainerImpl.existingVariable(vic, index, previousIsParent, navigationData.hasSubBlocks());
        }
        variables.put(fqn, newVic);
    }

    /**
     * Before iterations 1+, with fieldAnalyses non-empty only potentially for the the first statement
     * of the method.
     *
     * @param evaluationContext overview object for the analysis of this primary type
     * @param previous          the previous statement, or null if there is none (start of block)
     */
    public void initIteration1Plus(EvaluationContext evaluationContext,
                                   StatementAnalysis previous) {

        /* the reason we do this for all statements in the method's block is that in a subsequent iteration,
         the first statements may already be DONE, so the code doesn't reach here!
         */
        if (parent == null) {
            init1PlusStartOfMethodDoParameters(evaluationContext.getAnalyserContext());
        }

        StatementAnalysis copyFrom = previous == null ? parent : previous;

        variables.toImmutableMap().values().forEach(vic -> {
            VariableInfo variableInfo = vic.current();
            if (variableInfo.variable() instanceof This thisVar) {
                fromTypeAnalyserIntoInitialThis(evaluationContext, vic, thisVar);
            }
            if (vic.isInitial()) {
                if (variableInfo.variable() instanceof FieldReference fieldReference) {
                    fromFieldAnalyserIntoInitial(evaluationContext, vic, fieldReference);
                }
            } else {
                if (vic.hasEvaluation()) vic.copy(); //otherwise, variable not assigned, not read
            }
        });
        if (copyFrom != null) {
            explicitlyPropagateVariables(copyFrom, previous == null);
        }
        // this comes AFTER explicitly propagating already existing local copies of confirmed variables
        variables.toImmutableMap().values().forEach(vic -> ensureLocalCopiesOfConfirmedVariableFields(evaluationContext, vic));
    }

    /* explicitly copy local variables from above or previous (they cannot be created on demand)
       loop variables at the statement are not copied to the next one
       Some fields only become visible in a later iteration (see e.g. Enum_3 test, field inside constant result
       of array initialiser) -- so we don't explicitly restrict to local variables

       we also exclude local copies from places where they do not belong, e.g., if a loop variable (loop at 1)
       is modified in 1.0.1.0.1, it creates a var$1$1_0_1_0_1-E; this variable has no reason of existence in
       the other branch 1.0.1.1.0

       ConditionalInitialization only goes to the next statement, never inside a block
     */
    private void explicitlyPropagateVariables(StatementAnalysis copyFrom, boolean copyIsParent) {
        copyFrom.variables.stream()
                .filter(e -> explicitlyPropagate(copyFrom, copyIsParent, e.getValue()))
                .forEach(e -> {
                    String fqn = e.getKey();
                    VariableInfoContainer vicFrom = e.getValue();
                    if (!variables.isSet(fqn)) {
                        VariableInfoContainer newVic = VariableInfoContainerImpl.existingVariable(vicFrom,
                                null, copyIsParent, navigationData.hasSubBlocks());
                        variables.put(fqn, newVic);
                    }
                });
    }

    private boolean explicitlyPropagate(StatementAnalysis copyFrom, boolean copyIsParent, VariableInfoContainer vic) {
        if (vic.variableNature() instanceof VariableNature.Pattern pattern) {
            return StringUtil.inScopeOf(pattern.scope(), index);
        }
        if (copyIsParent) {
            // see variableEntryStream(EVALUATION) -> ignore those that have merges but no eval; see e.g. Basics_7
            return !vic.hasMerge() || vic.hasEvaluation();
            // we'd only copy fields if they are used somewhere in the block. BUT there are "hidden" fields
            // such as local variables with an array initialiser containing fields as a value; conclusion: copy all, but don't merge unless used.
        }
        // don't continue local copies of loop variables beyond the loop
        return !copyFrom.index.equals(vic.variableNature().getStatementIndexOfThisLoopOrLoopCopyVariable());
    }


    /*
    Do not add IMMUTABLE to this set! (computed from external, formal, context)
     */
    public static final Set<VariableProperty> FROM_PARAMETER_ANALYSER_TO_PROPERTIES
            = Set.of(IDENTITY, EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE, MODIFIED_OUTSIDE_METHOD, CONTAINER);

    /*
    assume that all parameters, also those from closures, are already present
     */
    private void init1PlusStartOfMethodDoParameters(AnalyserContext analyserContext) {
        variables.stream().map(Map.Entry::getValue)
                .filter(vic -> vic.getPreviousOrInitial().variable() instanceof ParameterInfo)
                .forEach(vic -> {
                    VariableInfo prevInitial = vic.getPreviousOrInitial();
                    ParameterInfo parameterInfo = (ParameterInfo) prevInitial.variable();
                    updateValuePropertiesOfParameter(analyserContext, vic, prevInitial, parameterInfo);
                    ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                    for (VariableProperty variableProperty : FROM_PARAMETER_ANALYSER_TO_PROPERTIES) {
                        DV value = parameterAnalysis.getProperty(variableProperty);
                        vic.setProperty(variableProperty, value, INITIAL);
                    }
                });
    }

    /*
    variables that are not marked for assignment, get no update of their @Immutable property
    In the mean time, however, this value may have changed from DELAY to MUTABLE (as is the case in Modification_14)

     */
    private void updateValuePropertiesOfParameter(AnalyserContext analyserContext,
                                                  VariableInfoContainer vic,
                                                  VariableInfo vi,
                                                  Variable variable) {
        //update @Immutable
        assert variable instanceof ParameterInfo;
        DV currentImmutable = vi.getProperty(IMMUTABLE);
        if (currentImmutable.isDelayed()) {
            DV formalImmutable = variable.parameterizedType().defaultImmutable(analyserContext, false);
            vic.setProperty(IMMUTABLE, formalImmutable, INITIAL);
        }
        // update @Independent
        TypeInfo bestType = variable.parameterizedType().bestTypeInfo();
        if (bestType != null) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
            DV currentIndependent = vi.getProperty(VariableProperty.INDEPENDENT);
            if (currentIndependent.isDelayed()) {
                DV independent = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
                vic.setProperty(VariableProperty.INDEPENDENT, independent, INITIAL);
            }
        }
    }

    private void fromTypeAnalyserIntoInitialThis(EvaluationContext evaluationContext,
                                                 VariableInfoContainer vic,
                                                 This thisVar) {
        // only copy EXT_IMM
        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(thisVar.typeInfo);
        DV extImm = typeAnalysis.getProperty(IMMUTABLE);
        vic.setProperty(EXTERNAL_IMMUTABLE, extImm, INITIAL);
    }

    private void fromFieldAnalyserIntoInitial(EvaluationContext evaluationContext,
                                              VariableInfoContainer vic,
                                              FieldReference fieldReference) {
        VariableInfo viInitial = vic.best(INITIAL);

        // see if we can resolve a delay in statement time
        if (viInitial.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);
            DV effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal.isDone()) {
                vic.setStatementTime(effectivelyFinal.valueIsTrue() ?
                        VariableInfoContainer.NOT_A_VARIABLE_FIELD : flowData.getInitialTime());
            }
            // so from here on, isConfirmedVariableField may be set
        }
        boolean selfReference = inPartOfConstruction() && !(fieldReference.scopeIsThis());
        Map<VariableProperty, DV> map = fieldPropertyMap(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo);
        Map<VariableProperty, DV> combined = new HashMap<>(map);
        Expression initialValue;

        if (!viInitial.valueIsSet()) {
            // we don't have an initial value yet
            initialValue = initialValueOfField(evaluationContext, fieldReference, selfReference);
            Map<VariableProperty, DV> valueMap = evaluationContext.getValueProperties(initialValue);
            valueMap.forEach((k, v) -> combined.merge(k, v, DV::max));

            // copy into initial

            vic.setValue(initialValue, LinkedVariables.EMPTY, combined, true);
        } else {
            // only set properties copied from the field
            map.forEach((k, v) -> vic.setProperty(k, v, INITIAL));
            initialValue = viInitial.getValue();
            assert initialValue.isDone();
            // add the value properties from the current value to combined
            Map<VariableProperty, DV> valueMap = evaluationContext.getValueProperties(viInitial.getValue());
            valueMap.forEach((k, v) -> combined.merge(k, v, DV::max));
        }

        /* copy into evaluation, but only if there is no assignment and no reading

        reading can change the value (e.g. when a modifying method call occurs), but we have a dedicated
        method that reads from INITIAL rather than EVAL so we don't have to copy yet.

        for properties, which are incremental upon reading, we already copy into evaluation,
        because we don't have explicit code available
         */
        VariableInfo viEval = vic.best(VariableInfoContainer.Level.EVALUATION);
        // not assigned in this statement
        if (viEval != viInitial && vic.isNotAssignedInThisStatement()) {
            if (!viEval.valueIsSet() && !initialValue.isUnknown() && !viEval.isRead()) {
                // whatever we do, we do NOT write CONTEXT properties, because they are written exactly once at the
                // end of the apply phase, even for variables that aren't read
                combined.keySet().removeAll(GroupPropertyValues.PROPERTIES);
                vic.setValue(initialValue, viInitial.getLinkedVariables(), combined, false);
            }
        }
    }

    private static final Set<VariableProperty> FROM_FIELD_ANALYSER_TO_PROPERTIES
            = Set.of(FINAL, EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE, MODIFIED_OUTSIDE_METHOD);

    private void ensureLocalCopiesOfConfirmedVariableFields(EvaluationContext evaluationContext, VariableInfoContainer vic) {
        if (vic.hasEvaluation()) {
            VariableInfo eval = vic.best(VariableInfoContainer.Level.EVALUATION);
            VariableInfo initial = vic.getPreviousOrInitial();
            if (eval.variable() instanceof FieldReference fieldReference &&
                    initial.isConfirmedVariableField() && !eval.getReadAtStatementTimes().isEmpty()) {

                AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
                FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
                Map<VariableProperty, DV> propertyMap = FROM_FIELD_ANALYSER_TO_PROPERTIES.stream()
                        .collect(Collectors.toUnmodifiableMap(vp -> vp, fieldAnalysis::getProperty));
                LinkedVariables assignedToOriginal = LinkedVariables.of(fieldReference, LinkedVariables.STATICALLY_ASSIGNED_DV);

                for (int statementTime : eval.getReadAtStatementTimes()) {
                    LocalVariableReference localCopy = createCopyOfVariableField(fieldReference, initial, statementTime);
                    if (!variables.isSet(localCopy.fullyQualifiedName())) {
                        VariableInfoContainer lvrVic = VariableInfoContainerImpl.newLocalCopyOfVariableField(location(),
                                localCopy, index + INITIAL, navigationData.hasSubBlocks());
                        variables.put(localCopy.fullyQualifiedName(), lvrVic);
                        String assignmentIdOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);

                        Expression initialValue = statementTime == initial.getStatementTime() &&
                                initial.getAssignmentIds().getLatestAssignment().compareTo(assignmentIdOfStatementTime) >= 0 ?
                                initial.getValue() :
                                Instance.localCopyOfVariableField(index, fieldReference, analyserContext);
                        Map<VariableProperty, DV> valueMap = evaluationContext.getValueProperties(initialValue);
                        Map<VariableProperty, DV> combined = new HashMap<>(propertyMap);
                        valueMap.forEach((k, v) -> combined.merge(k, v, DV::max));
                        for (VariableProperty vp : GroupPropertyValues.PROPERTIES) {
                            combined.put(vp, vp == EXTERNAL_NOT_NULL
                                    || vp == EXTERNAL_IMMUTABLE ? MultiLevel.NOT_INVOLVED_DV : vp.falseDv);
                        }
                        lvrVic.setValue(initialValue, assignedToOriginal, combined, true);
                    }
                }
            }
        }
    }

    public void ensureMessages(Stream<Message> messageStream) {
        messageStream.forEach(this::ensure);
    }

    /*
    output the statement, but take into account the list of variables, there may be name clashes to be resolved

     */
    public OutputBuilder output(Qualification qualification) {
        return statement.output(qualification, this);
    }

    public boolean assignsToFields() {
        return variableStream().anyMatch(vi -> vi.variable() instanceof FieldReference && vi.isAssigned());
    }

    public boolean noIncompatiblePrecondition() {
        return !(methodLevelData.combinedPrecondition.isFinal()
                && methodLevelData.combinedPrecondition.get().expression().isBoolValueFalse());
    }

    public boolean haveLocalMessages() {
        return !messages.isEmpty();
    }

    public Stream<Message> localMessageStream() {
        return messages.stream().filter(m -> m.location().info.getMethod() == methodAnalysis.getMethodInfo());
    }

    public Stream<Message> messageStream() {
        return messages.stream();
    }

    LocalVariableReference createCopyOfVariableField(FieldReference fieldReference,
                                                     VariableInfo fieldVi,
                                                     int statementTime) {
        // a variable field can have any value when first read in a method.
        // after statement time goes up, this value may have changed completely
        // therefore we return a new local variable each time we read and statement time has gone up.

        // when there are assignments within the same statement time, however, we stick to the assigned value
        // (we temporarily treat the field as a local variable)
        // so we need to know: have there been assignments AFTER the latest statement time increase?

        String copyAssignmentId;
        String indexOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);
        if (statementTime == fieldVi.getStatementTime() &&
                fieldVi.getAssignmentIds().getLatestAssignment().compareTo(indexOfStatementTime) >= 0) {
            copyAssignmentId = fieldVi.getAssignmentIds().getLatestAssignment(); // double $
        } else {
            copyAssignmentId = null; // single $
        }

        VariableNature.CopyOfVariableField copy = new VariableNature.CopyOfVariableField(statementTime,
                copyAssignmentId, fieldReference);
        // the statement time of the field indicates the time of the latest assignment
        return createLocalCopy(fieldReference, copy);
    }

    LocalVariableReference createLocalLoopCopy(Variable original, String statementIndexOfLoop) {
        VariableNature.CopyOfVariableInLoop copy = new VariableNature.CopyOfVariableInLoop(statementIndexOfLoop, original);
        return createLocalCopy(original, copy);
    }

    private LocalVariableReference createLocalCopy(Variable original, VariableNature copy) {
        String lvSimple;
        if (original instanceof FieldReference fr && !fr.scopeIsThis()) {
            if (fr.scope == null) {
                lvSimple = (fr.isStatic ? fr.fieldInfo.owner.simpleName : "this") + "." + original.simpleName();
            } else if (fr.scope instanceof VariableExpression ve) {
                lvSimple = ve.variable().simpleName() + "." + original.simpleName();
            } else {
                lvSimple = fr.scope.minimalOutput() + "." + original.simpleName();
            }
        } else {
            lvSimple = original.simpleName();
        }
        LocalVariable localVariable = new LocalVariable.Builder()
                .addModifier(LocalVariableModifier.FINAL)
                .setName(original.fullyQualifiedName() + copy.suffix())
                .setSimpleName(lvSimple + copy.suffix())
                .setNature(copy)
                .setParameterizedType(original.parameterizedType())
                .setOwningType(methodAnalysis.getMethodInfo().typeInfo)
                .build();
        return new LocalVariableReference(localVariable);
    }

    public record ConditionAndLastStatement(Expression condition,
                                            String firstStatementIndexForOldStyleSwitch,
                                            StatementAnalyser lastStatement,
                                            boolean alwaysEscapes) {
    }

    public record ConditionAndVariableInfo(Expression condition,
                                           VariableInfo variableInfo,
                                           boolean alwaysEscapes,
                                           VariableNature variableNature,
                                           String firstStatementIndexForOldStyleSwitch,
                                           String indexOfLastStatement,
                                           String indexOfCurrentStatement,
                                           StatementAnalysis lastStatement,
                                           Variable myself,
                                           EvaluationContext evaluationContext) {
        // for testing
        public ConditionAndVariableInfo(Expression condition, VariableInfo variableInfo) {
            this(condition, variableInfo, false, VariableNature.METHOD_WIDE, null, "0", "-", null, variableInfo.variable(), null);
        }

        public Expression value() {
            Expression value = variableInfo.getVariableValue(myself);

            List<Variable> variables = value.variables();
            if (variables.isEmpty()) return value;
            Map<Variable, Expression> replacements = new HashMap<>();
            for (Variable variable : variables) {
                // Test 26 Enum 1 shows that the variable may not exist
                VariableInfoContainer vic = lastStatement.variables.getOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null && !vic.variableNature().acceptForSubBlockMerging(indexOfCurrentStatement)) {
                    Expression currentValue = vic.current().getValue();
                    replacements.put(variable, currentValue);
                }
            }
            if (replacements.isEmpty()) return value;

            if (value.isDelayed()) {
                return DelayedExpression.forMerge(variableInfo.variable().parameterizedType(),
                        variableInfo.getLinkedVariables().changeAllToDelay(value.causesOfDelay()), value.causesOfDelay());
            }
            Map<VariableProperty, DV> valueProperties = evaluationContext.getValueProperties(value);
            return Instance.genericMergeResult(indexOfCurrentStatement, variableInfo.variable(), valueProperties);
        }
    }

    private record AcceptForMerging(VariableInfoContainer vic, boolean accept) {
        // useful for debugging
        @Override
        public String toString() {
            return vic.current().variable().fullyQualifiedName() + ":" + accept;
        }
    }

    /**
     * From child blocks into the parent block; determine the value and properties for the current statement
     *
     * @param evaluationContext       for expression evaluations
     * @param lastStatements          the last statement of each of the blocks
     * @param atLeastOneBlockExecuted true if we can (potentially) discard the current value
     * @param statementTime           the statement time of subBlocks
     */
    public AnalysisStatus copyBackLocalCopies(EvaluationContext evaluationContext,
                                              Expression stateOfConditionManagerBeforeExecution,
                                              List<ConditionAndLastStatement> lastStatements,
                                              boolean atLeastOneBlockExecuted,
                                              int statementTime) {

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        // first, per variable

        // we copy to a list, because we may be adding variables (ConditionalInitialization copies)
        List<AcceptForMerging> variableStream = makeVariableStream(lastStatements).toList();
        Set<String> merged = new HashSet<>();
        Map<Variable, LinkedVariables> linkedVariablesMap = new HashMap<>();
        Set<Variable> variablesWhereMergeOverwrites = new HashSet<>();

        for (AcceptForMerging e : variableStream) {
            VariableInfoContainer vic = e.vic();
            VariableInfo current = vic.current();
            Variable variable = current.variable();
            String fqn = variable.fullyQualifiedName();

            if (e.accept) {
                // the variable stream comes from multiple blocks; we ensure that merging takes place once only
                if (merged.add(fqn)) {
                    VariableInfoContainer destination;
                    if (!variables.isSet(fqn)) {
                        VariableNature nature = vic.variableNature();
                        // created in merge: see Enum_1, a dependent variable created inside the loop
                        // FIXME only for very special cases!
                        // VariableNature newNature = nature instanceof VariableNature.NormalLocalVariable
                        //        ? VariableNature.CREATED_IN_MERGE : nature;
                        destination = createVariable(evaluationContext, variable, statementTime, nature);
                        if (variable.needsNewVariableWithoutValueCall()) destination.newVariableWithoutValue();
                    } else {
                        destination = vic;
                    }
                    boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;

                    List<ConditionAndVariableInfo> toMerge = lastStatements.stream()
                            .filter(e2 -> e2.lastStatement.statementAnalysis.variables.isSet(fqn))
                            .map(e2 -> {
                                VariableInfoContainer vic2 = e2.lastStatement.statementAnalysis.variables.get(fqn);
                                return new ConditionAndVariableInfo(e2.condition,
                                        vic2.current(), e2.alwaysEscapes,
                                        vic2.variableNature(), e2.firstStatementIndexForOldStyleSwitch,
                                        e2.lastStatement.statementAnalysis.index,
                                        index,
                                        e2.lastStatement.statementAnalysis,
                                        variable,
                                        evaluationContext);
                            })
                            .filter(cav -> acceptVariableForMerging(cav, inSwitchStatementOldStyle)).toList();
                    boolean ignoreCurrent;
                    if (toMerge.size() == 1 && (toMerge.get(0).variableNature.ignoreCurrent(index) && !atLeastOneBlockExecuted ||
                            variable instanceof FieldReference fr && onlyOneCopy(evaluationContext, fr)) ||
                            destination.variableNature() == VariableNature.CREATED_IN_MERGE) {
                        ignoreCurrent = true;
                    } else {
                        ignoreCurrent = atLeastOneBlockExecuted;
                    }
                    if (toMerge.size() > 0) {
                        try {
                            destination.merge(evaluationContext, stateOfConditionManagerBeforeExecution, ignoreCurrent,
                                    toMerge, groupPropertyValues);

                            LinkedVariables linkedVariables = toMerge.stream().map(cav -> cav.variableInfo.getLinkedVariables())
                                    .reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
                            linkedVariablesMap.put(variable, linkedVariables);

                            if (ignoreCurrent) variablesWhereMergeOverwrites.add(variable);

                            if (atLeastOneBlockExecuted &&
                                    checkForOverwritingPreviousAssignment(variable, current, vic.variableNature(), toMerge)) {
                                ensure(Message.newMessage(new Location(methodAnalysis.getMethodInfo(), index, statement.getIdentifier()),
                                        Message.Label.OVERWRITING_PREVIOUS_ASSIGNMENT, variable.simpleName()));
                            }
                        } catch (Throwable throwable) {
                            LOGGER.warn("Caught exception while merging variable {} in {}, {}", fqn,
                                    methodAnalysis.getMethodInfo().fullyQualifiedName, index);
                            throw throwable;
                        }
                    } else if (destination.hasMerge()) {
                        assert evaluationContext.getIteration() > 0; // or it wouldn't have had a merge
                        // in previous iterations there was data for us, but now there isn't; copy from I/E into M
                        destination.copyFromEvalIntoMerge(groupPropertyValues);
                    } else {
                        for (VariableProperty variableProperty : GroupPropertyValues.PROPERTIES) {
                            groupPropertyValues.set(variableProperty, variable, current.getProperty(variableProperty));
                        }
                    }
                }
            } else {
                for (VariableProperty variableProperty : GroupPropertyValues.PROPERTIES) {
                    groupPropertyValues.set(variableProperty, variable, current.getProperty(variableProperty));
                }
                // the !merged check here is because some variables appear 2x, once with a positive accept,
                // and the second time from inside the block with a negative one
                // if (!merged.contains(fqn)) doNotWrite.add(variable);
            }

            // CNN_FOR_PARENT overwrite
            lastStatements.stream().filter(cal -> cal.lastStatement.statementAnalysis.variables.isSet(fqn)).forEach(cal -> {
                VariableInfoContainer calVic = cal.lastStatement.statementAnalysis.variables.get(fqn);
                VariableInfo calVi = calVic.best(EVALUATION);
            /*    DV cnn4ParentDelay = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY);
                DV cnn4ParentDelayResolved = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED);
                if (cnn4ParentDelay.valueIsTrue() && !cnn4ParentDelayResolved.valueIsTrue()) {
                    CausesOfDelay delay = new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(calVi.variable(),
                            location(), CauseOfDelay.Cause.CNN_PARENT)); // TODO improve this system!
                    groupPropertyValues.set(VariableProperty.CONTEXT_NOT_NULL, calVi.variable(), delay);
                } else {
                    DV cnn4Parent = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT);
                    if (cnn4Parent.isDone())
                        groupPropertyValues.set(VariableProperty.CONTEXT_NOT_NULL, calVi.variable(), cnn4Parent);
                }

             */
            });
        }

        // then, per cluster of variables
        Function<Variable, LinkedVariables> linkedVariablesFromBlocks =
                v -> linkedVariablesMap.getOrDefault(v, LinkedVariables.EMPTY);
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(this, MERGE,
                v -> !linkedVariablesMap.containsKey(v),
                variablesWhereMergeOverwrites,
                linkedVariablesFromBlocks, evaluationContext);
        computeLinkedVariables.writeLinkedVariables();

        CausesOfDelay ennStatus = computeLinkedVariables.write(EXTERNAL_NOT_NULL,
                groupPropertyValues.getMap(EXTERNAL_NOT_NULL));

        CausesOfDelay cnnStatus = computeLinkedVariables.write(CONTEXT_NOT_NULL,
                groupPropertyValues.getMap(CONTEXT_NOT_NULL));

        CausesOfDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        CausesOfDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE,
                groupPropertyValues.getMap(CONTEXT_IMMUTABLE));

        CausesOfDelay cmStatus = computeLinkedVariables.write(CONTEXT_MODIFIED,
                groupPropertyValues.getMap(CONTEXT_MODIFIED));

        return ennStatus.merge(cnnStatus).merge(cmStatus).merge(extImmStatus).merge(cImmStatus);
    }

    private boolean checkForOverwritingPreviousAssignment(Variable variable,
                                                          VariableInfo initial,
                                                          VariableNature variableNature,
                                                          List<ConditionAndVariableInfo> toMerge) {
        String fqn = variable.fullyQualifiedName();
        if (!(variable instanceof LocalVariableReference)) return false;
        if (variableNature instanceof VariableNature.LoopVariable ||
                variableNature instanceof VariableNature.Pattern) return false;
        if (initial.notReadAfterAssignment(index)) {
            // so now we know it is a local variable, it has been assigned to outside the sub-blocks, but not yet read
            int countAssignments = 0;
            for (ConditionAndVariableInfo cav : toMerge) {
                VariableInfoContainer localVic = cav.lastStatement.variables.getOrDefaultNull(fqn);
                if (localVic != null) {
                    VariableInfo current = localVic.current();
                    if (!current.isAssigned()) {
                        if (!current.isRead()) continue;
                        return false;
                    }
                    String assignmentIndex = current.getAssignmentIds().getLatestAssignmentIndex();
                    if (assignmentIndex.compareTo(index) < 0) continue;
                    String earliestAssignmentIndex = current.getAssignmentIds().getEarliestAssignmentIndex();
                    if (earliestAssignmentIndex.compareTo(index) < 0) {
                        // some branch is still relying on the earlier value
                        return false;
                    }

                    countAssignments++;
                    StatementAnalysis sa = navigateTo(assignmentIndex);
                    assert sa != null;
                    if (!sa.flowData.getGuaranteedToBeReachedInCurrentBlock().equals(FlowData.ALWAYS)) return false;
                    if (current.isRead()) {
                        if (current.getReadId().compareTo(current.getAssignmentIds().getLatestAssignment()) < 0) {
                            return false;
                        }
                        // so there is reading AFTER... but
                        // we'll need to double check that there was no reading before the assignment!
                        // secondly, we want to ensure that the assignment takes place unconditionally in the block

                        VariableInfoContainer atAssignment = sa.variables.get(fqn);
                        VariableInfo vi1 = atAssignment.current();
                        assert vi1.isAssigned();
                        // <= here instead of <; solves e.g. i+=1 (i = i + 1, read first, then assigned, same stmt)
                        if (vi1.isRead() && vi1.getReadId().compareTo(vi1.getAssignmentIds().getLatestAssignment()) <= 0) {
                            return false;
                        }

                        // else: assignment was before this merge... no bother; any reading will be after or not our problem
                    }
                }
            }
            return countAssignments > 0; // if not assigned, not read... just ignore
        }
        return false;
    }

    // almost identical code in statement analyser; serves a different purpose though
    private StatementAnalysis navigateTo(String target) {
        if (index.equals(target)) return this;
        if (target.startsWith(index)) {
            // go into sub-block
            int n = index.length();
            int blockIndex = Integer.parseInt(target.substring(n + 1, target.indexOf('.', n + 1)));
            StatementAnalysis inSub = navigationData.blocks.get().get(blockIndex)
                    .orElseThrow(() -> new UnsupportedOperationException("Looking for " + target + ", block " + blockIndex));
            return inSub.navigateTo(target);
        }
        if (index.compareTo(target) < 0 && navigationData.next.get().isPresent()) {
            return navigationData.next.get().get().navigateTo(target);
        }
        throw new UnsupportedOperationException("? have index " + index + ", looking for " + target);
    }

    private boolean onlyOneCopy(EvaluationContext evaluationContext, FieldReference fr) {
        if (fr.fieldInfo.isExplicitlyFinal()) return true;
        FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
        return fieldAnalysis.getProperty(FINAL).valueIsTrue();
    }

    private boolean acceptVariableForMerging(ConditionAndVariableInfo cav, boolean inSwitchStatementOldStyle) {
        if (inSwitchStatementOldStyle) {
            assert cav.firstStatementIndexForOldStyleSwitch != null;
            // if the variable is assigned in the block, it has to be assigned after the first index
            // "the block" is the switch statement; otherwise,
            String cavLatest = cav.variableInfo.getAssignmentIds().getLatestAssignmentIndex();
            if (cavLatest.compareTo(index) > 0) {
                return cav.firstStatementIndexForOldStyleSwitch.compareTo(cavLatest) <= 0;
            }
            return cav.firstStatementIndexForOldStyleSwitch.compareTo(cav.variableInfo.getReadId()) <= 0;
        }
        return cav.variableInfo.isRead() || cav.variableInfo.isAssigned();
    }

    // explicitly ignore loop and shadow loop variables, they should not exist beyond the statement ->
    //  !index.equals(vic.getStatementIndexOfThisLoopOrShadowVariable()))

    // return a stream of all variables that need merging up
    // note: .distinct() may not work
    private Stream<AcceptForMerging> makeVariableStream(List<ConditionAndLastStatement> lastStatements) {
        return Stream.concat(variables.stream().map(e -> new AcceptForMerging(e.getValue(),
                        e.getValue().variableNature().acceptVariableForMerging(index))),
                lastStatements.stream().flatMap(st -> st.lastStatement.statementAnalysis.variables.stream().map(e ->
                        new AcceptForMerging(e.getValue(), e.getValue().variableNature().acceptForSubBlockMerging(index))))
        );
    }

    /*
    create a variable, potentially even assign an initial value and a linked variables set.
    everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
     */
    public VariableInfoContainer createVariable(EvaluationContext evaluationContext,
                                                Variable variable,
                                                int statementTime,
                                                VariableNature variableInLoop) {
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) {
            throw new UnsupportedOperationException("Already exists: " +
                    fqn + " in " + index + ", " + methodAnalysis.getMethodInfo().fullyQualifiedName);
        }

        int statementTimeForVariable = statementTimeForVariable(analyserContext, variable, statementTime);
        VariableInfoContainer vic = VariableInfoContainerImpl.newVariable(location(), variable, statementTimeForVariable,
                variableInLoop, navigationData.hasSubBlocks());
        variables.put(variable.fullyQualifiedName(), vic);

        // linked variables travel from the parameters via the statements to the fields
        if (variable instanceof ReturnVariable returnVariable) {
            initializeReturnVariable(vic, evaluationContext.getAnalyserContext(), returnVariable);

        } else if (variable instanceof This thisVar) {
            initializeThis(vic, evaluationContext.getAnalyserContext(), thisVar);

        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            initializeParameter(vic, evaluationContext, parameterInfo);

        } else if (variable instanceof FieldReference fieldReference) {
            Expression initialValue = initialValueOfField(evaluationContext, fieldReference, false);

            // from field analyser
            // EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE,
            // initialises CONTEXT*
            Map<VariableProperty, DV> propertyMap = fieldPropertyMap(analyserContext, fieldReference.fieldInfo);

            // from initial value
            // IDENTITY, IMMUTABLE,CONTAINER, NOT_NULL_EXPRESSION, INDEPENDENT
            Map<VariableProperty, DV> valueProperties = evaluationContext.getValueProperties(initialValue);

            vic.setValue(initialValue, LinkedVariables.EMPTY, propertyMap, true);
            valueProperties.forEach((k, v) -> vic.setProperty(k, v, false, INITIAL));
        }
        return vic;
    }

    private void initializeReturnVariable(VariableInfoContainer vic, AnalyserContext analyserContext, ReturnVariable returnVariable) {
        DV defaultNotNull = methodAnalysis.getMethodInfo().returnType().defaultNotNull();
        Map<VariableProperty, DV> properties = sharedContext(defaultNotNull);
        properties.put(NOT_NULL_EXPRESSION, defaultNotNull);
        properties.put(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV);
        properties.put(EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED_DV);

        properties.put(IDENTITY, IDENTITY.falseDv);
        properties.put(CONTAINER, CONTAINER.falseDv);
        properties.put(IMMUTABLE, MUTABLE_DV);

        UnknownExpression value = new UnknownExpression(returnVariable.returnType, UnknownExpression.RETURN_VALUE);
        vic.setValue(value, LinkedVariables.EMPTY, properties, true);
    }

    private void initializeThis(VariableInfoContainer vic, AnalyserContext analyserContext, This thisVar) {
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(thisVar.typeInfo);

        // context properties
        Map<VariableProperty, DV> properties = sharedContext(MultiLevel.EFFECTIVELY_NOT_NULL_DV);

        // value properties
        properties.put(CONTAINER, CONTAINER.falseDv);
        properties.put(IMMUTABLE, IMMUTABLE.falseDv);
        // we do not keep the @NotNull status of a type
        properties.put(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);

        // external: only one relevant
        properties.put(EXTERNAL_IMMUTABLE, typeAnalysis.getProperty(IMMUTABLE));
        properties.put(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV);

        Instance value = Instance.forCatchOrThis(index, thisVar, analyserContext);
        vic.setValue(value, LinkedVariables.EMPTY, properties, true);
    }

    private void initializeParameter(VariableInfoContainer vic, EvaluationContext evaluationContext, ParameterInfo parameterInfo) {
        ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);

        // start with context properties
        Map<VariableProperty, DV> properties = sharedContext(parameterInfo.parameterizedType.defaultNotNull());

        // the value properties are not delayed (there's an assertion in the Instance factory method)
        DV notNull = parameterAnalysis.getProperty(NOT_NULL_PARAMETER)
                .maxIgnoreDelay(parameterInfo.parameterizedType.defaultNotNull());
        properties.put(NOT_NULL_EXPRESSION, notNull);

        DV formallyImmutable = parameterInfo.parameterizedType.defaultImmutable(evaluationContext.getAnalyserContext(), false);
        DV immutable = parameterAnalysis.getProperty(IMMUTABLE).max(formallyImmutable)
                .replaceDelayBy(MUTABLE_DV);
        properties.put(IMMUTABLE, immutable);

        DV formallyIndependent = parameterInfo.parameterizedType.defaultIndependent(evaluationContext.getAnalyserContext());
        DV independent = parameterAnalysis.getProperty(INDEPENDENT).max(formallyIndependent)
                .replaceDelayBy(MultiLevel.DEPENDENT_DV);
        properties.put(INDEPENDENT, independent);

        DV container = parameterAnalysis.getProperty(CONTAINER);
        properties.put(CONTAINER, container);

        boolean identity = parameterInfo.index == 0;
        properties.put(IDENTITY, Level.fromBoolDv(identity));

        // th external properties are delayed, but they're delayed in the correct way!
        DV extNotNull = parameterAnalysis.getProperty(EXTERNAL_NOT_NULL);
        assert extNotNull.isDelayed();
        properties.put(EXTERNAL_NOT_NULL, extNotNull);
        DV extImm = parameterAnalysis.getProperty(EXTERNAL_IMMUTABLE);
        assert extImm.isDelayed();
        properties.put(EXTERNAL_IMMUTABLE, extImm);
        DV mom = parameterAnalysis.getProperty(MODIFIED_METHOD);
        assert mom.isDelayed();
        properties.put(MODIFIED_OUTSIDE_METHOD, mom);

        Expression value = Instance.initialValueOfParameter(parameterInfo, notNull, immutable, independent, container, identity);
        vic.setValue(value, LinkedVariables.EMPTY, properties, true);
    }

    public int statementTimeForVariable(AnalyserContext analyserContext, Variable variable, int statementTime) {
        if (variable instanceof FieldReference fieldReference) {
            boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                    MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
            if (inPartOfConstruction) return VariableInfoContainer.NOT_A_VARIABLE_FIELD;

            DV effectivelyFinal = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getProperty(VariableProperty.FINAL);
            if (effectivelyFinal.isDelayed()) {
                return VariableInfoContainer.VARIABLE_FIELD_DELAY;
            }
            if (effectivelyFinal.valueIsFalse()) {
                return statementTime;
            }
        }
        return VariableInfoContainer.NOT_A_VARIABLE_FIELD;
    }

    private Map<VariableProperty, DV> sharedContext(DV contextNotNull) {
        Map<VariableProperty, DV> result = new HashMap<>();
        result.put(CONTEXT_NOT_NULL, contextNotNull);
        result.put(CONTEXT_IMMUTABLE, MUTABLE_DV);
        result.put(CONTEXT_MODIFIED, Level.FALSE_DV);
        return result;
    }
    
    private Map<VariableProperty, DV> fieldPropertyMap(AnalyserContext analyserContext,
                                                       FieldInfo fieldInfo) {
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
        Map<VariableProperty, DV> result = sharedContext(fieldInfo.type.defaultNotNull());

        for (VariableProperty vp : FROM_FIELD_ANALYSER_TO_PROPERTIES) {
            DV value = fieldAnalysis.getFieldProperty(analyserContext, fieldInfo, fieldInfo.type.bestTypeInfo(), vp);
            // IMPROVE we're not passing on 'our' analyserContext instead relying on that of the field, which does not know the lambda we're in at the moment
            result.put(vp, value);
        }
        return result;
    }

    private boolean inPartOfConstruction() {
        return methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
    }

    // FIXME should be much simpler
    private Expression initialValueOfField(EvaluationContext evaluationContext,
                                           FieldReference fieldReference,
                                           boolean selfReference) {
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        // rather than fieldAnalysis.getLinkedVariables

        boolean myOwn = fieldReference.scopeOwnerIs(methodAnalysis.getMethodInfo().typeInfo);

        if (inPartOfConstruction() && myOwn && !fieldReference.fieldInfo.isStatic()) { // instance field that must be initialised
            Expression initialValue = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getInitializerValue();
            if (initialValue.isDelayed()) { // initialiser value not yet evaluated
                return initialValue;
            }
            if (initialValue.isConstant()) {
                return initialValue;
            }
            if (initialValue.isInstanceOf(Instance.class)) return initialValue;

            // TODO will crash when notNull==-1
            return Instance.initialValueOfFieldPartOfConstruction(index, evaluationContext, fieldReference);
        }

        DV effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal.isDelayed() && !selfReference) {
            return DelayedVariableExpression.forField(fieldReference, effectivelyFinal.causesOfDelay());
        }
        // when selfReference (as in this.x = other.x during construction), we never delay


        Expression efv = fieldAnalysis.getValue();
        if (efv.isDelayed()) {
            if (analyserContext.getTypeAnalysis(fieldReference.fieldInfo.owner).isNotContracted() && !selfReference) {
                return DelayedVariableExpression.forField(fieldReference, efv.causesOfDelay());
            }
        } else {
            if (efv.isConstant()) {
                return efv;
            }
            Instance instance;
            if ((instance = efv.asInstanceOf(Instance.class)) != null) {
                return instance;
            }
        }

        DV notNull = fieldAnalysis.getProperty(EXTERNAL_NOT_NULL);
        return Instance.initialValueOfExternalVariableField(fieldReference, index, notNull, analyserContext);
    }

    public int statementTime(VariableInfoContainer.Level level) {
        return switch (level) {
            case INITIAL -> flowData.getInitialTime();
            case EVALUATION -> flowData.getTimeAfterEvaluation();
            case MERGE -> flowData.getTimeAfterSubBlocks();
        };
    }

    /**
     * Find a variable for reading. Intercepts variable fields and local variables.
     * This is the general method that must be used by the evaluation context, currentInstance, currentValue
     *
     * @param variable the variable
     * @return the most current variable info object
     */
    public VariableInfo initialValueForReading(@NotNull Variable variable,
                                               int statementTime,
                                               boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        if (!variables.isSet(fqn)) {
            assert !(variable instanceof ParameterInfo) : "Parameter " + variable.fullyQualifiedName() +
                    " should be known in " + methodAnalysis.getMethodInfo().fullyQualifiedName + ", statement " + index;
            return new VariableInfoImpl(variable); // no value, no state; will be created by a MarkRead
        }
        VariableInfoContainer vic = variables.get(fqn);
        VariableInfo vi = vic.getPreviousOrInitial();
        if (isNotAssignmentTarget) {
            if (vi.variable() instanceof FieldReference fieldReference) {
                if (vi.isConfirmedVariableField()) {
                    LocalVariableReference copy = createCopyOfVariableField(fieldReference, vi, statementTime);
                    if (!variables.isSet(copy.fullyQualifiedName())) {
                        // it is possible that the field has been assigned to, so it exists, but the local copy does not yet
                        return new VariableInfoImpl(variable);
                    }
                    return variables.get(copy.fullyQualifiedName()).getPreviousOrInitial();
                }
                if (vi.statementTimeDelayed()) {
                    return new VariableInfoImpl(variable);
                }
            }
            if (vic.variableNature().isLocalVariableInLoopDefinedOutside()) {
                StatementAnalysis relevantLoop = mostEnclosingLoop();
                if (relevantLoop.localVariablesAssignedInThisLoop.isFrozen()) {
                    if (relevantLoop.localVariablesAssignedInThisLoop.contains(fqn)) {
                        LocalVariableReference localCopy = createLocalLoopCopy(vi.variable(), relevantLoop.index);
                        // at this point we are certain the local copy exists
                        VariableInfoContainer newVic = variables.get(localCopy.fullyQualifiedName());
                        return newVic.getPreviousOrInitial();
                    }
                    return vi; // we don't participate in the modification process?
                }
                return new VariableInfoImpl(variable); // no value, no state
            }
        } // else we need to go to the variable itself
        return vi;
    }

    private StatementAnalysis mostEnclosingLoop() {
        StatementAnalysis sa = this;
        while (sa != null) {
            if (sa.statement instanceof LoopStatement) {
                return sa;//.localVariablesAssignedInThisLoop.isFrozen() && sa.localVariablesAssignedInThisLoop.contains(variableFqn);
            }
            sa = sa.parent;
        }
        throw new UnsupportedOperationException();
    }


    public record FindLoopResult(StatementAnalysis statementAnalysis, int steps) {
    }

    /*
    We've encountered a break or continue statement, and need to find the corresponding loop...
     */
    public FindLoopResult findLoopByLabel(BreakOrContinueStatement breakOrContinue) {
        StatementAnalysis sa = this;
        int cnt = 0;
        while (sa != null) {
            if (sa.statement instanceof LoopStatement loop &&
                    (!breakOrContinue.hasALabel() || loop.label != null && loop.label.equals(breakOrContinue.label))) {
                return new FindLoopResult(sa, cnt);
            }
            sa = sa.parent;
            cnt++;
        }
        throw new UnsupportedOperationException();
    }

    /**
     * helper method
     *
     * @param variable the variable
     * @return the most current variable info object
     * @throws IllegalArgumentException when the variable does not yet exist
     */
    public Expression initialValueOfReturnVariable(@NotNull Variable variable) {
        assert methodAnalysis.getMethodInfo().hasReturnValue();
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOrDefaultNull(fqn);
        if (vic == null) {
            throw new IllegalArgumentException("Cannot find " + variable + " in " + index);
        }
        return vic.getPreviousOrInitial().getValue();
    }

    /**
     * for reading, helper method; not for general use
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrNull(@NotNull Variable variable, VariableInfoContainer.Level level) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOrDefaultNull(fqn);
        if (vic == null) return null;
        return vic.best(level);
    }

    public VariableInfoContainer findOrNull(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        return variables.getOrDefaultNull(fqn);
    }

    /**
     * for reading, helper method; not for general use
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrThrow(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOrDefaultNull(fqn);
        if (vic == null)
            throw new UnsupportedOperationException("Have not yet evaluated " + variable.fullyQualifiedName());
        return vic.current();
    }

    public boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        if (!variables.isSet(variableName)) return false;
        VariableInfoContainer vic = variables.get(variableName);
        if (vic.variableNature().isLocalVariableInLoopDefinedOutside()) return false;
        VariableInfo variableInfo = vic.current();
        if (!variableInfo.variable().isLocal()) return false;
        if (parent == null) return true;
        return !parent.variables.isSet(variableName);
    }

    /**
     * this method assumes that the variable already exists!
     *
     * @param variableName the variable's fully qualified name
     * @return the container
     */
    public VariableInfoContainer findForWriting(@NotNull String variableName) {
        return variables.get(variableName);
    }

    /*
    will cause errors if variable does not exist yet!
    before you write, you'll have to ensureEvaluation
     */
    public VariableInfoContainer findForWriting(@NotNull Variable variable) {
        return variables.get(variable.fullyQualifiedName());
    }

    public Stream<VariableInfo> variableStream() {
        return variables.stream().map(Map.Entry::getValue).map(VariableInfoContainer::current);
    }

    public Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(VariableInfoContainer.Level level) {
        return variables.stream()
                // if in EVALUATION, ignore those that have a merge but no evaluation
                .filter(e -> level != EVALUATION || e.getValue().hasEvaluation() || !e.getValue().hasMerge());
    }


    public Expression notNullValuesAsExpression(EvaluationContext evaluationContext) {
        return And.and(evaluationContext, variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference
                        && vi.isAssigned()
                        && index().equals(vi.getAssignmentIds().getLatestAssignmentIndex()))
                .map(vi -> {
                    if (vi.variable() instanceof FieldReference fieldReference) {
                        if (vi.getValue() instanceof NullConstant) {
                            return new Pair<>(vi, Level.NOT_INVOLVED_DV);
                        }
                        DV notNull = evaluationContext.getProperty(new VariableExpression(fieldReference),
                                NOT_NULL_EXPRESSION, false, false);
                        return new Pair<>(vi, notNull);
                    }
                    return null;
                })
                .filter(e -> e != null && (e.v.equals(Level.NOT_INVOLVED_DV) || e.v.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)))
                .map(e -> {
                    Expression equals = Equals.equals(evaluationContext, new VariableExpression(e.k.variable()),
                            NullConstant.NULL_CONSTANT);
                    if (e.v.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                        return Negation.negate(evaluationContext, equals);
                    }
                    return equals;
                })
                .toArray(Expression[]::new));
    }
}
