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
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.*;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
import static org.e2immu.analyser.util.Logger.log;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container
public class StatementAnalysis extends AbstractAnalysisBuilder implements Comparable<StatementAnalysis>, HasNavigationData<StatementAnalysis> {

    public final Statement statement;
    public final String index;
    public final StatementAnalysis parent;
    public final boolean inSyncBlock;
    public final MethodAnalysis methodAnalysis;

    public final AddOnceSet<Message> messages = new AddOnceSet<>();
    public final NavigationData<StatementAnalysis> navigationData = new NavigationData<>();
    public final SetOnceMap<String, VariableInfoContainer> variables = new SetOnceMap<>();
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData;
    public final FlowData flowData = new FlowData();
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
        return followReplacements().navigationData.next.get().map(statementAnalysis -> {
            if (statementAnalysis.flowData.isUnreachable()) {
                return this;
            }
            return statementAnalysis.lastStatement();
        }).orElse(this);
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
            statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
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
                .filter(v -> v.variable() instanceof FieldReference fieldReference && fieldReference.fieldInfo == fieldInfo ||
                        allowLocalCopies && v.variable() instanceof LocalVariableReference lvr &&
                                lvr.variable.isLocalCopyOf() instanceof FieldReference fr && fr.fieldInfo == fieldInfo
                );
    }

    public List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo, boolean allowLocalCopies) {
        return streamOfLatestInfoOfVariablesReferringTo(fieldInfo, allowLocalCopies).collect(Collectors.toUnmodifiableList());
    }

    public boolean containsMessage(String messageString) {
        return messages.stream().anyMatch(message -> message.message.contains(messageString) && message.location.equals(location()));
    }

    @Override
    public AnnotationMode annotationMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider,
                                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Location location() {
        return new Location(methodAnalysis.getMethodInfo(), index);
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
                createReturnVariableAtBeginningOfEachBlock(evaluationContext);
            }
            if (parent == null) {
                createParametersThisAndVariablesFromClosure(evaluationContext, currentMethod);
                return;
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.variableEntryStream()
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
                    VariableInLoop variableInLoop = inClosure ? VariableInLoop.COPY_FROM_ENCLOSING_METHOD : VariableInLoop.NOT_IN_LOOP;
                    createVariable(closure, parameterInfo, 0, variableInLoop);
                }
            }
            closure = closure.getClosure();
            inClosure = true;
        }
        // for now, other variations on this are not explicitly present at the moment IMPROVE?
        if (!currentMethod.methodInspection.get().isStatic()) {
            This thisVariable = new This(evaluationContext.getAnalyserContext(), currentMethod.typeInfo);
            createVariable(evaluationContext, thisVariable, 0, VariableInLoop.NOT_IN_LOOP);
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

    private void createReturnVariableAtBeginningOfEachBlock(EvaluationContext evaluationContext) {
        Variable retVar = new ReturnVariable(methodAnalysis.getMethodInfo());
        VariableInfoContainer vic = createVariable(evaluationContext, retVar, 0, VariableInLoop.NOT_IN_LOOP);
        READ_FROM_RETURN_VALUE_PROPERTIES.forEach(vp ->
                vic.setProperty(vp, vp.falseValue, INITIAL));
        int notNull = Primitives.isPrimitiveExcludingVoid(methodAnalysis.getMethodInfo().returnType()) ?
                MultiLevel.EFFECTIVELY_NOT_NULL : MultiLevel.NULLABLE;
        vic.setProperty(CONTEXT_NOT_NULL, notNull, INITIAL);
        vic.setProperty(NOT_NULL_EXPRESSION, notNull, INITIAL);
        vic.setProperty(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED, INITIAL);
        vic.setProperty(CONTEXT_MODIFIED, Level.FALSE, INITIAL);
    }

    private void copyVariableFromPreviousInIteration0(Map.Entry<String, VariableInfoContainer> entry,
                                                      boolean previousIsParent,
                                                      String indexOfPrevious,
                                                      boolean markCopyOfEnclosingMethod) {
        String fqn = entry.getKey();
        VariableInfoContainer vic = entry.getValue();
        VariableInfo vi = vic.current();
        VariableInfoContainer newVic;

        if (markCopyOfEnclosingMethod) {
            newVic = VariableInfoContainerImpl.copyOfExistingVariableInEnclosingMethod(vic, navigationData.hasSubBlocks());
        } else if (!vic.isLocalVariableInLoopDefinedOutside() && statement instanceof LoopStatement && vi.variable().isLocal()) {
            // as we move into a loop statement, the VariableInLoop is added to obtain local variable in loop defined outside
            // the variable itself will not be used anymore, only its "local copy" associated with the loop
            // however, the loop may turn out to be completely empty, in which case the initial value is kept
            // so we must keep the initial value
            newVic = VariableInfoContainerImpl.existingLocalVariableIntoLoop(vic,
                    new VariableInLoop(index, null, VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE), previousIsParent);
        } else if (indexOfPrevious != null && indexOfPrevious.equals(vic.getStatementIndexOfThisLoopOrShadowVariable())) {
            // this is the very specific situation that the previous statement introduced a loop variable (or a shadow copy)
            // this loop variable should not go beyond the loop statement
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
    public void initIteration1Plus(EvaluationContext evaluationContext, MethodInfo currentMethod,
                                   StatementAnalysis previous) {

        /* the reason we do this for all statements in the method's block is that in a subsequent iteration,
         the first statements may already be DONE, so the code doesn't reach here!
         */
        if (parent == null) {
            init1PlusStartOfMethodDoParametersAndThis(evaluationContext.getAnalyserContext(), currentMethod);
        }

        StatementAnalysis copyFrom = previous == null ? parent : previous;

        variables.toImmutableMap().values().forEach(vic -> {
            if (vic.isInitial()) {
                fromFieldAnalyserIntoInitial(evaluationContext, vic);
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
     */
    private void explicitlyPropagateVariables(StatementAnalysis copyFrom, boolean copyIsParent) {
        copyFrom.variables.stream().filter(e -> copyIsParent && notLocalLoopCopyOutOfComfortZone(e.getValue()) ||
                !copyIsParent && !copyFrom.index.equals(e.getValue().getStatementIndexOfThisLoopOrShadowVariable()))
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

    private boolean notLocalLoopCopyOutOfComfortZone(VariableInfoContainer vic) {
        // we'd only copy fields if they are used somewhere in the block. BUT there are "hidden" fields
        // such as local variables with an array initialiser containing fields as a value; conclusion: copy all, but don't merge unless used.
        if (vic.getVariableInLoop().variableType() != VariableInLoop.VariableType.LOOP_COPY) return true;
        String assignmentId = vic.getVariableInLoop().assignmentId(); // 2nd dollar
        return assignmentId == null || !assignmentId.startsWith(parent.index);
    }


    private void init1PlusStartOfMethodDoParametersAndThis(AnalyserContext analyserContext, MethodInfo currentMethod) {
        for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().getParameters()) {
            VariableInfo prevIteration = findOrNull(parameterInfo, INITIAL);
            if (prevIteration != null) {
                VariableInfoContainer vic = findForWriting(parameterInfo);
                ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                for (VariableProperty variableProperty : FROM_ANALYSER_TO_PROPERTIES) {
                    int value = parameterAnalysis.getProperty(variableProperty);
                    if (value != Level.DELAY) {
                        vic.setProperty(variableProperty, value, INITIAL);
                    }
                }
            } else {
                log(ANALYSER, "Skipping parameter {}, not read, assigned", parameterInfo.fullyQualifiedName());
            }
        }
    }

    private void fromFieldAnalyserIntoInitial(EvaluationContext evaluationContext, VariableInfoContainer vic) {
        // initial, so we need to copy from analysers.
        // parameters are dealt with in the first part of this method
        // here we deal with copying in values from fields
        VariableInfo variableInfo = vic.current();
        if (!(variableInfo.variable() instanceof FieldReference fieldReference)) return;

        // see if we can resolve a delay in statement time
        if (variableInfo.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal != Level.DELAY) {
                vic.setStatementTime(effectivelyFinal == Level.TRUE ?
                        VariableInfoContainer.NOT_A_VARIABLE_FIELD : flowData.getInitialTime());
            }
            // so from here on, isConfirmedVariableField may be set
        }
        boolean selfReference = inPartOfConstruction() && !(fieldReference.scope instanceof This);

        // this is the first time we see this field (initial)
        ExpressionAndDelay initialValue = initialValueOfField(evaluationContext, fieldReference, selfReference);

        boolean notYetAssignedToWillBeAssignedToLater = notYetAssignedToWillBeAssignedToLater(variableInfo, fieldReference);
        // see FirstThen_0: this is here to break an chicken-and-egg problem between the FieldAnalyser (allAssignmentsHaveBeenSet)
        // and StatementAnalyser (checkNotNullEscapesAndPreconditions)
        if (initialValue.expressionIsDelayed && notYetAssignedToWillBeAssignedToLater) {
            String objectId = index + "-" + fieldReference.fieldInfo.fullyQualifiedName();
            Expression initial = NewObject.initialValueOfField(objectId, primitives, fieldReference.parameterizedType(),
                    ObjectFlow.NO_FLOW);
            initialValue = new ExpressionAndDelay(initial, false);
        }

        Map<VariableProperty, Integer> map = propertyMap(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo,
                fieldReference.fieldInfo.type.defaultNotNull());
        Map<VariableProperty, Integer> valueMap = evaluationContext.getValueProperties(initialValue.expression);
        Map<VariableProperty, Integer> combined = new HashMap<>(map);
        valueMap.forEach((k, v) -> combined.merge(k, v, Math::max));

        // copy into initial
        VariableInfo viInitial = vic.best(INITIAL);
        vic.setValue(initialValue.expression, initialValue.expressionIsDelayed, LinkedVariables.EMPTY, combined, true);

        vic.setLinkedVariables(LinkedVariables.EMPTY, true);


        /* copy into evaluation, but only if there is no assignment and no reading

        reading can change the value (e.g. when a modifying method call occurs), but we have a dedicated
        method that reads from INITIAL rather than EVAL so we don't have to copy yet.

        for properties, which are incremental upon reading, we already copy into evaluation,
        because we don't have explicit code available
         */
        VariableInfo viEval = vic.best(VariableInfoContainer.Level.EVALUATION);
        // not assigned in this statement
        if (viEval != viInitial && vic.isNotAssignedInThisStatement()) {
            if (!viEval.valueIsSet() && !initialValue.expression.isUnknown() && !viEval.isRead()) {
                // whatever we do, we do NOT write CONTEXT properties, because they are written exactly once at the
                // end of the apply phase, even for variables that aren't read
                combined.keySet().removeAll(GROUP_PROPERTIES);
                vic.setValue(initialValue.expression, initialValue.expressionIsDelayed,
                        viInitial.getStaticallyAssignedVariables(), combined, false);
            }
            // if the variable has not been read, it is not present in EVAL, so we set a value
            if (!vic.isReadInThisStatement() && !viEval.linkedVariablesIsSet()) {
                vic.setLinkedVariables(LinkedVariables.EMPTY, false);
            }
        }
    }

    private boolean notYetAssignedToWillBeAssignedToLater(VariableInfo variableInfo, FieldReference fieldReference) {
        StatementAnalysis lastStatement = lastStatement();
        VariableInfo vi = lastStatement.findOrNull(fieldReference, MERGE);
        return !variableInfo.isAssigned() && vi.isAssigned();
    }

    private void ensureLocalCopiesOfConfirmedVariableFields(EvaluationContext evaluationContext, VariableInfoContainer vic) {
        if (vic.hasEvaluation()) {
            VariableInfo eval = vic.best(VariableInfoContainer.Level.EVALUATION);
            VariableInfo initial = vic.getPreviousOrInitial();
            if (eval.variable() instanceof FieldReference fieldReference &&
                    initial.isConfirmedVariableField() && !eval.getReadAtStatementTimes().isEmpty()) {

                AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
                FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
                Map<VariableProperty, Integer> propertyMap = VariableProperty.FROM_ANALYSER_TO_PROPERTIES.stream()
                        .collect(Collectors.toUnmodifiableMap(vp -> vp, fieldAnalysis::getProperty));
                LinkedVariables assignedToOriginal = new LinkedVariables(Set.of(fieldReference));

                for (int statementTime : eval.getReadAtStatementTimes()) {
                    LocalVariableReference localCopy = variableInfoOfFieldWhenReading(analyserContext,
                            fieldReference, initial, statementTime);
                    if (!variables.isSet(localCopy.fullyQualifiedName())) {
                        VariableInfoContainer lvrVic = VariableInfoContainerImpl.newLocalCopyOfVariableField(localCopy,
                                index + INITIAL, navigationData.hasSubBlocks());
                        variables.put(localCopy.fullyQualifiedName(), lvrVic);
                        String indexOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);

                        Expression initialValue = statementTime == initial.getStatementTime() &&
                                initial.getAssignmentId().compareTo(indexOfStatementTime) >= 0 ?
                                initial.getValue() :
                                NewObject.localCopyOfVariableField(index + "-" + localCopy.fullyQualifiedName(),
                                        primitives, fieldReference.parameterizedType(),
                                        fieldAnalysis.getObjectFlow());
                        assert initialValue != null && evaluationContext.isNotDelayed(initialValue);
                        Map<VariableProperty, Integer> valueMap = evaluationContext.getValueProperties(initialValue);
                        Map<VariableProperty, Integer> combined = new HashMap<>(propertyMap);
                        valueMap.forEach((k, v) -> combined.merge(k, v, Math::max));
                        for (VariableProperty vp : GROUP_PROPERTIES) {
                            combined.put(vp, vp == EXTERNAL_NOT_NULL ? MultiLevel.NOT_INVOLVED : vp.falseValue);
                        }
                        lvrVic.setValue(initialValue, false, assignedToOriginal, combined, true);
                        lvrVic.setLinkedVariables(LinkedVariables.EMPTY, true);
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
        return !(methodLevelData.combinedPreconditionIsSet() && methodLevelData.getCombinedPrecondition().isBoolValueFalse());
    }

    public record ConditionAndLastStatement(Expression condition,
                                            String firstStatementIndexForOldStyleSwitch,
                                            StatementAnalyser lastStatement,
                                            boolean alwaysEscapes) {
    }

    public record ConditionAndVariableInfo(Expression condition,
                                           VariableInfo variableInfo,
                                           boolean alwaysEscapes,
                                           VariableInLoop variableInLoop,
                                           String firstStatementIndexForOldStyleSwitch) {
        // for testing
        public ConditionAndVariableInfo(Expression condition, VariableInfo variableInfo) {
            this(condition, variableInfo, false, VariableInLoop.NOT_IN_LOOP, null);
        }
    }

    private record AcceptForMerging(VariableInfoContainer vic, boolean accept) {
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
        Map<Variable, Integer> externalNotNull = new HashMap<>();
        Map<Variable, Integer> contextNotNull = new HashMap<>();
        Map<Variable, Integer> contextModified = new HashMap<>();

        // first, per variable

        Stream<AcceptForMerging> variableStream = makeVariableStream(lastStatements);
        Set<String> merged = new HashSet<>();
        Set<Variable> doNotWrite = new HashSet<>();
        variableStream.forEach(e -> {
            VariableInfoContainer vic = e.vic();
            VariableInfo current = vic.current();
            Variable variable = current.variable();
            String fqn = variable.fullyQualifiedName();

            if (e.accept) {
                // the variable stream comes from multiple blocks; we ensure that merging takes place once only
                if (merged.add(fqn)) {
                    VariableInfoContainer destination;
                    if (!variables.isSet(fqn)) {
                        destination = createVariable(evaluationContext, variable, statementTime, vic.getVariableInLoop());
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
                                        vic2.current(), e2.alwaysEscapes, vic2.getVariableInLoop(), e2.firstStatementIndexForOldStyleSwitch);
                            })
                            .filter(cav -> acceptVariableForMerging(cav, inSwitchStatementOldStyle))
                            .collect(Collectors.toUnmodifiableList());
                    boolean ignoreCurrent;
                    if (toMerge.size() == 1 && (toMerge.get(0).variableInLoop.assignmentId() != null
                            && toMerge.get(0).variableInLoop.assignmentId().startsWith(index) && !atLeastOneBlockExecuted ||
                            variable instanceof FieldReference fr && onlyOneCopy(evaluationContext, fr))) {
                        ignoreCurrent = true; // the
                    } else {
                        ignoreCurrent = atLeastOneBlockExecuted;
                    }
                    if (toMerge.size() > 0) {
                        destination.merge(evaluationContext, stateOfConditionManagerBeforeExecution, ignoreCurrent, toMerge,
                                externalNotNull, contextNotNull, contextModified);
                    } else if (destination.hasMerge()) {
                        assert evaluationContext.getIteration() > 0; // or it wouldn't have had a merge
                        // in previous iterations there was data for us, but now there isn't; copy from I/E into M
                        destination.copyFromEvalIntoMerge(externalNotNull, contextNotNull, contextModified);
                    } else {
                        externalNotNull.put(variable, current.getProperty(EXTERNAL_NOT_NULL));
                        contextModified.put(variable, current.getProperty(CONTEXT_MODIFIED));
                        contextNotNull.put(variable, current.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            } else {
                externalNotNull.put(variable, current.getProperty(EXTERNAL_NOT_NULL));
                contextModified.put(variable, current.getProperty(CONTEXT_MODIFIED));
                contextNotNull.put(variable, current.getProperty(CONTEXT_NOT_NULL));
                // the !merged check here is because some variables appear 2x, once with a positive accept,
                // and the second time from inside the block with a negative one
                if (!merged.contains(fqn)) doNotWrite.add(variable);
            }

            // CNN_FOR_PARENT overwrite
            lastStatements.stream().filter(cal -> cal.lastStatement.statementAnalysis.variables.isSet(fqn)).forEach(cal -> {
                VariableInfoContainer calVic = cal.lastStatement.statementAnalysis.variables.get(fqn);
                VariableInfo calVi = calVic.best(EVALUATION);
                int cnn4ParentDelay = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY);
                int cnn4ParentDelayResolved = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED);
                if (cnn4ParentDelay == Level.TRUE && cnn4ParentDelayResolved != Level.TRUE) {
                    contextNotNull.put(calVi.variable(), Level.DELAY);
                } else {
                    int cnn4Parent = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT);
                    if (cnn4Parent != Level.DELAY) contextNotNull.put(calVi.variable(), cnn4Parent);
                }
            });
        });

        // then, per cluster of variables

        ContextPropertyWriter contextPropertyWriter = new ContextPropertyWriter();
        AnalysisStatus ennStatus = contextPropertyWriter.write(this, evaluationContext,
                VariableInfo::getStaticallyAssignedVariables, EXTERNAL_NOT_NULL, externalNotNull, MERGE, doNotWrite);

        AnalysisStatus cnnStatus = contextPropertyWriter.write(this, evaluationContext,
                VariableInfo::getStaticallyAssignedVariables, CONTEXT_NOT_NULL, contextNotNull, MERGE, doNotWrite);

        AnalysisStatus cmStatus = contextPropertyWriter.write(this, evaluationContext,
                VariableInfo::getLinkedVariables, CONTEXT_MODIFIED, contextModified, MERGE, doNotWrite);

        return ennStatus.combine(cnnStatus).combine(cmStatus);
    }

    private boolean onlyOneCopy(EvaluationContext evaluationContext, FieldReference fr) {
        if (fr.fieldInfo.isExplicitlyFinal()) return true;
        FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
        return fieldAnalysis.getProperty(FINAL) == Level.TRUE;
    }

    private boolean acceptVariableForMerging(ConditionAndVariableInfo cav, boolean inSwitchStatementOldStyle) {
        if (inSwitchStatementOldStyle) {
            assert cav.firstStatementIndexForOldStyleSwitch != null;
            // if the variable is assigned in the block, it has to be assigned after the first index
            // "the block" is the switch statement; otherwise,
            if (cav.variableInfo.getAssignmentId().compareTo(index) > 0) {
                return cav.firstStatementIndexForOldStyleSwitch.compareTo(cav.variableInfo.getAssignmentId()) <= 0;
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
                        !index.equals(e.getValue().getStatementIndexOfThisLoopOrShadowVariable()))),
                lastStatements.stream().flatMap(st -> st.lastStatement.statementAnalysis.variables.stream().map(e -> {
                    VariableInfo vi = e.getValue().current();
                    // we don't copy up local variables, unless they're local copies of fields
                    boolean accept = (!vi.variable().isLocal() ||
                            vi.variable() instanceof LocalVariableReference lvr && lvr.variable.isLocalCopyOf() != null) &&
                            !index.equals(e.getValue().getStatementIndexOfThisLoopOrShadowVariable());
                    return new AcceptForMerging(e.getValue(), accept);
                }))
        );
    }

    /*
    create a variable, potentially even assign an initial value and a linked variables set.
    everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
     */
    public VariableInfoContainer createVariable(EvaluationContext evaluationContext,
                                                Variable variable,
                                                int statementTime,
                                                VariableInLoop variableInLoop) {
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) {
            throw new UnsupportedOperationException("Already exists: " +
                    fqn + " in " + index + ", " + methodAnalysis.getMethodInfo().fullyQualifiedName);
        }

        int statementTimeForVariable = statementTimeForVariable(analyserContext, variable, statementTime);
        VariableInfoContainer vic = VariableInfoContainerImpl.newVariable(variable, statementTimeForVariable,
                variableInLoop, navigationData.hasSubBlocks());
        variables.put(variable.fullyQualifiedName(), vic);

        // linked variables travel from the parameters via the statements to the fields
        if (variable instanceof ReturnVariable returnVariable) {
            int defaultNotNull = methodAnalysis.getMethodInfo().returnType().defaultNotNull();
            vic.setValue(new UnknownExpression(returnVariable.returnType, UnknownExpression.RETURN_VALUE), false,
                    LinkedVariables.EMPTY, Map.of(CONTEXT_NOT_NULL, defaultNotNull, CONTEXT_MODIFIED, Level.FALSE), true);
            // assignment will be at LEVEL 3
            vic.setLinkedVariables(LinkedVariables.EMPTY, true);

        } else if (variable instanceof This) {
            vic.setValue(NewObject.forCatchOrThis(
                    index + "-" + variable.fullyQualifiedName(),
                    primitives, variable.parameterizedType()), false, LinkedVariables.EMPTY,
                    propertyMap(analyserContext, methodAnalysis.getMethodInfo().typeInfo, MultiLevel.EFFECTIVELY_NOT_NULL), true);
            vic.setLinkedVariables(LinkedVariables.EMPTY, true);
            vic.setProperty(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL, INITIAL);
            vic.setProperty(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED, INITIAL);

        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            Expression initial = initialValueOfParameter(analyserContext, parameterInfo);
            vic.setValue(initial, false, LinkedVariables.EMPTY,
                    propertyMap(analyserContext, parameterInfo, parameterInfo.parameterizedType.defaultNotNull()), true);
            vic.setLinkedVariables(LinkedVariables.EMPTY, true);
            Map<VariableProperty, Integer> valueProperties = evaluationContext.getValueProperties(initial);
            valueProperties.forEach((k, v) -> vic.setProperty(k, v, false, INITIAL));

        } else if (variable instanceof FieldReference fieldReference) {
            ExpressionAndDelay initialValue = initialValueOfField(evaluationContext, fieldReference, false);
            vic.setValue(initialValue.expression, initialValue.expressionIsDelayed,
                    LinkedVariables.EMPTY, propertyMap(analyserContext, fieldReference.fieldInfo,
                            fieldReference.fieldInfo.type.defaultNotNull()), true);
            Map<VariableProperty, Integer> valueProperties = evaluationContext.getValueProperties(initialValue.expression);
            valueProperties.forEach((k, v) -> vic.setProperty(k, v, false, INITIAL));

            vic.setLinkedVariables(LinkedVariables.EMPTY, true);

        }
        return vic;
    }

    private Expression initialValueOfParameter(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
        int notNull = MultiLevel.bestNotNull(parameterAnalysis.getProperty(NOT_NULL_PARAMETER),
                parameterInfo.parameterizedType.defaultNotNull());
        ObjectFlow objectFlow = createObjectFlowForNewVariable(analyserContext, parameterInfo);
        Expression state = new BooleanConstant(primitives, true);
        return NewObject.initialValueOfParameter(
                index + "-" + parameterInfo.fullyQualifiedName(),
                parameterInfo.parameterizedType, state, notNull, objectFlow);
    }

    public int statementTimeForVariable(AnalyserContext analyserContext, Variable variable, int statementTime) {
        if (variable instanceof FieldReference fieldReference) {
            boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                    MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
            if (inPartOfConstruction) return VariableInfoContainer.NOT_A_VARIABLE_FIELD;

            int effectivelyFinal = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) {
                return VariableInfoContainer.VARIABLE_FIELD_DELAY;
            }
            if (effectivelyFinal == Level.FALSE) {
                return statementTime;
            }
        }
        return VariableInfoContainer.NOT_A_VARIABLE_FIELD;
    }

    private static final Set<VariableProperty> FROM_ANALYSER_TO_PROPERTIES_AND_CUSTOM
            = Set.of(IDENTITY, FINAL, EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD, IMMUTABLE, CONTAINER, NOT_MODIFIED_1,
            CONTEXT_NOT_NULL, CONTEXT_MODIFIED);

    private Map<VariableProperty, Integer> propertyMap(AnalyserContext analyserContext,
                                                       WithInspectionAndAnalysis object,
                                                       int defaultNotNull) {
        Function<VariableProperty, Integer> f;
        if (object instanceof TypeInfo typeInfo) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(typeInfo);
            f = typeAnalysis::getProperty;
        } else if (object instanceof ParameterInfo parameterInfo) {
            ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
            f = parameterAnalysis::getPropertyVerifyContracted;
        } else if (object instanceof FieldInfo fieldInfo) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
            f = fieldAnalysis::getPropertyVerifyContracted;
        } else throw new UnsupportedOperationException();
        return FROM_ANALYSER_TO_PROPERTIES_AND_CUSTOM.stream()
                .collect(Collectors.toUnmodifiableMap(vp -> vp, vp -> {
                    if (vp == CONTEXT_NOT_NULL) return defaultNotNull;
                    if (vp == CONTEXT_MODIFIED) return Level.FALSE;
                    return f.apply(vp);
                }));
    }

    record ExpressionAndDelay(Expression expression, boolean expressionIsDelayed) {
    }

    private boolean inPartOfConstruction() {
        return methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
    }

    private ExpressionAndDelay initialValueOfField(EvaluationContext evaluationContext,
                                                   FieldReference fieldReference,
                                                   boolean selfReference) {
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        // rather than fieldAnalysis.getLinkedVariables

        boolean myOwn = fieldReference.scope instanceof This thisVariable && thisVariable.typeInfo.equals(methodAnalysis.getMethodInfo().typeInfo);
        String newObjectIdentifier = index + "-" + fieldReference.fieldInfo.fullyQualifiedName();

        if (inPartOfConstruction() && myOwn) { // field that must be initialised
            Expression initialValue = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getInitialValue();
            if (initialValue == null) { // initialiser value not yet evaluated
                return new ExpressionAndDelay(DelayedVariableExpression.forField(fieldReference), true);
            }
            if (initialValue.isConstant()) {
                return new ExpressionAndDelay(initialValue, false);
            }
            if(initialValue instanceof NewObject) return new ExpressionAndDelay(initialValue, false);

            // FIXME will crash when notNull==-1
            NewObject newObject = NewObject.initialValueOfFieldPartOfConstruction(
                    newObjectIdentifier, evaluationContext, fieldReference, fieldAnalysis.getObjectFlow());
            return new ExpressionAndDelay(newObject, false);
        }

        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY && !selfReference) {
            return new ExpressionAndDelay(DelayedVariableExpression.forField(fieldReference), true);
        }

        int notNull = fieldAnalysis.getProperty(EXTERNAL_NOT_NULL);
        if (notNull == Level.DELAY) {
            return new ExpressionAndDelay(DelayedVariableExpression.forField(fieldReference), true);
        }

        // when selfReference (as in this.x = other.x during construction), we never delay

        boolean variableField = effectivelyFinal == Level.FALSE;
        if (!variableField) {
            Expression efv = fieldAnalysis.getEffectivelyFinalValue();
            if (efv == null) {
                if (analyserContext.getTypeAnalysis(fieldReference.fieldInfo.owner).isBeingAnalysed() && !selfReference) {
                    return new ExpressionAndDelay(DelayedVariableExpression.forField(fieldReference), true);
                }
            } else {
                if (efv.isConstant()) {
                    return new ExpressionAndDelay(efv, false);
                }
                NewObject newObject;
                if ((newObject = efv.asInstanceOf(NewObject.class)) != null) {
                    return new ExpressionAndDelay(newObject, false);
                }
            }
        }
        // variable field, some cases of effectively final field
        NewObject newObject;
        FieldAnalyser fieldAnalyser = analyserContext.getFieldAnalyser(fieldReference.fieldInfo);
        if (fieldAnalyser == null) {
            // not a local field
            int minimalNotNull = Math.max(MultiLevel.NULLABLE,
                    analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getProperty(EXTERNAL_NOT_NULL));
            newObject = NewObject.initialValueOfExternalField(newObjectIdentifier,
                    primitives, fieldReference.parameterizedType(), minimalNotNull, ObjectFlow.NO_FLOW);
        } else {
            newObject = NewObject.initialValueOfField(newObjectIdentifier,
                    primitives, fieldReference.parameterizedType(), fieldAnalyser.fieldAnalysis.getObjectFlow());
        }
        return new ExpressionAndDelay(newObject, false);
    }

    private ObjectFlow createObjectFlowForNewVariable(AnalyserContext analyserContext, Variable variable) {
        if (variable instanceof ParameterInfo parameterInfo) {
            ObjectFlow objectFlow = new ObjectFlow(new Location(parameterInfo),
                    parameterInfo.parameterizedType, Origin.PARAMETER);
            internalObjectFlows.add(objectFlow); // this will be a first
            return objectFlow;
        }

        if (variable instanceof FieldReference fieldReference) {
            ObjectFlow fieldObjectFlow = new ObjectFlow(new Location(fieldReference.fieldInfo),
                    fieldReference.parameterizedType(), Origin.FIELD_ACCESS);
            ObjectFlow objectFlow;
            if (internalObjectFlows.contains(fieldObjectFlow)) {
                objectFlow = internalObjectFlows.stream().filter(of -> of.equals(fieldObjectFlow)).findFirst().orElseThrow();
            } else {
                objectFlow = fieldObjectFlow;
                internalObjectFlows.add(objectFlow);
            }
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            objectFlow.addPrevious(fieldAnalysis.getObjectFlow());
            return objectFlow;
        }
        return ObjectFlow.NO_FLOW; // will be assigned to soon enough
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
            return new VariableInfoImpl(variable); // no value, no state; will be created by a MarkRead
        }
        VariableInfoContainer vic = variables.get(fqn);
        VariableInfo vi = vic.getPreviousOrInitial();
        if (isNotAssignmentTarget) {
            if (vi.variable() instanceof FieldReference fieldReference && vi.isConfirmedVariableField()) {
                String localVariableFqn = createLocalFieldCopyFQN(vi, fieldReference, statementTime);
                if (!variables.isSet(localVariableFqn)) {
                    // it is possible that the field has been assigned to, so it exists, but the local copy does not yet
                    return new VariableInfoImpl(variable);
                }
                return variables.get(localVariableFqn).getPreviousOrInitial();
            }
            if (vic.isLocalVariableInLoopDefinedOutside()) {
                StatementAnalysis relevantLoop = mostEnclosingLoop();
                if (relevantLoop.localVariablesAssignedInThisLoop.isFrozen()) {
                    if (relevantLoop.localVariablesAssignedInThisLoop.contains(fqn)) {
                        String localCopyFqn = createLocalLoopCopyFQN(vic, vi);
                        VariableInfoContainer newVic = variables.get(localCopyFqn);
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

    /*
    Two situations for a variable field. First, when there's consecutive reads after an assignment, each in increasing
    statement times, we simply use this statement time in the name: $st.
    Only when statement time hasn't increased, but assignments have, we use the combination $st$assignment id.
     */
    private String createLocalFieldCopyFQN(VariableInfo fieldVi, FieldReference fieldReference, int statementTime) {
        String indexOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);
        String prefix = fieldReference.fullyQualifiedName() + "$" + statementTime;
        if (statementTime == fieldVi.getStatementTime() && fieldVi.getAssignmentId().compareTo(indexOfStatementTime) >= 0) {
            // return a local variable with the current field value, numbered as the statement time + assignment ID
            return prefix + "$" + fieldVi.getAssignmentId().replace(".", "_");
        }
        return prefix;
    }

    public String createLocalLoopCopyFQN(VariableInfoContainer vic, VariableInfo vi) {
        assert vic.isLocalVariableInLoopDefinedOutside();

        String prefix = vi.name() + "$" + vic.getVariableInLoop().statementId();
        if (vi.getAssignmentId().compareTo(vic.getVariableInLoop().statementId()) > 0) {
            return prefix + "$" + vi.getAssignmentId().replace(".", "_");
        }
        return prefix;
    }

    public LocalVariableReference variableInfoOfFieldWhenReading(AnalyserContext analyserContext,
                                                                 FieldReference fieldReference,
                                                                 VariableInfo fieldVi,
                                                                 int statementTime) {
        // a variable field can have any value when first read in a method.
        // after statement time goes up, this value may have changed completely
        // therefore we return a new local variable each time we read and statement time has gone up.

        // when there are assignments within the same statement time, however, we stick to the assigned value
        // (we temporarily treat the field as a local variable)
        // so we need to know: have there been assignments AFTER the latest statement time increase?

        String localVariableFqn = createLocalFieldCopyFQN(fieldVi, fieldReference, statementTime);

        // the statement time of the field indicates the time of the latest assignment
        LocalVariable lv = new LocalVariable.Builder()
                .addModifier(LocalVariableModifier.FINAL)
                .setName(localVariableFqn)
                .setParameterizedType(fieldReference.parameterizedType())
                .setIsLocalCopyOf(fieldReference)
                .setOwningType(methodAnalysis.getMethodInfo().typeInfo)
                .build();
        return new LocalVariableReference(analyserContext, lv, List.of());
    }

    /**
     * helper method
     *
     * @param variable the variable
     * @return the most current variable info object
     * @throws IllegalArgumentException when the variable does not yet exist
     */
    public Expression initialValueOfReturnVariable(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
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
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null) return null;
        return vic.best(level);
    }

    /**
     * for reading, helper method; not for general use
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrThrow(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null)
            throw new UnsupportedOperationException("Have not yet evaluated " + variable.fullyQualifiedName());
        return vic.current();
    }

    public boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        if (!variables.isSet(variableName)) return false;
        VariableInfoContainer vic = variables.get(variableName);
        if (vic.isLocalVariableInLoopDefinedOutside()) return false;
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

    public Set<String> allUnqualifiedVariableNames(InspectionProvider inspectionProvider, TypeInfo currentType) {
        Set<String> fromFields = Resolver.accessibleFieldsStream(inspectionProvider, currentType, currentType.primaryType())
                .map(fieldInfo -> fieldInfo.name).collect(Collectors.toSet());
        Set<String> local = variableStream().map(vi -> vi.variable().simpleName()).collect(Collectors.toSet());
        return SetUtil.immutableUnion(fromFields, local);
    }

    // ***************** OBJECT FLOW CODE ***************

    public ObjectFlow getObjectFlow(Variable variable) {
        VariableInfo aboutVariable = findOrThrow(variable);
        return aboutVariable.getObjectFlow();
    }

    public ObjectFlow addAccess(boolean modifying, Access access, Expression value, EvaluationContext evaluationContext) {
        if (value.getObjectFlow() == ObjectFlow.NO_FLOW) return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value, evaluationContext, false); // TODO check false
        if (modifying) {
            log(OBJECT_FLOW, "Set modifying access on {}", potentiallySplit);
            potentiallySplit.setModifyingAccess((MethodAccess) access);
        } else {
            log(OBJECT_FLOW, "Added non-modifying access to {}", potentiallySplit);
            potentiallySplit.addNonModifyingAccess(access);
        }
        return potentiallySplit;
    }

    public ObjectFlow addCallOut(boolean modifying, ObjectFlow callOut, Expression value, EvaluationContext evaluationContext) {
        if (callOut == ObjectFlow.NO_FLOW || value.getObjectFlow() == ObjectFlow.NO_FLOW)
            return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value, evaluationContext, false); // TODO check false
        if (modifying) {
            log(OBJECT_FLOW, "Set call-out on {}", potentiallySplit);
            potentiallySplit.setModifyingCallOut(callOut);
        } else {
            log(OBJECT_FLOW, "Added non-modifying call-out to {}", potentiallySplit);
            potentiallySplit.addNonModifyingCallOut(callOut);
        }
        return potentiallySplit;
    }

    private ObjectFlow splitIfNeeded(Expression value, EvaluationContext evaluationContext, boolean initialOrEvaluation) {
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == ObjectFlow.NO_FLOW) return objectFlow; // not doing anything
        if (objectFlow.haveModifying()) {
            // we'll need to split
            ObjectFlow split = createInternalObjectFlow(objectFlow.type, evaluationContext);
            objectFlow.addNext(split);
            split.addPrevious(objectFlow);
            VariableExpression variableValue;
            if ((variableValue = value.asInstanceOf(VariableExpression.class)) != null) {
                updateObjectFlow(variableValue.variable(), split, initialOrEvaluation);
            }
            log(OBJECT_FLOW, "Split {}", objectFlow);
            return split;
        }
        return objectFlow;
    }

    private ObjectFlow createInternalObjectFlow(ParameterizedType parameterizedType, EvaluationContext evaluationContext) {
        Location location = evaluationContext.getLocation();
        ObjectFlow objectFlow = new ObjectFlow(location, parameterizedType, Origin.INTERNAL);
        if (!internalObjectFlows.contains(objectFlow)) {
            internalObjectFlows.add(objectFlow);
            log(OBJECT_FLOW, "Created internal flow {}", objectFlow);
            return objectFlow;
        }
        throw new UnsupportedOperationException("Object flow already exists"); // TODO
    }

    private void updateObjectFlow(Variable variable, ObjectFlow objectFlow, boolean initialOrEvaluation) {
        VariableInfoContainer variableInfo = findForWriting(variable);
        variableInfo.setObjectFlow(objectFlow, initialOrEvaluation);
    }

    public Stream<VariableInfo> variableStream() {
        return variables.stream().map(Map.Entry::getValue).map(VariableInfoContainer::current);
    }

    public Stream<VariableInfo> variableStream(VariableInfoContainer.Level level) {
        return variables.stream().map(Map.Entry::getValue).map(vic -> vic.best(level));
    }

    public Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream() {
        return variables.stream();
    }

    public Stream<VariableInfo> safeVariableStream(VariableInfoContainer.Level level) {
        return variables.toImmutableMap().values().stream().map(vic -> vic.best(level));
    }

    // this is a safe constant (delay == -1)
    private static final int EXACTLY_NULL = 0;

    public Expression notNullValuesAsExpression(EvaluationContext evaluationContext) {
        return new And(evaluationContext.getPrimitives()).append(evaluationContext, variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference
                        && vi.isAssigned()
                        && index().equals(VariableInfoContainer.statementId(vi.getAssignmentId())))
                .map(vi -> {
                    if (vi.variable() instanceof FieldReference fieldReference) {
                        if (vi.getValue() instanceof NullConstant) {
                            return new Pair<>(vi, EXACTLY_NULL);
                        }
                        int notNull = evaluationContext.getProperty(new VariableExpression(fieldReference),
                                NOT_NULL_EXPRESSION, false);
                        return new Pair<>(vi, notNull);
                    }
                    return null;
                })
                .filter(e -> e != null && (e.v == EXACTLY_NULL || e.v >= MultiLevel.EFFECTIVELY_NOT_NULL))
                .map(e -> {
                    Expression equals = Equals.equals(evaluationContext, new VariableExpression(e.k.variable()),
                            NullConstant.NULL_CONSTANT, ObjectFlow.NO_FLOW);
                    if (e.v >= MultiLevel.EFFECTIVELY_NOT_NULL) {
                        return Negation.negate(evaluationContext, equals);
                    }
                    return equals;
                })
                .toArray(Expression[]::new));
    }

    public boolean isAssignedToOtherVariable(Variable variable) {
        return variables.stream().anyMatch(e ->
                e.getValue().getPreviousOrInitial().getStaticallyAssignedVariables().contains(variable));
    }

    public boolean isLinkedToOtherVariable(Variable variable) {
        return variables.stream().anyMatch(e -> e.getValue().getPreviousOrInitial().getLinkedVariables().contains(variable));
    }
}
