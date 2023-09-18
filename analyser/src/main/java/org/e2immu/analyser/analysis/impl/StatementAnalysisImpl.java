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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.nonanalyserimpl.Merge;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoContainerImpl;
import org.e2immu.analyser.analyser.util.VariableAccessReport;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.AddOnceSet;
import org.e2immu.support.Either;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.*;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container
public class StatementAnalysisImpl extends AbstractAnalysisBuilder implements StatementAnalysis, LimitedStatementAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalysisImpl.class);

    public final Statement statement;
    public final String index;
    public final StatementAnalysis parent;
    public final boolean inSyncBlock;
    public final MethodAnalysis methodAnalysis;

    public final AddOnceSet<Message> messages = new AddOnceSet<>();
    public final NavigationData<StatementAnalysis> navigationData = new NavigationData<>();

    // make sure to use putVariable to add a variable to this map; facilitates debugging
    private final SetOnceMap<String, VariableInfoContainer> variables = new SetOnceMap<>();

    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData;
    public final FlowData flowData;
    public final RangeData rangeData;
    public final AddOnceSet<String> localVariablesAssignedInThisLoop;
    public final AddOnceSet<Variable> candidateVariablesForNullPtrWarning = new AddOnceSet<>();

    private final Map<Variable, Properties> propertiesFromSubAnalysers = new HashMap<>();

    // a variable that changes from iteration to iteration... should be moved out at some point
    private final Map<CausesOfDelay, Integer> applyCausesOfDelay = new HashMap<>();

    private final FlipSwitch brokeDelay = new FlipSwitch();

    @Override
    public void internalAllDoneCheck() {
        super.internalAllDoneCheck();
        if (!flowData.isUnreachable()) {
            rawVariableStream()
                    .map(Map.Entry::getValue)
                    // we do not look at Previous only, because those values are decided elsewhere
                    // neither do we look at Initial only, because they are injected at any time (see e.g. VariableScope_5)
                    .filter(vic -> vic.hasEvaluation() || vic.hasMerge())
                    .map(VariableInfoContainer::current)
                    .forEach(vi -> {
                        assert vi.valueIsSet() : "Variable " + vi.variable().fullyQualifiedName() + " has value: "
                                + vi.getValue() + " in statement " + index + ", "
                                + methodAnalysis.getMethodInfo().fullyQualifiedName;
                        assert vi.linkedVariablesIsSet() : "Variable " + vi.variable().fullyQualifiedName()
                                + " has unfrozen linked variables in statement " + index + ", "
                                + methodAnalysis.getMethodInfo().fullyQualifiedName;
                    });
        }
        stateData.internalAllDoneCheck();
        flowData.internalAllDoneCheck();
        if (rangeData != null) rangeData.internalAllDoneCheck();
        methodLevelData.internalAllDoneCheck();
    }

    @Override
    public void makeUnreachable() {
        flowData.makeUnreachable();
        stateData.makeUnreachable(primitives);
        methodLevelData.makeUnreachable(primitives);
        if (statement() instanceof ReturnStatement) {
            StatementAnalysis.FindLoopResult loop = findLoopByLabel(null);
            if (loop != null) {
                loop.statementAnalysis().stateData().stateOfReturnInLoopUnreachable(index());
            }
        }
    }

    public StatementAnalysisImpl(Primitives primitives,
                                 MethodAnalysis methodAnalysis,
                                 Statement statement,
                                 StatementAnalysis parent,
                                 String index,
                                 boolean inSyncBlock) {
        super(primitives, index);
        this.index = super.simpleName;
        this.statement = Objects.requireNonNull(statement);
        this.parent = parent;
        this.inSyncBlock = inSyncBlock;
        this.methodAnalysis = Objects.requireNonNull(methodAnalysis);
        localVariablesAssignedInThisLoop = statement instanceof LoopStatement ? new AddOnceSet<>() : null;
        Location location = new LocationImpl(methodAnalysis.getMethodInfo(), index + INITIAL, statement.getIdentifier());
        stateData = new StateData(location, statement instanceof LoopStatement, primitives);
        flowData = new FlowData(location);
        rangeData = statement instanceof LoopStatement ? new RangeDataImpl(location) : null;
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
            String padded = pad(statementIndex, statements.size());
            String iPlusSt = indices.isEmpty() ? padded : indices + "." + padded;
            StatementAnalysis statementAnalysis = new StatementAnalysisImpl(primitives, methodAnalysis, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.navigationData().next.set(Optional.of(statementAnalysis));
            }
            previous = statementAnalysis;
            if (first == null) first = statementAnalysis;

            int blockIndex = 0;
            List<Optional<StatementAnalysis>> analysisBlocks = new ArrayList<>();

            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;
            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);
                List<Statement> listOfSubStatements = structure.getStatements();
                assert listOfSubStatements != null :
                        "Statement " + statementAnalysis.index() + " of class " + statement.getClass().getSimpleName()
                                + " has statements, but the statements() call returns null";
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                        listOfSubStatements, indexWithBlock, true, newInSyncBlock);
                analysisBlocks.add(Optional.of(subStatementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements()) {
                if (subStatements.haveStatements()) {
                    String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);
                    List<Statement> listOfSubStatements = subStatements.getStatements();
                    assert listOfSubStatements != null :
                            "Sub-block " + blockIndex + " of statement " + statementAnalysis.index()
                                    + " of class " + statement.getClass().getSimpleName()
                                    + " has statements, but the subStatements.statements() call returns null";
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                            listOfSubStatements, indexWithBlock, true, newInSyncBlock);
                    analysisBlocks.add(Optional.of(subStatementAnalysis));
                } else {
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            }
            statementAnalysis.navigationData().blocks.set(List.copyOf(analysisBlocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.navigationData().next.set(Optional.empty());
        }
        return first;
    }

    @Override
    public String fullyQualifiedName() {
        return methodAnalysis.getMethodInfo().fullyQualifiedName + ":" + index;
    }

    public String toString() {
        return index + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(StatementAnalysis o) {
        return index.compareTo(o.index());
    }

    @Override
    public NavigationData<StatementAnalysis> getNavigationData() {
        return navigationData;
    }

    @Override
    public StatementAnalysis startOfBlock(int i) {
        if (!navigationData.blocks.isSet()) return null;
        List<Optional<StatementAnalysis>> list = navigationData.blocks.get();
        return i >= list.size() ? null : list.get(i).orElse(null);
    }

    @Override
    public RangeData rangeData() {
        return rangeData;
    }

    @Override
    public StatementAnalysis startOfBlockStatementAnalysis(int i) {
        return startOfBlock(i);
    }

    @Override
    public LimitedStatementAnalysis navigationBlock0OrElseNull() {
        return navigationData.blocks.get().get(0).orElse(null);
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
    public boolean navigationReplacementIsSet() {
        return navigationData.replacement.isSet();
    }

    @Override
    public boolean navigationNextIsSet() {
        return navigationData.next.isSet();
    }

    @Override
    public LimitedStatementAnalysis navigationNextGetOrElseNull() {
        return navigationData.next.get().orElse(null);
    }

    @Override
    public LimitedStatementAnalysis navigationReplacementGet() {
        return navigationData.replacement.get();
    }

    @Override
    public boolean navigationHasSubBlocks() {
        return navigationData.hasSubBlocks();
    }

    @Override
    public boolean navigationBlock0IsPresent() {
        return navigationData.blocks.get().get(0).isPresent();
    }

    @Override
    public Statement statement() {
        return statement;
    }

    @Override
    public StateData stateData() {
        return stateData;
    }

    @Override
    public boolean inSyncBlock() {
        return inSyncBlock;
    }

    @Override
    public int numberOfVariables() {
        return variables.size();
    }

    @Override
    public Primitives primitives() {
        return primitives;
    }

    @Override
    public MethodAnalysis methodAnalysis() {
        return methodAnalysis;
    }

    @Override
    public StatementAnalysis lastStatement() {
        return lastStatement(false);
    }

    @Override
    public StatementAnalysis lastStatement(boolean excludeThrows) {
        if (flowData.isUnreachable() && "0".equals(index)) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        StatementAnalysis replaced = followReplacements();
        if (replaced.navigationData().next.get().isPresent()) {
            StatementAnalysis statementAnalysis = replaced.navigationData().next.get().get();
            if (statementAnalysis.flowData().isUnreachable() ||
                    excludeThrows && statementAnalysis.statement() instanceof ThrowStatement) {
                return replaced;
            }
            // recursion
            return statementAnalysis.lastStatement(excludeThrows);
        }
        return replaced;
    }

    @Override
    public List<StatementAnalysis> lastStatementsOfNonEmptySubBlocks() {
        return navigationData.blocks.get().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sa -> !sa.flowData().isUnreachable())
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

    @Override
    public boolean atTopLevel() {
        return index.indexOf('.') == 0;
    }

    @Override
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

    @Override
    @NotNull
    public VariableInfo getLatestVariableInfo(String variableName) {
        if (!variables.isSet(variableName)) {
            return null; // statements will not have been analysed yet?
        }
        return variables.get(variableName).current();
    }

    @Override
    public Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        return rawVariableStream()
                .map(e -> e.getValue().current())
                .filter(v -> v.variable() instanceof FieldReference fieldReference
                        && fieldReference.fieldInfo == fieldInfo);
    }

    @Override
    public List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        return streamOfLatestInfoOfVariablesReferringTo(fieldInfo).toList();
    }

    @Override
    public VariableInfoContainer getVariable(String fullyQualifiedName) {
        assert variables.isSet(fullyQualifiedName);
        return variables.get(fullyQualifiedName);
    }

    @Override
    public VariableInfoContainer getVariableOrDefaultNull(String fullyQualifiedName) {
        return variables.getOrDefaultNull(fullyQualifiedName);
    }

    @Override
    public boolean variableIsSet(String fullyQualifiedName) {
        return variables.isSet(fullyQualifiedName);
    }

    @Override
    public boolean containsMessage(Message.Label messageLabel) {
        return localMessageStream().anyMatch(message -> message.message() == messageLabel &&
                message.location().equalsIgnoreStage(location(INITIAL)));
    }

    @Override
    public boolean containsMessage(Message message) {
        return messages.contains(message);
    }

    @Override
    public DV getProperty(Property property) {
        throw new UnsupportedOperationException("? statements have no property");
    }

    @Override
    public Location location(Stage stage) {
        return new LocationImpl(methodAnalysis.getMethodInfo(), index + stage.label, statement.getIdentifier());
    }

    // ****************************************************************************************

    /**
     * Before iteration 0, all statements: create what was already present higher up
     *
     * @param evaluationContext overview object for the analysis of this primary type
     * @param previous          the previous statement, or null if there is none (start of block)
     */
    @Override
    public void initIteration0(EvaluationContext evaluationContext, MethodInfo currentMethod, StatementAnalysis previous) {
        if (previous == null) {
            // at the beginning of a block
            if (methodAnalysis.getMethodInfo().hasReturnValue()) {
                Variable retVar = new ReturnVariable(methodAnalysis.getMethodInfo());
                createVariable(evaluationContext, retVar, 0, VariableNature.METHOD_WIDE);
            }
            if (parent == null) {
                assert "0".equals(index) || "00".equals(index) || "000".equals(index);
                flowData.setInitialTime(0, index);
                createParametersThisAndVariablesFromClosure(evaluationContext, currentMethod);
                return;
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.rawVariableStream()
                // never copy a return variable from the parent
                .filter(e -> previous != null || !(e.getValue().current().variable() instanceof ReturnVariable))
                .forEach(e -> copyVariableFromPreviousInIteration0(e, copyFrom,
                        previous == null, previous == null ? null : previous.index()));

        flowData.initialiseAssignmentIds(copyFrom.flowData());
    }

    private void createParametersThisAndVariablesFromClosure(EvaluationContext evaluationContext, MethodInfo currentMethod) {
        // at the beginning of a method, we create parameters; also those from closures
        assert evaluationContext != null;
        assert currentMethod != null;

        for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().getParameters()) {
            createVariable(evaluationContext, parameterInfo, 0, VariableNature.METHOD_WIDE);
        }
        EvaluationContext closure = evaluationContext.getClosure();
        if (closure != null) {
            closure.variablesFromClosure().forEach(e -> {
                VariableInfo vi = e.getValue().current();
                if (vi.variable() instanceof LocalVariableReference || vi.variable() instanceof DependentVariable) {
                    Expression newValue = vi.getValue().generify(evaluationContext);
                    VariableInfoContainer newVic = VariableInfoContainerImpl.copyOfExistingVariableInEnclosingMethod(location(INITIAL),
                            e.getValue(), navigationData.hasSubBlocks(), newValue);
                    putVariable(e.getKey(), newVic);
                } else {
                    // FR, This: they get values injected
                    createVariable(evaluationContext, vi.variable(), 0, VariableNature.FROM_ENCLOSING_METHOD);
                }
            });
        }

        // even static methods need a "this" variable, because we use it to mark that a modifying method has been called
        // in a static context
        This thisVariable = new This(evaluationContext.getAnalyserContext(), currentMethod.typeInfo);
        createVariable(evaluationContext, thisVariable, 0, VariableNature.METHOD_WIDE);
    }

    private void copyVariableFromPreviousInIteration0(Map.Entry<String, VariableInfoContainer> entry,
                                                      StatementAnalysis copyFrom,
                                                      boolean previousIsParent,
                                                      String indexOfPrevious) {
        String fqn = entry.getKey();
        VariableInfoContainer vic = entry.getValue();
        VariableInfo vi = vic.current();
        Variable variable = vi.variable();
        VariableInfoContainer newVic;

        if (doNotCopyToNextStatement(copyFrom, vic, variable, indexOfPrevious)) {
            return; // skip; note: order is important, this check has to come before the next one (e.g., Var_2)
        } else if (conditionsToMoveVariableInsideLoop(variable, copyFrom, previousIsParent)) {
            // move a local variable, not defined in this loop, inside the loop
            // for all loops except forEach, the expression of the loop serves as the entry point
            // for forEach, the expression is outside, but the loop variable is inside. this complicates the code somewhat
            if (copyFrom.statement() instanceof ForEachStatement && previousIsParent) {
                newVic = VariableInfoContainerImpl.existingLocalVariableIntoLoop(vic, copyFrom.index(), true);
            } else {
                newVic = VariableInfoContainerImpl.existingLocalVariableIntoLoop(vic, index, previousIsParent);
            }
        } else {
            // make a simple reference copy; potentially resetting localVariableInLoopDefinedOutside
            newVic = VariableInfoContainerImpl.existingVariable(vic, index, previousIsParent, navigationData.hasSubBlocks());
        }
        putVariable(fqn, newVic);
    }

    private boolean doNotCopyToNextStatement(StatementAnalysis copyFrom,
                                             VariableInfoContainer vic,
                                             Variable variable,
                                             String indexOfPrevious) {
        if (vic.variableNature().doNotCopyToNextStatement(indexOfPrevious, index)) return true;
        // but what if we have a field access on one such variable? check recursively!
        IsVariableExpression ive;
        if (variable instanceof FieldReference fr && ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null)) {
            String scopeFqn = ive.variable().fullyQualifiedName();
            if (copyFrom.variableIsSet(scopeFqn)) {
                VariableInfoContainer scopeVic = copyFrom.getVariable(scopeFqn);
                return doNotCopyToNextStatement(copyFrom, scopeVic, ive.variable(), indexOfPrevious);
            }
            return true;
        }
        return false;
    }

    /*
    normal situation: statement instanceof LoopStatement && variable.isLocal()
    this means: any local variable will transition, at the loop statement, into a VariableDefinedOutsideLoop,
    at the evaluation level already.

    the problem is that this also happens to the forEach loop source, which should, ONLY for eval at the loop statement,
    stay outside. (see e.g. ResourcesSimplified_0). The loop source should only become VDOL inside the loop.

     */
    private boolean conditionsToMoveVariableInsideLoop(Variable variable,
                                                       StatementAnalysis previous,
                                                       boolean previousIsParent) {
        if (!variable.isLocal()) return false;
        if (statement instanceof ForEachStatement forEach) {
            // fine for all, except the loop source
            // TODO what if the loop source is an expression? should we take all variables?
            return !(forEach.expression instanceof VariableExpression ve && ve.variable().equals(variable));
        }
        if (previous.statement() instanceof ForEachStatement forEach && previousIsParent) {
            // only for the loop source
            return forEach.expression instanceof VariableExpression ve && ve.variable().equals(variable);
        }
        return statement instanceof LoopStatement;
    }

    /**
     * Before iterations 1+, with fieldAnalyses non-empty only potentially for the first statement
     * of the method.
     *
     * @param evaluationContext overview object for the analysis of this primary type
     * @param previous          the previous statement, or null if there is none (start of block)
     */
    public void initIteration1Plus(EvaluationContext evaluationContext,
                                   StatementAnalysis previous) {

        /* the reason we do this for all statements in the method's block is that in a subsequent iteration,
         the first statements may already be DONE, so the code doesn't reach here!
         See VariableScope_5 as an example where only in 0 and 1-E are done before a value is reached.
         */
        init1PlusStartOfMethodDoParameters(evaluationContext.getAnalyserContext());
        EvaluationContext closure = evaluationContext.getClosure();
        if (closure != null) {
            closure.variablesFromClosure().forEach(e -> {
                VariableInfoContainer here = variables.getOrDefaultNull(e.getKey());
                if (here != null) {
                    VariableInfo viInClosure = e.getValue().getPreviousOrInitial();
                    VariableInfo hereInitial = here.getRecursiveInitialOrNull();
                    if (hereInitial != null) {
                        // E2Immutable_7 shows that we cannot simply keep copying in LVs
                        // there's sufficient guarantee that copying in can work, but as soon as some statements are DONE,
                        // we need to be more careful
                        LinkedVariables lv = hereInitial.getLinkedVariables().isDone() ? null : viInClosure.getLinkedVariables();
                        here.safeSetValue(viInClosure.getValue(), lv, viInClosure.valueProperties(), INITIAL);
                    }
                } // else: it is perfectly possible for variables to be returned by variablesFromClosure that were not
                // present in the initial call in iteration 0 (see Warnings_5, VariableScope_5, e.g.)
                // however, if they are not present, they have no reason to be there.
            });
        }

        StatementAnalysis copyFrom = previous == null ? parent : previous;

        rawVariableStream().map(Map.Entry::getValue).forEach(vic -> {
            VariableInfo variableInfo = vic.current();
            Variable variable = variableInfo.variable();
            if (variable instanceof This) {
                DV immutable = evaluationContext.getAnalyserContext().typeImmutable(variable.parameterizedType());
                vic.setProperty(EXTERNAL_IMMUTABLE, immutable, true, INITIAL);
            }
            if (vic.isInitial() && variable instanceof FieldReference fieldReference) {
                fromFieldAnalyserIntoInitial(evaluationContext, vic, fieldReference);
            }
            if (vic.previousIsRemoved()) {
                vic.remove();
            }
        });
        if (copyFrom != null) {
            explicitlyPropagateVariables(copyFrom, previous == null);
        }
        EvaluationResult context = EvaluationResult.from(evaluationContext);
        rawVariableStream()
                .map(Map.Entry::getValue)
                .filter(VariableInfoContainer::isInitial)
                .forEach(vic -> {
                    VariableInfo variableInfo = vic.current();
                    if (variableInfo.variable() instanceof DependentVariable dv && vic.getPreviousOrInitial().getValue().isDelayed()) {
                        initializeLocalOrDependentVariable(vic, dv, context);
                    }
                });
    }

    private TypeInfo getCurrentType() {
        return methodAnalysis.getMethodInfo().typeInfo;
    }

    /* explicitly copy local variables from above or previous (they cannot be created on demand)
       loop variables at the statement are not copied to the next one
       Some fields only become visible in a later iteration (see e.g. Enum_3 test, field inside constant result
       of array initialiser) -- so we don't explicitly restrict to local variables

       ConditionalInitialization only goes to the next statement, never inside a block
     */
    private void explicitlyPropagateVariables(StatementAnalysis copyFrom, boolean copyIsParent) {
        copyFrom.rawVariableStream()
                .filter(e -> explicitlyPropagate(copyFrom, copyIsParent, e.getValue()))
                .forEach(e -> {
                    String fqn = e.getKey();
                    VariableInfoContainer vicFrom = e.getValue();
                    if (!variables.isSet(fqn)) {
                        VariableInfoContainer newVic = VariableInfoContainerImpl.existingVariable(vicFrom,
                                null, copyIsParent, navigationData.hasSubBlocks());
                        putVariable(fqn, newVic);
                    }
                });
    }

    private boolean explicitlyPropagate(StatementAnalysis copyFrom, boolean copyIsParent, VariableInfoContainer vic) {
        if (vic.variableNature() instanceof VariableNature.Pattern pattern) {
            return StringUtil.inScopeOf(pattern.scope(), index);
        }
        if (vic.variableNature().doNotCopyToNextStatement(copyFrom.index(), index)) return false;
        Variable variable = vic.current().variable();
        if (copyIsParent && variable.hasScopeVariableCreatedAt(copyFrom.index())) {
            return false;
        }
        if (copyIsParent) {
            // see variableEntryStream(EVALUATION) -> ignore those that have merges but no eval; see e.g. Basics_7
            return !vic.hasMerge() || vic.hasEvaluation();
            // we'd only copy fields if they are used somewhere in the block. BUT there are "hidden" fields
            // such as local variables with an array initialiser containing fields as a value; conclusion: copy all, but don't merge unless used.
        }
        // don't continue loop and resource variables beyond the loop
        if (copyFrom.index().equals(vic.variableNature().getStatementIndexOfBlockVariable())) return false;

        IsVariableExpression ive;
        if (variable instanceof FieldReference fr && ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null)) {
            String scopeFqn = ive.variable().fullyQualifiedName();
            if (copyFrom.variableIsSet(scopeFqn)) {
                VariableInfoContainer scopeVic = copyFrom.getVariable(scopeFqn);
                return explicitlyPropagate(copyFrom, false, scopeVic);
            }
            return false; // don't propagate
        }
        return true;
    }


    /*
    Do not add IMMUTABLE to this set! (computed from external, formal, context)
     */
    public static final Set<Property> FROM_PARAMETER_ANALYSER_TO_PROPERTIES
            = Set.of(EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE, CONTAINER_RESTRICTION, IGNORE_MODIFICATIONS);

    /*
    assume that all parameters, also those from closures, are already present
     */
    private void init1PlusStartOfMethodDoParameters(AnalyserContext analyserContext) {
        rawVariableStream().map(Map.Entry::getValue)
                .filter(vic -> vic.getPreviousOrInitial().variable() instanceof ParameterInfo)
                .forEach(vic -> {
                    VariableInfo prevInitial = vic.getPreviousOrInitial();
                    ParameterInfo parameterInfo = (ParameterInfo) prevInitial.variable();
                    if (vic.isRecursivelyInitial()) {
                        updateValuePropertiesOfParameter(analyserContext, vic, prevInitial, parameterInfo);
                        ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                        for (Property property : FROM_PARAMETER_ANALYSER_TO_PROPERTIES) {
                            DV value = parameterAnalysis.getProperty(property);
                            // we have given a value in the first iteration for e.g. @Container
                            // we'll not get back to that (will happen in EVAL rather than here)
                            // hence the 'true' to ensure that we don't cause exceptions
                            vic.setProperty(property, value, true, INITIAL);
                        }
                    }
                });
    }

    /*
    variables that are not marked for assignment, get no update of their @Immutable property
    In the meantime, however, this value may have changed from DELAY to MUTABLE (as is the case in Modification_14)

     */
    private void updateValuePropertiesOfParameter(AnalyserContext analyserContext,
                                                  VariableInfoContainer vic,
                                                  VariableInfo vi,
                                                  Variable variable) {
        //update @Immutable
        assert variable instanceof ParameterInfo;
        DV currentImmutable = vi.getProperty(IMMUTABLE);
        if (currentImmutable.isDelayed()) {
            DV formalImmutable = analyserContext.typeImmutable(variable.parameterizedType());
            vic.setProperty(IMMUTABLE, formalImmutable, INITIAL);
        }
        // update @Independent
        TypeInfo bestType = variable.parameterizedType().bestTypeInfo();
        if (bestType != null) {
            DV currentIndependent = vi.getProperty(Property.INDEPENDENT);
            if (currentIndependent.isDelayed()) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                DV independent = typeAnalysis == null ? DEPENDENT_DV : typeAnalysis.getProperty(Property.INDEPENDENT);
                vic.setProperty(Property.INDEPENDENT, independent, INITIAL);
            }
        }
    }

    private void fromFieldAnalyserIntoInitial(EvaluationContext evaluationContext,
                                              VariableInfoContainer vic,
                                              FieldReference fieldReference) {
        VariableInfo viInitial = vic.best(INITIAL);

        Properties map = fieldPropertyMap(evaluationContext, fieldReference.fieldInfo);
        Expression initialValue;
        FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);

        boolean myself = evaluationContext.isMyself(fieldReference);
        if (!myself && evaluationContext.inConstruction()) {
            DV immutable = map.getOrDefault(EXTERNAL_IMMUTABLE, MUTABLE_DV);
            DV ctx = contextImmutable(vic, evaluationContext, fieldAnalysis, immutable);
            map.overwrite(CONTEXT_IMMUTABLE, ctx);
        } else {
            assert MUTABLE_DV.equals(map.get(CONTEXT_IMMUTABLE));
            assert !map.containsKey(IMMUTABLE);
        }

        if (!viInitial.valueIsSet()) {
            // we don't have an initial value yet; the initial field value is only visible in constructors
            // and then only to direct references (this.field)
            if (methodAnalysis.getMethodInfo().isConstructor && fieldReference.scopeIsThis(evaluationContext.getCurrentType())) {
                initialValue = fieldAnalysis.getInitializerValue();
            } else {
                initialValue = fieldAnalysis.getValueForStatementAnalyser(getCurrentType().primaryType(),
                        fieldReference, flowData().getInitialTime());
            }
        } else {
            // only set properties copied from the field
            map.stream().forEach(e -> vic.setProperty(e.getKey(), e.getValue(), INITIAL));
            initialValue = viInitial.getValue();
            assert initialValue.isDone();
            // add the value properties from the current value to combined (do not set to initial!!)
        }

        if (myself) {
            map.put(IMMUTABLE, MUTABLE_DV);
            map.put(CONTAINER, NOT_CONTAINER_DV);
            map.put(INDEPENDENT, DEPENDENT_DV);
            map.put(IDENTITY, DV.FALSE_DV);
            map.put(IGNORE_MODIFICATIONS, NOT_IGNORE_MODS_DV);
        } else {
            Properties valueMap = evaluationContext.getValueProperties(viInitial.variable().parameterizedType(), initialValue);
            valueMap.stream().forEach(e -> map.merge(e.getKey(), e.getValue(), DV::max));

            CausesOfDelay causesOfDelay = valueMap.delays();
            if (causesOfDelay.isDelayed() && initialValue.isDone()) {
                initialValue = DelayedVariableExpression.forField(fieldReference, flowData.getInitialTime(), causesOfDelay);
            }
        }
        if (!viInitial.valueIsSet()) {
            LinkedVariables lv1 = viInitial.getLinkedVariables();
            LinkedVariables lv = lv1.isEmpty() ? vic.initialLinkedVariables() : lv1;
            vic.setValue(initialValue, lv, map, INITIAL);
        }
        /* copy into evaluation, but only if there is no assignment and no reading

        reading can change the value (e.g. when a modifying method call occurs), but we have a dedicated
        method that reads from INITIAL rather than EVAL, so we don't have to copy yet.

        for properties, which are incremental upon reading, we already copy into evaluation,
        because we don't have explicit code available
         */
        VariableInfo viEval = vic.best(Stage.EVALUATION);
        // not assigned in this statement
        if (viEval != viInitial && vic.isNotAssignedInThisStatement()) {
            if (!viEval.valueIsSet() && !initialValue.isEmpty() && !viEval.isRead()) {
                // whatever we do, we do NOT write CONTEXT properties, because they are written exactly once at the
                // end of the "apply" phase, even for variables that aren't read.
                // Neither do we write linked variables here.
                map.removeAll(GroupPropertyValues.PROPERTIES);
                vic.setValue(initialValue, null, map, EVALUATION);
            }
        }
    }

    private static final Set<Property> FROM_FIELD_ANALYSER_TO_PROPERTIES = EXTERNALS;

    @Override
    public void ensureMessages(Stream<Message> messageStream) {
        messageStream.forEach(this::ensure);
    }

    /*
    output the statement, but take into account the list of variables, there may be name clashes to be resolved
     */
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder ob = statement.output(qualification, this);
        Comment comment = statement.getStructure().comment();
        if (comment != null) {
            return comment.output(qualification).add(ob);
        }
        return ob;
    }

    @Override
    public boolean assignsToFields() {
        return variableStream().anyMatch(vi -> vi.variable() instanceof FieldReference && vi.isAssigned());
    }

    @Override
    public boolean noIncompatiblePrecondition() {
        return !(methodLevelData.combinedPreconditionIsFinal()
                && methodLevelData.combinedPreconditionGet().expression().isBoolValueFalse());
    }

    public boolean haveLocalMessages() {
        return !messages.isEmpty();
    }

    public Stream<Message> localMessageStream() {
        return messages.stream().filter(m -> ((LocationImpl) m.location()).info.getMethod() == methodAnalysis.getMethodInfo());
    }

    @Override
    public Stream<Message> messageStream() {
        return messages.stream();
    }

    // return progress
    public boolean ensureLocalVariableAssignedInThisLoop(String name) {
        if (!(localVariablesAssignedInThisLoop.isFrozen()) &&
                !localVariablesAssignedInThisLoop.contains(name)) {
            localVariablesAssignedInThisLoop.add(name);
            return true;
        }
        return false;
    }

    // single point for adding to the variables map, but at the moment, not enforced
    public void putVariable(String name, VariableInfoContainer vic) {
        variables.put(name, vic);
    }

    public void ensureCandidateVariableForNullPtrWarning(Variable variable) {
        if (!candidateVariablesForNullPtrWarning.contains(variable)) {
            candidateVariablesForNullPtrWarning.add(variable);
        }
    }

    public void freezeLocalVariablesAssignedInThisLoop() {
        localVariablesAssignedInThisLoop.freeze();
    }

    @Override
    public Stream<Variable> candidateVariablesForNullPtrWarningStream() {
        return candidateVariablesForNullPtrWarning.stream();
    }

    /**
     * @param toMerge  variables which will go through the merge process, they will get a -M VariableInfo
     * @param toIgnore variables to be ignored by the merge process, they will not get a -M VariableInfo
     * @param toRemove references to these variables (in value, in scope of field ref) will be replaced by Instance objects
     */
    private record PrepareMerge(EvaluationContext evaluationContext,
                                List<VariableInfoContainer> toMerge,
                                List<VariableInfoContainer> toIgnore,
                                Set<Variable> toRemove,
                                TranslationMapImpl.Builder bestValueForToRemove,
                                Map<Variable, Variable> renames,
                                TranslationMapImpl.Builder translationMap) {
        public PrepareMerge(EvaluationContext evaluationContext) {
            this(evaluationContext, new LinkedList<>(), new LinkedList<>(), new HashSet<>(), new TranslationMapImpl.Builder(),
                    new HashMap<>(), new TranslationMapImpl.Builder());
        }

        // we go over the list of variables to merge, and try to find if we need to rename them because
        // some scope has to be removed (field reference x.y where x cannot exist anymore).
        // this method relies on bestValueForToRemove
        // at the same time, when BOTH are present in the toMerge (in a subsequent iteration)
        // we remove the non-renamed
        // return new scope variables to be created and assigned
        private Set<LocalVariableReference> computeRenames() {
            TranslationMap renameMap = bestValueForToRemove.build();
            Set<LocalVariableReference> newScopeVariables = new HashSet<>();
            toMerge.removeIf(vic -> prepareRenameDecideToRemove(vic, renameMap, newScopeVariables));
            toIgnore.removeIf(vic -> prepareRenameDecideToRemove(vic, renameMap, newScopeVariables));
            return newScopeVariables;
        }

        private boolean prepareRenameDecideToRemove(VariableInfoContainer vic,
                                                    TranslationMap renameMap,
                                                    Set<LocalVariableReference> newScopeVariables) {
            Variable variable = vic.current().variable();
            RenameVariableResult rvr = renameVariable(variable, renameMap);
            if (rvr != null) {
                newScopeVariables.addAll(rvr.newScopeVariables);
                renames.put(variable, rvr.variable);
                translationMap.addVariableExpression(variable, rvr.variableExpression);
                translationMap.put(variable, rvr.variable);
                return false;
            }
            // scope variables that are created here, are filled in using renames
            return variable.hasScopeVariableCreatedAt(evaluationContext.statementIndex());
        }

        private record RenameVariableResult(Variable variable,
                                            VariableExpression variableExpression,
                                            List<LocalVariableReference> newScopeVariables) {
        }

        // serious example for this method is VariableScope_10; VS_6 shows that field references need to remain:
        // otherwise, we cannot see their assignment easily.
        private RenameVariableResult renameVariable(Variable variable, TranslationMap translationMap) {
            if (variable instanceof FieldReference fr) {
                Expression newScope = fr.scope.translate(evaluationContext.getAnalyserContext(), translationMap);
                if (newScope != fr.scope) {
                    assert fr.scopeVariable != null;
                    String name = "scope-" + fr.scopeVariable.simpleName() + ":" + evaluationContext.statementIndex();
                    // if statement index is 2, then 2~ is after 2.x.x, but before 3
                    VariableNature vn = new VariableNature.ScopeVariable(evaluationContext.statementIndex());
                    LocalVariable lv = new LocalVariable(Set.of(LocalVariableModifier.FINAL), name,
                            fr.scope.returnType(), List.of(), fr.getOwningType(), vn);
                    LocalVariableReference scopeVariable = new LocalVariableReference(lv, newScope);
                    Expression scope = new VariableExpression(fr.scope.getIdentifier(), scopeVariable);
                    FieldReference newFr = new FieldReference(evaluationContext.getAnalyserContext(), fr.fieldInfo,
                            scope, scopeVariable, fr.getOwningType());
                    VariableExpression ve = new VariableExpression(fr.scope.getIdentifier(), newFr,
                            VariableExpression.NO_SUFFIX, scope, null);
                    return new RenameVariableResult(newFr, ve, List.of(scopeVariable));
                }
                if (fr.scopeVariable instanceof FieldReference) {
                    RenameVariableResult rvr = renameVariable(fr.scopeVariable, translationMap);
                    if (rvr != null) {
                        throw new UnsupportedOperationException("Implement!");
                    }
                }
            }
            return null;
        }
    }

    // as a general remark: This and ReturnVariable variables are always merged, never removed
    private PrepareMerge mergeActions(EvaluationContext evaluationContext,
                                      List<ConditionAndLastStatement> lastStatements,
                                      Set<Variable> mergeEvenIfNotInSubBlocks) {
        PrepareMerge pm = new PrepareMerge(evaluationContext);
        // some variables will be picked up in the sub-blocks, others in the current block, others in both
        // we want to deal with them only once, though.
        Set<Variable> seen = new HashSet<>();
        Map<Variable, VariableInfoContainer> useVic = rawVariableStream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getValue().current().variable(), Map.Entry::getValue));

        // first group: variables seen in any of the sub blocks.
        // this includes those that do not exist at the top-level, i.e., they're local to the sub-blocks
        // they should never bubble up
        // variables in loop defined outside will be merged, and receive a special value in "replaceLocalVariables"
        // field references with a scope which needs to be removed, should equally be removed
        //
        // if recognized: remove (into remove+ignore), otherwise merge
        // we must add the VIC of the current statement, if it exists! If not we still cannot use the one from "below",
        // we must create a new one, but only if we're merging!
        Stream<VariableInfoContainer> fromSubBlocks = lastStatements.stream()
                .flatMap(st -> st.lastStatement().getStatementAnalysis().rawVariableStream().map(Map.Entry::getValue))
                .filter(vic -> vic.hasBeenAccessedInThisBlock(index));
        mergeAction(pm, seen, fromSubBlocks, vn -> vn.removeInSubBlockMerge(index), v -> true, useVic);

        // second group: those that exist at the block level, and were not present in any of the sub-blocks
        // (because of the "seen" map variables from the sub-blocks are ignored)
        //
        // should be removed and not merged: any variable created in this top level, such as
        // loop variables of for-each, for with LVC, try resource, instance-of pattern
        // variables in loop defined outside must have been accessed in the sub-block, by definition
        //
        // normal variables not accessed in the block, defined before the block, can be ignored
        //
        // if recognized: remove (into remove+ignore), otherwise ignore

        Set<Variable> alsoMergeStaticallyAssigned = new HashSet<>(mergeEvenIfNotInSubBlocks);
        Set<Variable> staticallyLinked = pm.toMerge.stream().flatMap(vic -> vic.best(EVALUATION).getLinkedVariables().staticallyAssignedStream()).collect(Collectors.toUnmodifiableSet());
        alsoMergeStaticallyAssigned.addAll(staticallyLinked);

        Stream<VariableInfoContainer> atTopLevel = rawVariableStream().map(Map.Entry::getValue);
        mergeAction(pm, seen, atTopLevel, vn -> vn.removeInMerge(index), alsoMergeStaticallyAssigned::contains, null);
        return pm;
    }

    private void mergeAction(PrepareMerge prepareMerge,
                             Set<Variable> seen,
                             Stream<VariableInfoContainer> stream,
                             Predicate<VariableNature> remove,
                             Predicate<Variable> mergeWhenNotRemove,
                             Map<Variable, VariableInfoContainer> useVic) {
        stream.forEach(vic -> {
            Variable variable = vic.current().variable();
            // vicToAdd is null when there is no VIC at this level. In the case a merge, we'll need to create one,
            // and we can send on the current one. In the case of toIgnore, we must skip this variable!
            // see Loops_19 as an example (variable "key", to be ignored in statement "1")
            VariableInfoContainer vicToAdd = useVic == null ? vic : useVic.get(variable);
            if (seen.add(variable)) {
                if (remove.test(vic.variableNature())) {
                    if (vicToAdd != null) {
                        prepareMerge.toIgnore.add(vicToAdd);
                    }
                    prepareMerge.toRemove.add(variable);
                } else {
                    if (mergeWhenNotRemove.test(variable)) {
                        VariableInfoContainer vicMerge = vicToAdd == null ? vic : vicToAdd;
                        if(useVic != null && vicToAdd == null) {
                            // we'll copy from "vic"
                            vic.ensureCopyToMerge();
                        }
                        /*
                        See Loops_21: if the vic was copied from lastStatements, and it did not exist before, we must
                        ensure that a delayed initial gets updated when the initial of the lastStatements is done.
                         */
                        if (vic != vicToAdd
                                && vicToAdd != null
                                && vic.isCopyToMerge()
                                && vicToAdd.getPreviousOrInitial().getValue().isDelayed()
                                && !vic.getPreviousOrInitial().getValue().isDelayed()) {
                            vicToAdd.copyInitialFrom(vic.getPreviousOrInitial());
                        }
                        prepareMerge.toMerge.add(vicMerge);
                    } else if (vicToAdd != null) {
                        prepareMerge.toIgnore.add(vicToAdd);
                    }
                    /* The following code fragment replaces the variable expressions with loop suffix by a delayed or instance value.
                    For now, it seems not necessary to do this (see e.g. TrieSimplified_5)
                    if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside && outside.statementIndex().equals(index)) {
                        VariableExpression ve = new VariableExpression(variable, new VariableExpression.VariableInLoop(index));
                        VariableInfo best = vic.best(EVALUATION);
                        Expression value;
                        Properties valueProperties = best.valueProperties();
                        CausesOfDelay delays = valueProperties.delays();
                        if (best.getValue().isDelayed() || delays.isDelayed()) {
                            value = DelayedVariableExpression.forLocalVariableInLoop(variable, delays);
                        } else {
                            value = Instance.forLoopVariable(index, variable, valueProperties);
                        }
                        prepareMerge.translationMap.put(ve, value);
                    }*/
                }
            }
        });
    }

    public record ConditionAndLastStatement(Expression condition,
                                            Expression absoluteState,
                                            String firstStatementIndexForOldStyleSwitch,
                                            StatementAnalyser lastStatement,
                                            DV executionOfLastStatement,
                                            boolean alwaysEscapes,
                                            boolean alwaysEscapesOrReturns) {
    }

    public record MergeResult(ProgressAndDelay analysisStatus, Expression translatedAddToStateAfterMerge) {
    }

    /**
     * From child blocks into the parent block; determine the value and properties for the current statement
     *
     * @param evaluationContext       for expression evaluations
     * @param lastStatements          the last statement of each of the blocks
     * @param atLeastOneBlockExecuted true if we can (potentially) discard the current value
     * @param statementTime           the statement time of subBlocks
     * @param setCnnVariables         variables that should receive CNN >= ENN because of escape in sub-block
     */
    public MergeResult mergeVariablesFromSubBlocks(EvaluationContext evaluationContext,
                                                   Expression stateOfConditionManagerBeforeExecution,
                                                   Expression addToStateAfterMerge,
                                                   List<ConditionAndLastStatement> lastStatements,
                                                   boolean atLeastOneBlockExecuted,
                                                   int statementTime,
                                                   Map<Variable, DV> setCnnVariables) {

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        PrepareMerge prepareMerge = mergeActions(evaluationContext, lastStatements, setCnnVariables.keySet());
        // 2 more steps: fill in PrepareMerge.bestValueForToRemove, then compute renames
        TranslationMapImpl.Builder outOfScopeBuilder = new TranslationMapImpl.Builder();
        Map<Variable, Expression> afterFiltering = new HashMap<>();
        for (Variable toRemove : prepareMerge.toRemove) {
            boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;
            List<ConditionAndVariableInfo> toMerge = filterSubBlocks(evaluationContext, lastStatements, toRemove,
                    inSwitchStatementOldStyle);
            if (!toMerge.isEmpty()) { // a try statement can have more than one, it's only the first we're interested
                // and finally, copy the result into prepareMerge
                VariableInfo best = toMerge.get(0).variableInfo();
                Identifier identifier = best.getIdentifier();
                Expression outOfScopeValue;
                Properties bestProperties = best.valueProperties();
                if (best.getValue().isDelayed() || bestProperties.delays().isDelayed()) {
                    CausesOfDelay causes = best.getValue().causesOfDelay().merge(bestProperties.delays().causesOfDelay());
                    outOfScopeValue = DelayedExpression.forOutOfScope(identifier, toRemove.simpleName(),
                            toRemove.parameterizedType(),
                            best.getValue(),
                            causes);
                    afterFiltering.put(toRemove, outOfScopeValue);
                } else {
                    // at the moment, there may be references to other variables to be removed inside the best.getValue()
                    // they will be replaced soon in applyTranslations()
                    // on the other hand, we will already avoid self-references!
                    // NOTE: Instance is based on identifier and type

                    outOfScopeValue = Instance.forMerge(identifier, best.variable().parameterizedType(), bestProperties);
                    // the following rule works fine for VS_10, but may be too limited
                    // TODO should we loop over all VDOL's, add them to the translation map?
                    if (isVariableInLoopDefinedOutside(best.getValue())) {
                        afterFiltering.put(toRemove, outOfScopeValue);
                    } else {
                        afterFiltering.put(toRemove, best.getValue());
                    }
                }
                outOfScopeBuilder.addVariableExpression(best.variable(), outOfScopeValue);

            } // else: See e.g. Loops_3: block not executed
        }
        TranslationMap instances = outOfScopeBuilder.setRecurseIntoScopeVariables(true).build();
        for (Map.Entry<Variable, Expression> entry : afterFiltering.entrySet()) {
            Expression expression = entry.getValue();
            Variable toRemove = entry.getKey();
            Expression bestValue = expression.translate(evaluationContext.getAnalyserContext(), instances);
            prepareMerge.bestValueForToRemove.addVariableExpression(toRemove, bestValue);
            prepareMerge.translationMap.addVariableExpression(toRemove, bestValue);
            prepareMerge.translationMap.put(expression, bestValue);
        }

        Map<Variable, LinkedVariables> linkedVariablesMap = new HashMap<>();
        // fields never go out of scope, but their scope variables may. "computeRenames" will create
        // new field references, each with new scope variables that also don't go out of scope anymore.
        // the same applies to dependent variables
        Set<LocalVariableReference> newScopeVariables = prepareMerge.computeRenames();
        for (LocalVariableReference lvr : newScopeVariables) {
            VariableInfoContainer newScopeVar;
            if (!variables.isSet(lvr.fullyQualifiedName())) {
                newScopeVar = createVariable(evaluationContext, lvr, statementTime(MERGE), new VariableNature.ScopeVariable(evaluationContext.statementIndex()));
                newScopeVar.ensureMerge(evaluationContext.getLocation(MERGE), evaluationContext.statementIndex() + ":M");
            } else {
                newScopeVar = variables.get(lvr.fullyQualifiedName());
            }
            Properties propertiesOfLvr = evaluationContext.getValueProperties(lvr.assignmentExpression);
            Expression scopeValue;
            CausesOfDelay causesOfDelay = propertiesOfLvr.delays();
            if (causesOfDelay.isDelayed() && lvr.assignmentExpression.isDone()) {
                scopeValue = DelayedExpression.forDelayedValueProperties(lvr.assignmentExpression.getIdentifier(),
                        lvr.assignmentExpression.returnType(), lvr.assignmentExpression, causesOfDelay, Properties.EMPTY);
            } else {
                scopeValue = lvr.assignmentExpression;
            }
            newScopeVar.setValue(scopeValue, null, propertiesOfLvr, MERGE);
            groupPropertyValues.setDefaultsForScopeVariable(lvr);
        }
        TranslationMap translationMap = prepareMerge.translationMap.build();

        Set<Variable> variablesWhereMergeOverwrites = new HashSet<>();

        CausesOfDelay delay = CausesOfDelay.EMPTY;
        boolean progress = false;
        Map<Variable, Integer> modificationTimes = new HashMap<>();

        for (VariableInfoContainer vic : prepareMerge.toMerge) {
            VariableInfo current = vic.current();
            Variable variable = current.variable();
            assert !variable.hasScopeVariableCreatedAt(index) : "should have been removed";
            Variable renamed = prepareMerge.renames.getOrDefault(variable, variable);

            VariableInfoContainer destination;
            if (!variables.isSet(renamed.fullyQualifiedName())) {
                VariableNature variableNature;
                if (renamed instanceof FieldReference fr) {
                    if (fr.scope.isDelayed()) {
                        variableNature = new VariableNature.DelayedScope();
                    } else {
                        variableNature = vic.variableNature();
                    }
                } else {
                    variableNature = vic.variableNature();
                }
                destination = createVariable(evaluationContext, renamed, statementTime, variableNature);
            } else if (variable == renamed) {
                destination = vic;
            } else {
                destination = variables.get(renamed.fullyQualifiedName());
            }
            if (destination.variableNature() instanceof VariableNature.DelayedScope ds) {
                ds.setIteration(evaluationContext.getIteration());
            }
            boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;

            Merge.ExpressionAndProperties overwriteValue = overwrite(evaluationContext, variable);
            assert overwriteValue == null || renamed == variable : "Overwrites do not go together with renames?";

            // use "variable" here rather than "renamed", this deals with data in the sub-blocks
            boolean localAtLeastOneBlock;
            List<ConditionAndVariableInfo> toMerge = filterSubBlocks(evaluationContext, lastStatements, variable,
                    inSwitchStatementOldStyle);
            if (variable instanceof ReturnVariable) {
                localAtLeastOneBlock = atLeastOneBlockExecuted && lastStatements.size() == toMerge.size() + lastStatements.stream().mapToInt(cav -> cav.alwaysEscapes() ? 1 : 0).sum();
            } else if (variable instanceof DependentVariable dv && dv.statementIndex.startsWith(index + ".")) {
                localAtLeastOneBlock = true; // the dependent variable was dynamically created inside one of the blocks
            } else {
                // in a Try statement, the blocks are successive rather than exclusive
                localAtLeastOneBlock = atLeastOneBlockExecuted && (statement instanceof TryStatement ||
                        lastStatements.size() == toMerge.size() + lastStatements.stream().mapToInt(cav -> cav.alwaysEscapesOrReturns() ? 1 : 0).sum());
            }
            if (toMerge.size() > 0) {
                try {
                    CausesOfDelay executionDelay = toMerge.stream().map(cavi -> cavi.executionOfLastStatement().causesOfDelay())
                            .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
                    Merge merge = new Merge(evaluationContext, destination, executionDelay);

                    // the main merge operation
                    ProgressAndDelay pad = merge.merge(stateOfConditionManagerBeforeExecution, overwriteValue,
                            localAtLeastOneBlock, toMerge, groupPropertyValues, translationMap);
                    delay = delay.merge(pad.causes());
                    progress |= pad.progress();

                    Stream<LinkedVariables> toMergeLvStream = toMerge.stream().map(cav -> cav.variableInfo().getLinkedVariables());
                    Stream<LinkedVariables> lvStream;
                    if (localAtLeastOneBlock) {
                        lvStream = toMergeLvStream;
                    } else {
                        LinkedVariables previousLv = vic.best(EVALUATION).getLinkedVariables();
                        lvStream = Stream.concat(Stream.of(previousLv), toMergeLvStream);
                    }
                    LinkedVariables linkedVariables = lvStream
                            .map(lv -> lv.translate(evaluationContext.getAnalyserContext(), translationMap))
                            .reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
                    if (executionDelay.isDelayed()) {
                        linkedVariablesMap.put(renamed, linkedVariables.changeNonStaticallyAssignedToDelay(executionDelay));
                    } else {
                        linkedVariablesMap.put(renamed, linkedVariables);
                    }
                    if (localAtLeastOneBlock) variablesWhereMergeOverwrites.add(renamed);

                    if (localAtLeastOneBlock &&
                            checkForOverwritingPreviousAssignment(variable, current, vic.variableNature(), toMerge)) {
                        assert variable == renamed : "Overwriting previous assignments doesn't go together with renames";
                        ensure(Message.newMessage(location(MERGE), Message.Label.OVERWRITING_PREVIOUS_ASSIGNMENT,
                                variable.simpleName()));
                    }
                    IntStream modificationTimeOfSubs = toMerge.stream().mapToInt(cav -> cav.variableInfo().getModificationTimeOrNegative());
                    int maxMod = modificationTimeOfSubs.reduce(0, (t1, t2) -> t1 < 0 || t2 < 0 ? -1 : Math.max(t1, t2));
                    modificationTimes.put(variable, maxMod);
                } catch (Throwable throwable) {
                    LOGGER.warn("Caught exception while merging variable {} (rename to {}} in {}, {}", variable, renamed,
                            methodAnalysis.getMethodInfo().fullyQualifiedName, index);
                    throw throwable;
                }
            } else if (destination.hasMerge()) {
                assert evaluationContext.getIteration() > 0; // or it wouldn't have had a merge
                // in previous iterations there was data for us, but now there isn't; copy from I/E into M
                progress |= destination.copyFromEvalIntoMerge(groupPropertyValues);
                linkedVariablesMap.put(renamed, destination.best(MERGE).getLinkedVariables());
            } else {
                throw new UnsupportedOperationException("Have not yet encountered this situation");
            }
        }

        rawVariableStream().forEach(e -> {
            if (e.getValue().variableNature() instanceof VariableNature.DelayedScope ds && ds.getIteration() < evaluationContext.getIteration()) {
                LOGGER.debug("Removing {}, not actively merged anymore", e.getKey());
                e.getValue().remove();
            }
        });

        CausesOfDelay conditionCauses = lastStatements.stream().map(cav -> cav.condition.causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        ProgressAndDelay soFar = new ProgressAndDelay(progress, delay);
        ProgressAndDelay mergeStatus = linkingAndGroupProperties(evaluationContext, groupPropertyValues, linkedVariablesMap,
                modificationTimes, statementTime,
                variablesWhereMergeOverwrites, newScopeVariables, prepareMerge, setCnnVariables, translationMap,
                conditionCauses, soFar).addProgress(progress);

        Expression translatedAddToState = addToStateAfterMerge == null ? null :
                addToStateAfterMerge.translate(evaluationContext.getAnalyserContext(), translationMap);
        return new MergeResult(mergeStatus, translatedAddToState);
    }

    private boolean isVariableInLoopDefinedOutside(Expression value) {
        IsVariableExpression ive;
        if ((ive = value.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfoContainer vic = findOrNull(ive.variable());
            return vic != null && index.equals(vic.variableNature().getStatementIndexOfBlockVariable());
        }
        return false;
    }

    /**
     * In some rare situations we do not want to merge, but to write a specific value.
     * This is the case when the exit value of a loop is known; see Range_3
     */
    private Merge.ExpressionAndProperties overwrite(EvaluationContext evaluationContext, Variable variable) {
        if (statement instanceof LoopStatement) {
            Expression exit = rangeData.getRange().exitValue(primitives, variable);
            if (exit != null && stateData.noExitViaReturnOrBreak()) {
                Properties properties = evaluationContext.getValueProperties(exit);
                return new Merge.ExpressionAndProperties(exit, properties);
            }
        }
        return null;
    }

    private List<ConditionAndVariableInfo> filterSubBlocks(EvaluationContext evaluationContext,
                                                           List<ConditionAndLastStatement> lastStatements,
                                                           Variable variable,
                                                           boolean inSwitchStatementOldStyle) {
        String fqn = variable.fullyQualifiedName();
        return lastStatements.stream()
                .filter(e2 -> e2.lastStatement().getStatementAnalysis().variableIsSet(fqn))
                .map(e2 -> {
                    VariableInfoContainer vic2 = e2.lastStatement().getStatementAnalysis().getVariable(fqn);
                    return new ConditionAndVariableInfo(e2.condition(), e2.absoluteState(),
                            vic2.current(), e2.alwaysEscapes(), e2.alwaysEscapesOrReturns(),
                            vic2.variableNature(), e2.firstStatementIndexForOldStyleSwitch(),
                            e2.lastStatement().getStatementAnalysis().index(),
                            index,
                            e2.lastStatement().getStatementAnalysis(),
                            e2.executionOfLastStatement,
                            variable,
                            evaluationContext);
                })
                .filter(cav -> acceptVariableForMerging(cav, inSwitchStatementOldStyle)).toList();
    }

    private boolean acceptVariableForMerging(ConditionAndVariableInfo cav, boolean inSwitchStatementOldStyle) {
        if (inSwitchStatementOldStyle) {
            assert cav.firstStatementIndexForOldStyleSwitch() != null;
            // if the variable is assigned in the block, it has to be assigned after the first index
            // "the block" is the switch statement; otherwise,
            String cavLatest = cav.variableInfo().getAssignmentIds().getLatestAssignmentIndex();
            if (cavLatest.compareTo(index) > 0) {
                return cav.firstStatementIndexForOldStyleSwitch().compareTo(cavLatest) <= 0;
            }
            return cav.firstStatementIndexForOldStyleSwitch().compareTo(cav.variableInfo().getReadId()) <= 0;
        }
        return cav.variableInfo().isRead() || cav.variableInfo().isAssigned();
    }

    private record BackLinkForEachResult(Set<Variable> newlyCreated, CausesOfDelay delays) {
    }

    private ProgressAndDelay linkingAndGroupProperties(EvaluationContext evaluationContext,
                                                       GroupPropertyValues groupPropertyValues,
                                                       Map<Variable, LinkedVariables> linkedVariablesMap,
                                                       Map<Variable, Integer> modificationTimes,
                                                       int statementTime,
                                                       Set<Variable> variablesWhereMergeOverwrites,
                                                       Set<LocalVariableReference> newlyCreatedScopeVariables,
                                                       PrepareMerge prepareMerge,
                                                       Map<Variable, DV> setCnnVariables,
                                                       TranslationMap translationMap,
                                                       CausesOfDelay conditionCauses,
                                                       ProgressAndDelay delay) {

        BackLinkForEachResult backLink;
        if (statement instanceof ForEachStatement) {
            backLink = backLinkIterable(evaluationContext, linkedVariablesMap);
        } else {
            backLink = new BackLinkForEachResult(Set.of(), CausesOfDelay.EMPTY);
        }
        for (VariableInfoContainer vic : prepareMerge.toIgnore) {
            Variable variable = vic.current().variable();
            Variable renamed = prepareMerge.renames.get(variable);
            if (renamed != null) {
                ensureDestination(renamed, vic, evaluationContext, statementTime(MERGE));
            }
        }

        // then, per cluster of variables
        // which variables should we consider? linkedVariablesMap provides the linked variables from the sub-blocks
        // create looks at these+previous, minus those to be removed.
        Function<Variable, LinkedVariables> linkedVariablesFromBlocks =
                v -> linkedVariablesMap.getOrDefault(v, LinkedVariables.EMPTY);
        // we include -E in touched, see Basics_8 (j, k in statement 4)
        Set<Variable> touched = touchedStream(linkedVariablesMap, newlyCreatedScopeVariables, prepareMerge);
        boolean oneBranchHasBecomeUnreachable = oneBranchHasBecomeUnreachable();
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(this, MERGE,
                oneBranchHasBecomeUnreachable,
                (vic, v) -> !touched.contains(v),
                variablesWhereMergeOverwrites,
                linkedVariablesFromBlocks, evaluationContext);

        boolean progress = computeLinkedVariables.writeLinkedVariables(computeLinkedVariables, touched,
                prepareMerge.toRemove, linkedVariablesMap.keySet());

        for (Variable variable : touched) {
            if ((!linkedVariablesMap.containsKey(variable) || backLink.newlyCreated.contains(variable)) &&
                    !(variable instanceof LocalVariableReference lvr && newlyCreatedScopeVariables.contains(lvr))) {
                VariableInfoContainer vic = variables.getOrDefaultNull(variable.fullyQualifiedName());
                assert vic != null;
                Variable renamed = prepareMerge.renames.get(variable);
                if (renamed != null) {
                    // copy from vic into the renamed variable
                    VariableInfoContainer vicRenamed = variables.get(renamed.fullyQualifiedName());
                    progress |= vic.copyNonContextFromPreviousOrEvalToMergeOfOther(groupPropertyValues, vicRenamed);
                } else {
                    progress |= vic.copyNonContextFromPreviousOrEvalToMerge(groupPropertyValues);
                }
            }
        }
        HashSet<VariableInfoContainer> ignoredNotTouched = new HashSet<>(prepareMerge.toIgnore);
        ignoredNotTouched.removeIf(vic -> touched.contains(vic.current().variable())
                || prepareMerge.renames.containsKey(vic.current().variable()));

        CausesOfDelay externalDelaysOnIgnoredVariables = CausesOfDelay.EMPTY;
        for (VariableInfoContainer vic : ignoredNotTouched) {
            CausesOfDelay delays = vic.copyAllFromPreviousOrEvalIntoMergeIfMergeExists();
            externalDelaysOnIgnoredVariables = externalDelaysOnIgnoredVariables.merge(delays);
            // IMPORTANT: no progress associated with this---is that correct?
        }

        if (translationMap.hasVariableTranslations()) {
            groupPropertyValues.translate(evaluationContext.getAnalyserContext(), translationMap);
        }

        ProgressAndDelay ennStatus = computeLinkedVariables.write(EXTERNAL_NOT_NULL,
                groupPropertyValues.getMap(EXTERNAL_NOT_NULL));

        Map<Variable, DV> cnnMap = groupPropertyValues.getMap(CONTEXT_NOT_NULL);
        for (Map.Entry<Variable, DV> e : setCnnVariables.entrySet()) {
            cnnMap.merge(e.getKey(), e.getValue(), DV::max);
        }
        ProgressAndDelay cnnStatus = computeLinkedVariables.write(CONTEXT_NOT_NULL, cnnMap);

        ProgressAndDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        ProgressAndDelay extContStatus = computeLinkedVariables.write(CONTAINER_RESTRICTION,
                groupPropertyValues.getMap(CONTAINER_RESTRICTION));

        ProgressAndDelay extIgnModStatus = computeLinkedVariables.write(EXTERNAL_IGNORE_MODIFICATIONS,
                groupPropertyValues.getMap(EXTERNAL_IGNORE_MODIFICATIONS));

        // extra delay.causes(): before we know whether branches get excluded, we cannot decide
        ProgressAndDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE,
                groupPropertyValues.getMap(CONTEXT_IMMUTABLE));

        ProgressAndDelay cContStatus = computeLinkedVariables.write(CONTEXT_CONTAINER,
                groupPropertyValues.getMap(CONTEXT_CONTAINER));

        int statementTimeDelta = statementTime - statementTime(EVALUATION);
        ProgressAndDelay cmStatus = computeLinkedVariables.writeContextModified(evaluationContext.getAnalyserContext(),
                groupPropertyValues.getMap(CONTEXT_MODIFIED), Map.of(), statementTimeDelta, modificationTimes,
                conditionCauses, true);

        CausesOfDelay delaysOfValuesOfIgnored = prepareMerge.toIgnore.stream()
                .map(vic -> vic.best(MERGE).getValue().causesOfDelay()).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        return delay
                .combine(backLink.delays)
                .combine(ennStatus).combine(cnnStatus).combine(cmStatus).combine(extImmStatus)
                .combine(extContStatus).combine(cImmStatus).combine(cContStatus).combine(extIgnModStatus)
                .merge(externalDelaysOnIgnoredVariables)
                .merge(delaysOfValuesOfIgnored)
                .addProgress(progress);
    }

    /*
    Example code: 'for(T t: ts) { consumer.accept(t); }'
    t links to consumer:3, ts:3, we want to connect consumer and ts with :4

    See Independent1_4.
    The reason we need this code is that links are unidirectional, and the links from t will disappear as the loop var
    goes out of scope.
    The reason we need this code HERE is that we must have the delay to consumer:-1 from the very first iteration.
    We cannot add it during evaluationOfForEachVariable() in the first iteration, as we don't know which variable to link to.
     */
    private BackLinkForEachResult backLinkIterable(EvaluationContext evaluationContext,
                                                   Map<Variable, LinkedVariables> linkedVariablesMap) {

        Set<Variable> newToLinkedVariablesMap = new HashSet<>();
        CausesOfDelay causes = CausesOfDelay.EMPTY;

        StatementAnalysis first = navigationData.blocks.get().get(0).orElse(null);
        StatementAnalysis last = first == null ? null : first.lastStatement();
        if (last != null) {
            Expression initialiser = statement.getStructure().initialisers().get(0);
            if (initialiser instanceof LocalVariableCreation lvc) {
                assert lvc.hasSingleDeclaration();
                Variable loopVar = lvc.localVariableReference;
                VariableInfo latestVariableInfo = last.getLatestVariableInfo(loopVar.fullyQualifiedName());
                if (latestVariableInfo != null) {
                    LinkedVariables linkedOfLoopVar = latestVariableInfo.getLinkedVariables();
                    // any --3--> link to a variable not local to the loop, we also point to the iterable as <--4-->

                    EvaluationResult context = EvaluationResult.from(evaluationContext);
                    LinkedVariables linkedVariables = stateData.valueOfExpression.get().linkedVariables(context);

                    for (Map.Entry<Variable, DV> lve : linkedVariables) {
                        Variable iterableVar = lve.getKey();

                        for (Map.Entry<Variable, DV> e : linkedOfLoopVar) {
                            Variable targetOfLoopVar = e.getKey();
                            if (!targetOfLoopVar.equals(iterableVar)) {
                                if (!linkedVariablesMap.containsKey(targetOfLoopVar)) {
                                    newToLinkedVariablesMap.add(targetOfLoopVar);
                                }
                                if (!linkedVariablesMap.containsKey(iterableVar)) {
                                    newToLinkedVariablesMap.add(iterableVar);
                                }
                                if (e.getValue().isDelayed()) {
                                    link(linkedVariablesMap, iterableVar, targetOfLoopVar, e.getValue());
                                    link(linkedVariablesMap, targetOfLoopVar, iterableVar, e.getValue());
                                    causes = causes.merge(e.getValue().causesOfDelay());
                                } else if (LinkedVariables.LINK_IS_HC_OF.equals(e.getValue())) {
                                    link(linkedVariablesMap, iterableVar, targetOfLoopVar, LinkedVariables.LINK_COMMON_HC);
                                    link(linkedVariablesMap, targetOfLoopVar, iterableVar, LinkedVariables.LINK_COMMON_HC);
                                }
                            }
                        }
                    }
                } else {
                    LOGGER.debug("possible in iteration 0 of a loop, see e.g. Loops_3");
                }
            } else throw new UnsupportedOperationException("?? expect lvc, got " + initialiser);
        }
        return new BackLinkForEachResult(Set.copyOf(newToLinkedVariablesMap), causes);
    }

    private void link(Map<Variable, LinkedVariables> linkedVariablesMap, Variable from, Variable to, DV linkLevel) {
        LinkedVariables lv = linkedVariablesMap.get(from);
        if (lv == null) {
            linkedVariablesMap.put(from, LinkedVariables.of(to, linkLevel));
        } else {
            linkedVariablesMap.put(from, lv.merge(LinkedVariables.of(to, linkLevel)));
        }
    }

    /*
    actually: at least one branch, this is possible in switch statements. Generally, it'll be the one of the two arms
    in an "if-else" construct, or the single branch of an "if".
     */
    private boolean oneBranchHasBecomeUnreachable() {
        if (navigationData.hasSubBlocks()) {
            return navigationData.blocks.get().stream()
                    .anyMatch(optSa -> optSa.isPresent() && optSa.get().flowData().isUnreachable());
        }
        return false;
    }

    private Set<Variable> touchedStream(Map<Variable, LinkedVariables> linkedVariablesMap, Set<? extends Variable> newlyCreatedScopeVariables, PrepareMerge prepareMerge) {
        return Stream.concat(newlyCreatedScopeVariables.stream(), Stream.concat(Stream.concat(linkedVariablesMap.keySet().stream(),
                                linkedVariablesMap.values().stream().flatMap(lv -> lv.variables().keySet().stream())),
                        variables.valueStream().filter(vic -> vic.hasEvaluation() ||
                                        // the following condition is necessary to include fields with a scope in newlyCreatedScopeVariables, see e.g. InstanceOf_16
                                        vic.current().variable().containsAtLeastOneOf(newlyCreatedScopeVariables))
                                .map(e -> e.current().variable())))
                .filter(v -> !prepareMerge.toRemove.contains(v)
                        && !prepareMerge.renames.containsKey(v)
                        && variables.isSet(v.fullyQualifiedName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void ensureDestination(Variable renamed,
                                   VariableInfoContainer vic,
                                   EvaluationContext evaluationContext,
                                   int statementTime) {
        if (!variables.isSet(renamed.fullyQualifiedName())) {
            VariableNature variableNature;
            if (renamed instanceof FieldReference fr) {
                if (fr.scope.isDelayed()) {
                    variableNature = new VariableNature.DelayedScope();
                } else {
                    variableNature = vic.variableNature();
                }
            } else {
                variableNature = vic.variableNature();
            }
            createVariable(evaluationContext, renamed, statementTime, variableNature);
        }
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
                VariableInfoContainer localVic = cav.lastStatement().getVariableOrDefaultNull(fqn);
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
                    if (!sa.flowData().getGuaranteedToBeReachedInCurrentBlock().equals(FlowData.ALWAYS)) return false;
                    if (current.isRead()) {
                        if (current.getReadId().compareTo(current.getAssignmentIds().getLatestAssignment()) < 0) {
                            return false;
                        }
                        // so there is reading AFTER... but
                        // we'll need to double-check that there was no reading before the assignment!
                        // secondly, we want to ensure that the assignment takes place unconditionally in the block

                        VariableInfoContainer atAssignment = sa.getVariable(fqn);
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
    @Override
    public StatementAnalysis navigateTo(String target) {
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

    /*
    create a variable, potentially even assign an initial value and a linked variables set.
    everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
     */
    @Override
    public VariableInfoContainer createVariable(EvaluationContext evaluationContext,
                                                Variable variable,
                                                int statementTime,
                                                VariableNature variableNature) {
        String fqn = variable.fullyQualifiedName();
        assert evaluationContext.getIteration() == 0
                : "Cannot make new variables in iteration " + evaluationContext.getIteration() + ": " + fqn;
        if (variables.isSet(fqn)) {
            throw new UnsupportedOperationException("Already exists: " +
                    fqn + " in " + index + ", " + methodAnalysis.getMethodInfo().fullyQualifiedName);
        }

        VariableInfoContainer vic = VariableInfoContainerImpl.newVariable(location(INITIAL), variable,
                variableNature, navigationData.hasSubBlocks());
        putVariable(variable.fullyQualifiedName(), vic);

        // linked variables travel from the parameters via the statements to the fields
        if (variable instanceof ReturnVariable returnVariable) {
            initializeReturnVariable(vic, returnVariable);

        } else if (variable instanceof This thisVar) {
            initializeThis(vic, evaluationContext, thisVar);

        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            initializeParameter(vic, evaluationContext, parameterInfo);

        } else if (variable instanceof FieldReference fieldReference) {
            initializeFieldReference(vic, evaluationContext, fieldReference);

        } else if (variable instanceof LocalVariableReference || variable instanceof DependentVariable) {
            // forEach() goes through a different system than for(), see code in SAApply.potentiallyModifyEvaluationResult and
            // ParameterizedType_0 test
            if (variableNature instanceof VariableNature.LoopVariable && !(statement instanceof ForEachStatement)
                    || variableNature instanceof VariableNature.ScopeVariable) {
                Identifier identifier = evaluationContext.getLocation(INITIAL).identifier();
                initializeLoopVariable(identifier, vic, variable, evaluationContext.getAnalyserContext());
            } else {
                initializeLocalOrDependentVariable(vic, variable, EvaluationResult.from(evaluationContext));
            }
        } else {
            throw new UnsupportedOperationException("? initialize variable of type " + variable.getClass());
        }
        return vic;
    }

    private void initializeLoopVariable(Identifier identifier,
                                        VariableInfoContainer vic,
                                        Variable variable,
                                        AnalyserContext analyserContext) {
        // but, because we don't evaluate the assignment, we need to assign some value to the loop variable
        // otherwise we'll get delays
        // especially in the case of forEach, the lvc.expression is empty (e.g., 'String s') anyway
        // an assignment may be difficult.
        // we should not worry about them
        ParameterizedType parameterizedType = variable.parameterizedType();
        Properties valueProperties = analyserContext.defaultValueProperties(parameterizedType, true);
        valueProperties.replaceDelaysByMinimalValue();
        Instance instance = Instance.forLoopVariable(identifier, variable, valueProperties);
        Properties properties = Properties.of(Map.of(
                EXTERNAL_NOT_NULL, EXTERNAL_NOT_NULL.valueWhenAbsent(),
                EXTERNAL_IMMUTABLE, EXTERNAL_IMMUTABLE.valueWhenAbsent(),
                CONTAINER_RESTRICTION, CONTAINER_RESTRICTION.valueWhenAbsent(),
                EXTERNAL_IGNORE_MODIFICATIONS, EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent(),
                CONTEXT_MODIFIED, DV.FALSE_DV,
                CONTEXT_NOT_NULL, valueProperties.get(NOT_NULL_EXPRESSION),
                CONTEXT_IMMUTABLE, valueProperties.get(IMMUTABLE),
                CONTEXT_CONTAINER, CONTAINER_RESTRICTION.falseDv
        ));
        Properties allProperties = valueProperties.combine(properties);
        vic.setValue(instance, LinkedVariables.EMPTY, allProperties, INITIAL);
        // the linking (normal, and content) can only be done after evaluating the expression over which we iterate
    }

    private void initializeLocalOrDependentVariable(VariableInfoContainer vic,
                                                    Variable variable,
                                                    EvaluationResult context) {
        DV defaultNotNull = AnalysisProvider.defaultNotNull(variable.parameterizedType());
        Properties properties = sharedContext(defaultNotNull);
        for (Property ext : EXTERNALS) {
            properties.put(ext, ext.valueWhenAbsent());
        }
        Expression initialValue;
        LinkedVariables linkedVariables;
        if (variable instanceof DependentVariable dv) {
            Expression arrayBase = new VariableExpression(dv.arrayExpression().getIdentifier(), dv.arrayVariable());
            LinkedVariables lvArrayBase = arrayBase.linkedVariables(context);
            DV independent = determineIndependentOfArrayBase(context, arrayBase);
            CausesOfDelay causesOfDelay = independent.causesOfDelay().merge(lvArrayBase.causesOfDelay());
            Expression arrayValue;
            if (causesOfDelay.isDelayed()) {
                arrayValue = DelayedVariableExpression.forVariable(dv, flowData.getTimeAfterEvaluation(), causesOfDelay);
            } else {
                arrayValue = Instance.genericArrayAccess(Identifier.generate("dep var"), context, arrayBase, dv);
            }
            Properties valueProperties = context.evaluationContext().getValueProperties(arrayValue);
            DV lvIndependent = LinkedVariables.fromIndependentToLinkedVariableLevel(independent,
                    // FIXME EMPTY is not going to cut it
                    dv.parameterizedType, SetOfTypes.EMPTY);
            if (lvIndependent.equals(LinkedVariables.LINK_INDEPENDENT)) {
                linkedVariables = vic.initialLinkedVariables();
                initialValue = arrayValue;
            } else {
                linkedVariables = lvArrayBase.changeAllTo(lvIndependent);
                initialValue = PropertyWrapper.propertyWrapper(arrayValue, linkedVariables);
            }
            valueProperties.stream().forEach(e -> properties.put(e.getKey(), e.getValue()));
        } else {
            initialValue = UnknownExpression.forNotYetAssigned(Identifier.generate("not yet assigned"), variable.parameterizedType());
            linkedVariables = vic.initialLinkedVariables();
        }
        vic.setValue(initialValue, linkedVariables, properties, INITIAL);
    }

    /*
     If the array base type is not immutable, we should return DEPENDENT.
     See DependentVariables_1,_2
     */
    private static DV determineIndependentOfArrayBase(EvaluationResult context, Expression value) {
        ParameterizedType arrayBaseType = value.returnType().copyWithoutArrays();
        if (context.evaluationContext().isMyself(arrayBaseType))
            return MultiLevel.NOT_INVOLVED_DV; // BREAK INFINITE LOOP
        // IMPORTANT: currentType == null, we've done the hidden content check already
        DV immutable = context.getAnalyserContext().typeImmutable(arrayBaseType);
        if (immutable.isDelayed()) {
            return immutable;
        }
        int immutableLevel = MultiLevel.level(immutable);
        return MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
    }

    private void initializeReturnVariable(VariableInfoContainer vic, ReturnVariable returnVariable) {
        DV defaultNotNull = AnalysisProvider.defaultNotNull(methodAnalysis.getMethodInfo().returnType());
        Properties properties = sharedContext(defaultNotNull);

        properties.put(NOT_NULL_EXPRESSION, defaultNotNull);
        properties.put(IDENTITY, IDENTITY.falseDv);
        properties.put(IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv);
        properties.put(CONTAINER, CONTAINER.falseDv);
        properties.put(IMMUTABLE, MUTABLE_DV);
        properties.put(INDEPENDENT, INDEPENDENT.falseDv);

        for (Property ext : Property.EXTERNALS) {
            properties.put(ext, ext.valueWhenAbsent());
        }
        UnknownExpression value = UnknownExpression.forReturnVariable(methodAnalysis.getMethodInfo().identifier,
                returnVariable.returnType);
        vic.setValue(value, LinkedVariables.EMPTY, properties, INITIAL);
    }

    private void initializeThis(VariableInfoContainer vic, EvaluationContext evaluationContext, This thisVar) {
        // context properties
        Properties properties = sharedContext(MultiLevel.EFFECTIVELY_NOT_NULL_DV);

        // value properties
        properties.put(IDENTITY, IDENTITY.falseDv);
        properties.put(IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv);
        properties.put(CONTAINER, CONTAINER.falseDv);
        properties.put(IMMUTABLE, IMMUTABLE.falseDv);
        properties.put(INDEPENDENT, INDEPENDENT.falseDv);
        properties.put(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);

        // external: not relevant, except for external immutable, used for tracking the before/after state on the object
        for (Property ext : Property.EXTERNALS) {
            if (ext != EXTERNAL_IMMUTABLE) properties.put(ext, ext.valueWhenAbsent());
        }
        // no hidden content check for this
        DV currentImmutable = evaluationContext.getAnalyserContext().typeImmutable(thisVar.typeAsParameterizedType);
        properties.put(EXTERNAL_IMMUTABLE, currentImmutable);

        Instance value = Instance.forCatchOrThis(index, thisVar, properties);
        vic.setValue(value, vic.initialLinkedVariables(), properties, INITIAL);
    }

    private void initializeFieldReference(VariableInfoContainer vic, EvaluationContext evaluationContext, FieldReference fieldReference) {
        FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);

        // start with context properties
        Properties properties = sharedContext(AnalysisProvider.defaultNotNull(fieldReference.fieldInfo.type));
        Expression value = fieldAnalysis.getValueForStatementAnalyser(getCurrentType().primaryType(),
                fieldReference, flowData.getInitialTime());

        Properties combined;
        boolean myself = evaluationContext.isMyself(fieldReference);
        boolean wroteExtIgnMod;
        if (myself && !fieldReference.fieldInfo.fieldInspection.get().isStatic()) {
            // captures self-referencing instance fields (but not static fields, as in Enum_)
            // a similar check exists in SAApply
            combined = evaluationContext.ensureMyselfValueProperties(properties);
            wroteExtIgnMod = false;
        } else {
            // the value and its properties are taken from the field analyser
            Properties valueProps = evaluationContext.getValueProperties(fieldReference.parameterizedType, value);
            combined = properties.combine(valueProps);
            if (evaluationContext.inConstruction()) {
                properties.put(EXTERNAL_IGNORE_MODIFICATIONS, EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent());
                wroteExtIgnMod = true;
                if (myself) {
                    assert MUTABLE_DV.equals(properties.get(CONTEXT_IMMUTABLE));
                } else {
                    DV immutable = contextImmutable(vic, evaluationContext, fieldAnalysis, valueProps.get(IMMUTABLE));
                    properties.overwrite(CONTEXT_IMMUTABLE, immutable);
                }
            } else {
                wroteExtIgnMod = false;
            }
        }
        // the external properties
        for (Property property : FROM_FIELD_ANALYSER_TO_PROPERTIES) {
            if (!wroteExtIgnMod || property != EXTERNAL_IGNORE_MODIFICATIONS) {
                DV v = fieldAnalysis.getProperty(property);
                combined.put(property, v);
            }
        }
        Expression toWrite;
        if (value.isDone()) {
            CausesOfDelay causes = combined.delays();
            if (causes.isDelayed()) {
                toWrite = DelayedVariableExpression.forDelayedValueProperties(fieldReference, statementTime(INITIAL),
                        properties, causes);
            } else {
                toWrite = value;
            }
        } else {
            toWrite = value;
        }
        vic.setValue(toWrite, vic.initialLinkedVariables(), combined, INITIAL);
    }

    private DV contextImmutable(VariableInfoContainer vic, EvaluationContext evaluationContext, FieldAnalysis fieldAnalysis, DV valueProperty) {
        DV immutable;
        if (vic.getPreviousOrInitial().isAssigned()) {
            // if there has been an assignment use:
            immutable = valueProperty;
        } else if (fieldAnalysis.getFieldInfo().owner.primaryType().equals(evaluationContext.getCurrentType().primaryType())) {
            Expression initializerValue = fieldAnalysis.getInitializerValue();
            if (initializerValue == null) {
                immutable = DelayFactory.createDelay(location(INITIAL), CauseOfDelay.Cause.INITIAL_VALUE);
            } else {
                ParameterizedType pt = initializerValue.returnType();
                immutable = evaluationContext.getAnalyserContext().typeImmutable(pt);
            }
        } else {
            immutable = MUTABLE_DV; // not relevant to this computation, like System.out
        }
        return immutable;
    }

    private void initializeParameter(VariableInfoContainer vic, EvaluationContext evaluationContext, ParameterInfo parameterInfo) {
        ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);

        // start with context properties
        ParameterizedType type = parameterInfo.parameterizedType;
        Properties properties = sharedContext(AnalysisProvider.defaultNotNull(type));

        // the value properties are not delayed (there's an assertion in the Instance factory method)
        DV notNull = parameterAnalysis.getProperty(NOT_NULL_PARAMETER)
                .maxIgnoreDelay(AnalysisProvider.defaultNotNull(type));
        properties.put(NOT_NULL_EXPRESSION, notNull);

        // the external properties may be delayed, but if so they're delayed in the correct way!
        for (Property property : FROM_PARAMETER_ANALYSER_TO_PROPERTIES) {
            DV v = parameterAnalysis.getProperty(property);
            properties.put(property, v);
        }
        properties.put(EXTERNAL_IGNORE_MODIFICATIONS, EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent());

        DV formallyImmutable = evaluationContext.getAnalyserContext().typeImmutable(type);
        DV immutable = IMMUTABLE.max(parameterAnalysis.getProperty(IMMUTABLE), formallyImmutable)
                .replaceDelayBy(MUTABLE_DV.maxIgnoreDelay(formallyImmutable));

        DV formallyIndependent = evaluationContext.getAnalyserContext().typeIndependent(type);
        DV independent = INDEPENDENT.max(parameterAnalysis.getProperty(INDEPENDENT), formallyIndependent)
                .replaceDelayBy(MultiLevel.DEPENDENT_DV.maxIgnoreDelay(formallyIndependent));

        /*
        See ComputingTypeAnalyser.correctIndependentFunctionalInterface(), Lazy. A functional interface comes in as the
        parameter of a non-private method. Modifications on its single, modifying method are ignored. As a consequence,
        we treat the object as immutable.
         */
        DV ignoreModifications = parameterAnalysis.getProperty(IGNORE_MODIFICATIONS)
                .maxIgnoreDelay(IGNORE_MODIFICATIONS.falseDv);
        if (ignoreModifications.equals(IGNORE_MODS_DV)
                && type.isFunctionalInterface()
                && !parameterAnalysis.getParameterInfo().getMethod().methodInspection.get().isPrivate()) {
            properties.put(IMMUTABLE, immutable.max(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV));
            properties.put(INDEPENDENT, independent.max(MultiLevel.INDEPENDENT_HC_DV));
        } else {
            properties.put(IMMUTABLE, immutable);
            properties.put(INDEPENDENT, independent);
        }

        // if the parameter is not explicitly annotated as a container, we can take a default value
        DV container = parameterAnalysis.getProperty(CONTAINER).maxIgnoreDelay(MultiLevel.NOT_CONTAINER_DV);
        properties.put(CONTAINER, container);

        boolean identity = parameterInfo.index == 0;
        properties.put(IDENTITY, DV.fromBoolDv(identity));

        Expression value = Instance.initialValueOfParameter(parameterInfo, properties);
        vic.setValue(value, vic.initialLinkedVariables(), properties, INITIAL);
    }

    private Properties sharedContext(DV contextNotNull) {
        Properties result = Properties.writable();
        result.put(CONTEXT_NOT_NULL, contextNotNull);
        result.put(CONTEXT_IMMUTABLE, MUTABLE_DV);
        result.put(CONTEXT_MODIFIED, DV.FALSE_DV);
        result.put(CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        return result;
    }

    private Properties fieldPropertyMap(EvaluationContext evaluationContext,
                                        FieldInfo fieldInfo) {
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
        Properties result = sharedContext(AnalysisProvider.defaultNotNull(fieldInfo.type));

        for (Property vp : FROM_FIELD_ANALYSER_TO_PROPERTIES) {
            DV value;
            if (vp == EXTERNAL_IGNORE_MODIFICATIONS && evaluationContext.inConstruction()) {
                value = EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent();
            } else {
                value = fieldAnalysis.getFieldProperty(analyserContext, fieldInfo, fieldInfo.type.bestTypeInfo(), vp);
            }
            // IMPROVE we're not passing on 'our' analyserContext instead relying on that of the field, which does not know the lambda we're in at the moment
            result.put(vp, value);
        }
        return result;
    }

    @Override
    public int statementTime(Stage level) {
        return switch (level) {
            case INITIAL -> flowData.getInitialTime();
            case EVALUATION -> flowData.getTimeAfterEvaluation();
            case MERGE -> flowData.getTimeAfterSubBlocks();
        };
    }

    @Override
    public StatementAnalysis mostEnclosingLoop() {
        StatementAnalysis sa = this;
        while (sa != null) {
            if (sa.statement() instanceof LoopStatement) {
                return sa;//.localVariablesAssignedInThisLoop.isFrozen() && sa.localVariablesAssignedInThisLoop.contains(variableFqn);
            }
            sa = sa.parent();
        }
        throw new UnsupportedOperationException();
    }

    /**
     * We've encountered a break or continue statement, and need to find the corresponding loop...
     *
     * @param breakOrContinue null for a return statement
     * @return the loop and the number of steps up to compute the state; null if the parameter was null, and there is no loop
     */
    @Override
    public FindLoopResult findLoopByLabel(BreakOrContinueStatement breakOrContinue) {
        StatementAnalysis sa = this;
        int cnt = 0;
        while (sa != null) {
            if (sa.statement() instanceof LoopStatement loop &&
                    (breakOrContinue == null
                            || !breakOrContinue.hasALabel()
                            || loop.label != null && loop.label.equals(breakOrContinue.label))) {
                return new FindLoopResult(sa, cnt);
            }
            sa = sa.parent();
            cnt++;
        }
        if (breakOrContinue == null) return null;
        throw new UnsupportedOperationException();
    }

    /**
     * for reading, helper method; not for general use
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    @Override
    public VariableInfo findOrNull(@NotNull Variable variable, Stage level) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOrDefaultNull(fqn);
        if (vic == null) return null;
        return vic.best(level);
    }

    @Override
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
    @Override
    public VariableInfo findOrThrow(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOrDefaultNull(fqn);
        assert vic != null : "Have not yet evaluated " + variable.fullyQualifiedName();
        return vic.current();
    }

    @Override
    public boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        if (!variables.isSet(variableName)) return false;
        VariableInfoContainer vic = variables.get(variableName);
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) return false;
        VariableInfo variableInfo = vic.current();
        if (!variableInfo.variable().isLocal()) return false;

        String definedInBlock = vic.variableNature().definedInBlock();
        if (definedInBlock != null) {
            return parent != null && definedInBlock.equals(parent.index());
        }

        if (parent == null) return true;
        return !parent.variableIsSet(variableName);
    }

    /**
     * this method assumes that the variable already exists!
     *
     * @param variableName the variable's fully qualified name
     * @return the container
     */
    @Override
    public VariableInfoContainer findForWriting(@NotNull String variableName) {
        return variables.get(variableName);
    }

    /*
    will cause errors if variable does not exist yet!
    before you write, you'll have to ensureEvaluation
     */
    @Override
    public VariableInfoContainer findForWriting(@NotNull Variable variable) {
        return variables.get(variable.fullyQualifiedName());
    }

    @Override
    public Stream<VariableInfo> variableStream() {
        return variables.valueStream()
                .filter(VariableInfoContainer::isNotRemoved)
                .map(VariableInfoContainer::current);
    }

    @Override
    public Stream<Map.Entry<String, VariableInfoContainer>> rawVariableStream() {
        return variables.stream().filter(e -> e.getValue().isNotRemoved());
    }

    @Override
    public Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(Stage level) {
        return variables.stream()
                .filter(e -> e.getValue().isNotRemoved())
                .filter(e -> switch (level) {
                    case INITIAL -> !e.getValue().hasEvaluation() && !e.getValue().hasMerge();
                    case EVALUATION -> e.getValue().hasEvaluation();
                    case MERGE -> true;
                });
    }


    @Override
    public Expression notNullValuesAsExpression(EvaluationContext evaluationContext) {
        EvaluationResult context = EvaluationResult.from(evaluationContext);
        return And.and(context, variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference
                        && vi.isAssigned()
                        && index().equals(vi.getAssignmentIds().getLatestAssignmentIndex()))
                .map(vi -> {
                    if (vi.variable() instanceof FieldReference fieldReference) {
                        if (vi.getValue().isInstanceOf(NullConstant.class)) {
                            return new Pair<>(vi, DV.MIN_INT_DV);
                        }
                        VariableExpression ve = new VariableExpression(vi.getIdentifier(), fieldReference);
                        DV notNull = evaluationContext.getProperty(ve, NOT_NULL_EXPRESSION, false,
                                false);
                        return new Pair<>(vi, notNull);
                    }
                    return null;
                })
                .filter(e -> e != null && (e.v == DV.MIN_INT_DV || e.v.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)))
                .map(e -> {
                    VariableExpression ve = new VariableExpression(e.k.getIdentifier(), e.k.variable());
                    Expression equals = Equals.equals(context, ve, NullConstant.NULL_CONSTANT);
                    if (e.v.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                        return Negation.negate(context, equals);
                    }
                    return equals;
                })
                .toArray(Expression[]::new));
    }

    @Override
    public NavigationData<StatementAnalysis> navigationData() {
        return navigationData;
    }

    @Override
    public FlowData flowData() {
        return flowData;
    }

    @Override
    public MethodLevelData methodLevelData() {
        return methodLevelData;
    }

    public CausesOfDelay localVariablesInLoop() {
        if (localVariablesAssignedInThisLoop == null || localVariablesAssignedInThisLoop.isFrozen()) {
            return CausesOfDelay.EMPTY;
        }
        return DelayFactory.createDelay(location(INITIAL), CauseOfDelay.Cause.LOCAL_VARS_ASSIGNED); // DELAY
    }

    @Override
    public DV isReturnOrEscapeAlwaysExecutedInCurrentBlock(boolean escapeOnly) {
        if (!flowData().interruptsFlowIsSet()) {
            return flowData().interruptStatus().causesOfDelay();
        }
        InterruptsFlow bestAlways = flowData().bestAlwaysInterrupt();
        boolean escapes = !escapeOnly && bestAlways == InterruptsFlow.RETURN || bestAlways == InterruptsFlow.ESCAPE;
        return DV.fromBoolDv(escapes);
    }

    @Override
    public DV isEscapeAlwaysExecutedInCurrentBlock() {
        if (!flowData().interruptsFlowIsSet()) {
            return flowData().interruptStatus().causesOfDelay();
        }
        InterruptsFlow bestAlways = flowData().bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return DV.fromBoolDv(flowData().getGuaranteedToBeReachedInCurrentBlock().equals(FlowData.ALWAYS));
        }
        return DV.FALSE_DV;
    }

    @Override
    public Variable obtainLoopVar() {
        Structure structure = statement().getStructure();
        LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
        assert lvc.hasSingleDeclaration();
        return lvc.localVariableReference;
    }

    @Override
    public EvaluationResult evaluationOfForEachVariable(Variable loopVar,
                                                        Expression evaluatedIterable,
                                                        CausesOfDelay someValueWasDelayed,
                                                        EvaluationResult evaluationResult) {
        EvaluationContext evaluationContext = evaluationResult.evaluationContext();
        assert evaluationContext != null;

        VariableInfoContainer vic = findForWriting(loopVar);
        vic.ensureEvaluation(location(EVALUATION), new AssignmentIds(index() + EVALUATION), VariableInfoContainer.NOT_YET_READ,
                Set.of());
        ParameterizedType parameterizedType = loopVar.parameterizedType();

        DV nne = notNullOfLoopVariable(evaluationContext, evaluatedIterable, someValueWasDelayed);
        Properties valueProperties = evaluationContext.defaultValuePropertiesAllowMyself(parameterizedType, nne);

        CausesOfDelay delayed = valueProperties.delays();
        Expression value;
        if (evaluatedIterable.isDelayed() || delayed.isDelayed()) {
            CausesOfDelay causes = evaluatedIterable.isDelayed() ? evaluatedIterable.causesOfDelay() : delayed;
            value = DelayedVariableExpression.forLocalVariableInLoop(loopVar, causes);
        } else {
            value = Instance.forLoopVariable(evaluatedIterable.getIdentifier(), loopVar, valueProperties);
        }
        LinkedVariables linkedOfIterable = evaluatedIterable.linkedVariables(EvaluationResult.from(evaluationContext))
                .minimum(LinkedVariables.LINK_ASSIGNED);
        DV linkOfLoopVarInIterable = linkOfLoopVarInIterable(evaluationContext, parameterizedType,
                evaluatedIterable.returnType());
        LinkedVariables linked;
        if (linkOfLoopVarInIterable.isDelayed()) {
            linked = linkedOfIterable.changeToDelay(linkOfLoopVarInIterable.causesOfDelay());
        } else {
            DV immutableOfLoopVar = valueProperties.get(IMMUTABLE);
            linked = combineLinkOfLoopVarAndLinkedOfIterable(linkedOfIterable, linkOfLoopVarInIterable, immutableOfLoopVar);
        }
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationResult);
        builder.assignment(loopVar, value, linked);
        return builder.compose(evaluationResult).build();
    }

    private LinkedVariables combineLinkOfLoopVarAndLinkedOfIterable(LinkedVariables linkedOfIterable,
                                                                    DV linkOfLoopVarInIterable,
                                                                    DV immutableOfLoopVar) {
        assert linkOfLoopVarInIterable.isDone();

        if (LinkedVariables.LINK_INDEPENDENT.equals(linkOfLoopVarInIterable)) {
            return LinkedVariables.EMPTY;
        }
        if (LinkedVariables.LINK_DEPENDENT.equals(linkOfLoopVarInIterable)) {
            return linkedOfIterable.minimum(LinkedVariables.LINK_DEPENDENT);
        }

        // T to List<T> type-wise; t:list --> list:1 -> list:3;
        // t : someMethodExpression(list), with list:4 -> list:3
        if (LinkedVariables.LINK_IS_HC_OF.equals(linkOfLoopVarInIterable)) {
            return linkedOfIterable.changeAllToUnlessDelayed(LinkedVariables.LINK_IS_HC_OF);
        }
        // hidden content: how do the types relate? entry: map, the linkedOfIterable=map:2; Map.Entry is mutable -> keep :2
        // the type were immutable without hc
        assert LinkedVariables.LINK_COMMON_HC.equals(linkOfLoopVarInIterable);
        if (MultiLevel.isMutable(immutableOfLoopVar)) {
            return linkedOfIterable.minimum(LinkedVariables.LINK_DEPENDENT);
        }

        // so immutable with hidden content (because fully immutable would not result in common hc)
        return linkedOfIterable.minimum(LinkedVariables.LINK_COMMON_HC);
    }

    private DV linkOfLoopVarInIterable(EvaluationContext evaluationContext, ParameterizedType concreteType,
                                       ParameterizedType iterableType) {
        DV immutable = evaluationContext.getAnalyserContext().typeImmutable(concreteType);
        return LinkedVariables.fromImmutableToLinkedVariableLevel(immutable, evaluationContext.getAnalyserContext(),
                evaluationContext.getCurrentType(), concreteType, iterableType);
    }

    private static DV notNullOfLoopVariable(EvaluationContext evaluationContext, Expression value, CausesOfDelay delays) {
        if (delays.isDelayed()) {
            // we want to avoid a particular value on EVAL for the loop variable
            return delays;
        }
        DV nne = evaluationContext.getProperty(value, NOT_NULL_EXPRESSION, true, false);
        return nne.isDelayed() ? nne : MultiLevel.composeOneLevelLessNotNull(nne);
    }

    /*
    Initially triggered by the presence of the IN_NOT_NULL_CONTEXT flag, which marks the transition of CNN from NULLABLE
    to a higher value.

    Not directly in EvaluationResult.variableOccursInNotNullContext, because we could have ENN = 0 on a local field copy,
    and ENN = 1 on the field itself.
    this is only "leveled out" using the dependency graph of static assignments.

    The property CNN_TRAVELS_TO_PRECONDITION is computed in SAApply to decide whether an error should be raised, or not.
    if not, the restriction moves to the parameter or field.
     */
    @Override
    public void potentiallyRaiseErrorsOnNotNullInContext(AnalyserContext analyserContext,
                                                         Map<Variable, EvaluationResult.ChangeData> changeDataMap) {
        Set<Variable> alreadyRaised = new HashSet<>();
        changeDataMap.entrySet().stream()
                .filter(e -> e.getValue().getProperty(IN_NOT_NULL_CONTEXT).ge(EFFECTIVELY_NOT_NULL_DV))
                .filter(e -> !(e.getKey() instanceof LocalVariableReference lvr && lvr.variableNature() instanceof VariableNature.ScopeVariable))
                .sorted(Comparator.comparing(e -> -e.getValue().getProperty(IN_NOT_NULL_CONTEXT).value()))
                .forEach(e -> {
                    Variable variable = e.getKey();
                    EvaluationResult.ChangeData changeData = e.getValue();
                    DV inNotNullContext = changeData.getProperty(IN_NOT_NULL_CONTEXT);

                    VariableInfoContainer vic = findOrNull(variable);
                    VariableInfo previousOrInitial = vic.getPreviousOrInitial(); // IN_NNC was computed using the previous values!
                    VariableInfo eval = vic.best(EVALUATION);
                    DV cnnTravelsToPc = eval.getProperty(CNN_TRAVELS_TO_PRECONDITION);
                    if (previousOrInitial.valueIsSet() && !cnnTravelsToPc.valueIsTrue()) {
                        DV externalNotNull = previousOrInitial.getProperty(Property.EXTERNAL_NOT_NULL);
                        DV notNullExpression;
                        // a bit of an exception: the not-null of the pattern variable sits in the expression, and
                        // cannot be read from the initial value at the point of definition of a pattern variable
                        if (vic.variableNature() instanceof VariableNature.Pattern p && p.scope().equals(index)) {
                            notNullExpression = eval.getProperty(CONTEXT_NOT_NULL);
                        } else {
                            notNullExpression = previousOrInitial.getProperty(NOT_NULL_EXPRESSION);
                        }
                        DV max = externalNotNull.max(notNullExpression);
                        if (inNotNullContext.equals(EFFECTIVELY_CONTENT_NOT_NULL_DV)) {
                            if (max.lt(EFFECTIVELY_CONTENT_NOT_NULL_DV)) {
                                ensure(Message.newMessage(location(EVALUATION), Message.Label.POTENTIAL_CONTENT_NOT_NULL,
                                        "Variable: " + variable.simpleName()));
                                alreadyRaised.add(variable);
                            }
                        } else if (max.equals(MultiLevel.NULLABLE_DV)) {
                            Either<CausesOfDelay, Set<Variable>> linked = recursivelyLinkedToParameterOrField(analyserContext,
                                    variable, true);
                            if (linked.isRight() && Collections.disjoint(linked.getRight(), alreadyRaised)) {
                                ensure(Message.newMessage(location(EVALUATION), Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION,
                                        "Variable: " + variable.simpleName()));
                                alreadyRaised.add(variable);
                            }
                        }
                    }
                });
        changeDataMap.forEach((v, cd) -> {
            if (cd.getProperty(CANDIDATE_FOR_NULL_PTR_WARNING).valueIsTrue()) {
                ensureCandidateVariableForNullPtrWarning(v);
            }
        });
    }

    /*
    if(x == null) { ...} when we know that x will never be null (or the other way around) based
    on properties of x rather than the value of x
     */
    @Override
    public void potentiallyRaiseNullPointerWarningENN() {
        candidateVariablesForNullPtrWarningStream().forEach(variable -> {
            VariableInfo vi = findOrNull(variable, Stage.MERGE);
            DV cnn = vi.getProperty(CONTEXT_NOT_NULL); // after merge, CNN should still be too low
            if (cnn.equals(NULLABLE_DV)) {
                ensure(Message.newMessage(location(EVALUATION), Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN,
                        "Variable: " + variable.fullyQualifiedName()));
            }
        });
    }

    /*
    Apply the precondition coming from the evaluation result, which originates mainly from EvaluatePreconditionFromMethod.
    (there is a technical use in ExplicitConstructorInvocation code as well.)

    Null value means that there was no method call in the expression.

    Assert statements need special attention, because they may create additional precondition clauses
     */
    @Override
    public boolean applyPrecondition(Precondition precondition,
                                     EvaluationContext evaluationContext,
                                     ConditionManager localConditionManager) {
        stateData.setPreconditionFromMethodCalls(Objects.requireNonNullElseGet(precondition,
                () -> Precondition.empty(evaluationContext.getPrimitives())));
        if (statement instanceof AssertStatement || statement instanceof ThrowStatement) {
            // we'll not be writing the final precondition here, that's done in SASubBlocks
            // because there we write the local condition manager for the next statement
            return false;
        }
        boolean progress;
        Location location = location(EVALUATION);
        if (precondition != null) {
            Expression preconditionExpression = precondition.expression();
            if (preconditionExpression.isBoolValueFalse()) {
                ensure(Message.newMessage(location, Message.Label.INCOMPATIBLE_PRECONDITION));
                progress = stateData.setPreconditionAllowEquals(Precondition.empty(primitives));
            } else {
                if (preconditionExpression.isDelayed()) {
                    LOGGER.debug("Apply of {}, {} is delayed because of precondition",
                            index(), methodAnalysis.getMethodInfo().fullyQualifiedName);
                    stateData.setPrecondition(new Precondition(preconditionExpression, precondition.causes()));
                    return false;
                }
                Expression translated;
                if (precondition.singleCompanionCauseOrNull() != null) {
                    translated = precondition.expression(); // keep as is
                } else {
                    Identifier id = precondition.expression().getIdentifier();
                    translated = evaluationContext.acceptAndTranslatePrecondition(id, precondition.expression());
                }
                if (translated != null) {
                    Precondition pc;
                    if (translated instanceof MultiExpressions) {
                        LOGGER.debug("Ignoring precondition that is too complex in {}", index);
                        pc = Precondition.empty(primitives); // too complex
                    } else {
                        pc = new Precondition(translated, precondition.causes());
                    }
                    progress = stateData.setPrecondition(pc);
                    EvaluationResult context = EvaluationResult.from(evaluationContext);
                    Expression result = localConditionManager.evaluate(context, translated, false);
                    if (result.isBoolValueFalse()) {
                        ensure(Message.newMessage(location, Message.Label.INCOMPATIBLE_PRECONDITION));
                    }
                } else {
                    progress = stateData.setPrecondition(Precondition.empty(primitives));
                }
            }
        } else if (!stateData().preconditionIsFinal()) {
            // undo a potential previous delay, so that no precondition is seen to be present
            progress = stateData.setPrecondition(Precondition.noInformationYet(location, primitives));
        } else {
            progress = false;
        }
        return progress;
    }

    /*
    As the first action in 'apply', we need to ensure that all variables exist, and have a proper assignmentId and readId.

    We need to do:
    - generally ensure a EVALUATION level for each variable occurring, with correct assignmentId, readId
    - create fields + local copies of variable fields, because they don't exist in the first iteration
    - link the fields to their local copies (or at least, compute these links)

    Local variables, This, Parameters will already exist, minimally in INITIAL level
    Fields (and forms of This (super...)) will not exist in the first iteration; they need creating
    */
    @Override
    public void ensureVariable(EvaluationContext evaluationContext,
                               Variable variable,
                               EvaluationResult.ChangeData changeData,
                               int newStatementTime) {
        VariableInfoContainer vic;
        VariableNature vn;
        if (!variableIsSet(variable.fullyQualifiedName())) {
            vn = variable.variableNature();
            assert vn instanceof VariableNature.NormalLocalVariable || vn instanceof VariableNature.ScopeVariable :
                    "Encountering variable " + variable.fullyQualifiedName() + " of nature " + vn;
            // a dependent variable should have a scope restricted by the least of the two parts
            VariableNature normal = VariableNature.normal(variable, index());
            VariableNature newVn =
                    variable instanceof DependentVariable dv ? computeVariableNature(dv, normal) :
                            vn instanceof VariableNature.NormalLocalVariable
                                    ? normal : vn;
            vic = createVariable(evaluationContext, variable, flowData().getInitialTime(), newVn);
        } else {
            vic = getVariable(variable.fullyQualifiedName());
            vn = vic.variableNature();
        }
        String id = index() + EVALUATION;
        VariableInfo initial = vic.getPreviousOrInitial();
        AssignmentIds assignmentIds = changeData.markAssignment() ? new AssignmentIds(id) : initial.getAssignmentIds();
        // we do not set readId to the empty set when markAssignment... we'd rather keep the old value
        // we will compare the recency anyway

        String readId = changeData.readAtStatementTime().isEmpty() && !(vn instanceof VariableNature.ScopeVariable)
                ? initial.getReadId() : id;

        vic.ensureEvaluation(location(EVALUATION), assignmentIds, readId, changeData.readAtStatementTime());
        if (evaluationContext.isMyself(variable)) {
            vic.setProperty(CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV, EVALUATION);
        }
    }

    private VariableNature computeVariableNature(DependentVariable dv, VariableNature normal) {
        VariableInfoContainer arrayVic = variables.getOrDefaultNull(dv.arrayVariable().fullyQualifiedName());
        if (arrayVic == null) return normal;
        if (dv.indexVariable() != null) {
            VariableInfoContainer scopeVic = variables.getOrDefaultNull(dv.indexVariable().fullyQualifiedName());
            if (scopeVic != null) {
                String arrayBlock = arrayVic.variableNature().getStatementIndexOfBlockVariable();
                String indexBlock = scopeVic.variableNature().getStatementIndexOfBlockVariable();
                if (indexBlock != null && (arrayBlock == null || indexBlock.startsWith(arrayBlock))) {
                    return scopeVic.variableNature();
                }
            }
        }
        return arrayVic.variableNature();
    }


    /*
     we keep track which local variables are assigned in a loop

     add this variable name to all parent loop statements until definition of the local variable
     in this way, a version can be introduced to be used before this assignment.

     at the same time, a new local copy has to be created in this statement to be used after the assignment
     */

    @Override
    public boolean addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName) {
        StatementAnalysis sa = this;
        String loopIndex = null;
        boolean progress = false;
        while (sa != null) {
            if (!sa.variableIsSet(fullyQualifiedName)) return false;
            VariableInfoContainer localVic = sa.getVariable(fullyQualifiedName);
            if (!(localVic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop)) return false;
            if (sa.statement() instanceof LoopStatement) {
                progress = ((StatementAnalysisImpl) sa).ensureLocalVariableAssignedInThisLoop(fullyQualifiedName);
                loopIndex = sa.index();
                break; // we've found the loop
            }
            sa = sa.parent();
        }
        assert loopIndex != null;
        return progress;
    }

    /*
    we protect
     */
    @Override
    public void setVariableAccessReportOfSubAnalysers(VariableAccessReport variableAccessReport) {
        for (Map.Entry<Variable, Properties> e : variableAccessReport.propertiesMap().entrySet()) {
            Variable variable = e.getKey();
            DV read = e.getValue().getOrDefaultNull(READ);
            DV modified = e.getValue().getOrDefaultNull(CONTEXT_MODIFIED);
            DV notNull = e.getValue().getOrDefaultNull(CONTEXT_NOT_NULL);
            boolean haveMapForVariable = read != null || modified != null || notNull != null;
            if (haveMapForVariable) {
                Properties p = propertiesFromSubAnalysers.computeIfAbsent(variable, v -> Properties.writable());
                try {
                    if (read != null) {
                        p.put(READ, read);
                    }
                    if (modified != null) {
                        p.put(CONTEXT_MODIFIED, modified);
                    }
                    if (notNull != null) {
                        p.put(CONTEXT_NOT_NULL, notNull);
                    }
                } catch (IllegalArgumentException ise) {
                    LOGGER.error("Caught property change violation for variable {}", variable);
                    throw ise;
                }
            }
        }
    }

    @Override
    public List<Variable> variablesReadBySubAnalysers() {
        return this.propertiesFromSubAnalysers.entrySet().stream()
                .filter(e -> e.getValue().getOrDefault(READ, DV.FALSE_DV).valueIsTrue())
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public Stream<Map.Entry<Variable, Properties>> propertiesFromSubAnalysers() {
        return this.propertiesFromSubAnalysers.entrySet().stream();
    }

    @Override
    public boolean havePropertiesFromSubAnalysers() {
        return !this.propertiesFromSubAnalysers.isEmpty();
    }

    // IMPORTANT: Singleton_2 runs green when we only look at the previous delay
    // it halts if we cannot progress if we encounter any of the earlier delays (so don't keep a set of delays)
    @Override
    public boolean latestDelay(CausesOfDelay delay) {
        int count = applyCausesOfDelay.merge(delay, 1, Integer::sum);
        if (count > 10) {
            LOGGER.error("Delay map:");
            applyCausesOfDelay.forEach((k, v) -> LOGGER.error("{}: {}", k, v));
            //  throw new NoProgressException("Interrupting at statement " + index + " in method " + methodAnalysis.getMethodInfo().fullyQualifiedName);
        }
        // new d
        return count <= 2;
    }

    @Override
    public boolean inLoop() {
        if (statement instanceof LoopStatement) return true;
        if (parent != null) {
            return parent.inLoop();
        }
        return false;
    }

    @Override
    public boolean isStillReachable(String target) {
        StatementAnalysisImpl current = this;
        while (true) {
            if (current.flowData.isUnreachable()) return false;
            if (target.equals(current.index)) return true; // we're there!
            if (target.startsWith(current.index)) {
                int n = current.index.length();
                int blockIndex = Integer.parseInt(target.substring(n + 1, target.indexOf('.', n + 1)));
                current = (StatementAnalysisImpl) current.navigationData.blocks.get().get(blockIndex)
                        .orElseThrow(() -> new UnsupportedOperationException("Looking for " + target + ", block " + blockIndex));
            } else if (current.index.compareTo(target) < 0 && current.navigationData.next.get().isPresent()) {
                current = (StatementAnalysisImpl) current.navigationData.next.get().get();
            }
        }
    }

    @Override
    public DependentVariable searchInEquivalenceGroupForLatestAssignment(EvaluationContext evaluationContext,
                                                                         DependentVariable dependentVariable,
                                                                         Expression arrayValue,
                                                                         Expression indexValue,
                                                                         ForwardEvaluationInfo forwardEvaluationInfo) {
        List<VariableInfo> inEquivalenceGroup = variableStream().filter(vi -> {
            Variable v = vi.variable();
            if (v.equals(dependentVariable)) return true;
            if (v instanceof DependentVariable dv && !forwardEvaluationInfo.getEvaluating().contains(v)) {
                boolean acceptArrayValue;
                if (dv.arrayVariable() instanceof LocalVariableReference lvr && lvr.variableNature() instanceof VariableNature.ScopeVariable) {
                    // the idea is to evaluate the expression, but we have to be careful to avoid recursions
                    ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().addEvaluating(dv).build();
                    EvaluationResult er = dv.arrayExpression().evaluate(EvaluationResult.from(evaluationContext), fwd);
                    acceptArrayValue = arrayValue.equals(er.value());
                } else if (dv.arrayVariable().equals(dependentVariable.arrayVariable())) {
                    acceptArrayValue = true;
                } else {
                    VariableInfo variableInfo = getVariable(dv.arrayVariable().fullyQualifiedName()).best(INITIAL);
                    acceptArrayValue = variableInfo.getValue().equals(arrayValue);
                }
                if (acceptArrayValue) {
                    if (dv.indexVariable() instanceof LocalVariableReference lvr && lvr.variableNature() instanceof VariableNature.ScopeVariable) {
                        // IMPROVE not really sure, need to dive into this subject again
                        return dv.indexExpression().equals(indexValue);
                    }
                    if (dv.indexVariable() == null) {
                        return dv.indexExpression().equals(indexValue);
                    }
                    if (dv.indexVariable().equals(dependentVariable.indexVariable())) {
                        return true;
                    }
                    VariableInfo variableInfo = getVariable(dv.indexVariable().fullyQualifiedName()).best(INITIAL);
                    return variableInfo.getValue().equals(indexValue);
                }
            }
            return false;
        }).sorted(Comparator.comparing(StatementAnalysisImpl::key)).toList();
        return inEquivalenceGroup.isEmpty() ? dependentVariable : (DependentVariable) inEquivalenceGroup.get(0).variable();
    }

    private static String key(VariableInfo vi) {
        return (vi.getAssignmentIds().hasNotYetBeenAssigned() ? "~" : vi.getAssignmentIds().getLatestAssignment()) + "|" + vi.variable().fullyQualifiedName();
    }

    @Override
    public Either<CausesOfDelay, Set<Variable>> recursivelyLinkedToParameterOrField(AnalyserContext analyserContext,
                                                                                    Variable v,
                                                                                    boolean cnnTravelsToFields) {
        if (v instanceof ParameterInfo
                || cnnTravelsToFields && v instanceof FieldReference) return Either.right(Set.of(v));
        VariableInfoContainer vic = findOrNull(v);
        if (vic == null) {
            return Either.right(Set.of());
        }
        if (vic.best(Stage.EVALUATION).getProperty(Property.CNN_TRAVELS_TO_PRECONDITION).valueIsTrue())
            return Either.right(Set.of(v));
        if (v instanceof DependentVariable dv) {
            return recursivelyLinkedToParameterOrField(analyserContext, dv.arrayVariable(), cnnTravelsToFields);
        }
        VariableNature vn = vic.variableNature();
        while (vn instanceof VariableNature.VariableDefinedOutsideLoop outside) {
            vn = outside.previousVariableNature();
        }
        if (vn instanceof VariableNature.LoopVariable lv) {
            Expression e = lv.statementAnalysis().statement().getStructure().expression();
            Either<CausesOfDelay, Set<Variable>> either = e.loopSourceVariables(analyserContext, v.parameterizedType());
            if (either.isLeft()) return either;
            CausesOfDelay causes = CausesOfDelay.EMPTY;
            Set<Variable> vars = new HashSet<>();
            for (Variable vv : either.getRight()) {
                Either<CausesOfDelay, Set<Variable>> recursive =
                        recursivelyLinkedToParameterOrField(analyserContext, vv, cnnTravelsToFields);
                if (recursive.isLeft()) {
                    causes = causes.merge(recursive.getLeft());
                } else {
                    vars.addAll(recursive.getRight());
                }
            }
            if (causes.isDelayed()) return Either.left(causes);
            return Either.right(vars);
        }
        return Either.right(Set.of());
    }

    @Override
    public boolean isBrokeDelay() {
        return brokeDelay.isSet();
    }

    @Override
    public void setBrokeDelay() {
        if (!brokeDelay.isSet()) brokeDelay.set();
    }
}
