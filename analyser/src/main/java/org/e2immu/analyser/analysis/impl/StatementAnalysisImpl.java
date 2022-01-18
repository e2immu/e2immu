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
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.nonanalyserimpl.Merge;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoContainerImpl;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.LocationImpl;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.*;
import static org.e2immu.analyser.model.MultiLevel.MUTABLE_DV;
import static org.e2immu.analyser.model.MultiLevel.NOT_INVOLVED_DV;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container
public class StatementAnalysisImpl extends AbstractAnalysisBuilder implements StatementAnalysis, LimitedStatementAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalysisImpl.class);

    public final Statement statement;
    public final String index;
    public final StatementAnalysis parent;
    public final boolean inSyncBlock;
    public final MethodAnalysis methodAnalysis;
    public final Location location;

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

    public StatementAnalysisImpl(Primitives primitives,
                                 MethodAnalysis methodAnalysis,
                                 Statement statement,
                                 StatementAnalysis parent,
                                 String index,
                                 boolean inSyncBlock) {
        super(primitives, index);
        this.index = super.simpleName;
        this.statement = statement;
        this.parent = parent;
        this.inSyncBlock = inSyncBlock;
        this.methodAnalysis = Objects.requireNonNull(methodAnalysis);
        localVariablesAssignedInThisLoop = statement instanceof LoopStatement ? new AddOnceSet<>() : null;
        stateData = new StateData(statement instanceof LoopStatement);
        location = new LocationImpl(methodAnalysis.getMethodInfo(), index, statement.getIdentifier());
        flowData = new FlowData(location);
        rangeData = statement instanceof LoopStatement ? new RangeDataImpl(location) : null;
    }

    static StatementAnalysis recursivelyCreateAnalysisObjects(
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
        if (flowData.isUnreachable()) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        StatementAnalysis replaced = followReplacements();
        return replaced.navigationData().next.get().map(statementAnalysis -> {
            if (statementAnalysis.flowData().isUnreachable()) {
                return replaced;
            }
            return statementAnalysis.lastStatement();
        }).orElse(replaced);
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
        return variables.stream()
                .map(e -> e.getValue().current())
                .filter(v -> v.variable() instanceof FieldReference fieldReference
                        && fieldReference.fieldInfo == fieldInfo);
    }

    @Override
    public List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        return streamOfLatestInfoOfVariablesReferringTo(fieldInfo).toList();
    }

    // next to this.field, and local copies, we also have fields with a non-this scope.
    // all values that contain the field itself get blocked;

    @Override
    public List<VariableInfo> assignmentInfo(FieldInfo fieldInfo) {
        List<VariableInfo> normalValue = latestInfoOfVariablesReferringTo(fieldInfo);
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

        FieldReference fieldReference = new FieldReference(InspectionProvider.defaultFrom(primitives), fieldInfo);

        DelayedVariableExpression dve;
        boolean viThisHasDelayedValue = (dve = viThis.getValue().asInstanceOf(DelayedVariableExpression.class)) != null &&
                dve.variable() instanceof FieldReference fr && fr.fieldInfo == fieldInfo;
        boolean partialAssignment = viThis.getValue().variables(true).contains(fieldReference);
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
                    VariableInfoContainer assignmentVic = statementAnalysis.getVariable(fieldInfo.fullyQualifiedName());
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

    @Override
    public VariableInfoContainer getVariable(String fullyQualifiedName) {
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

    private boolean prefixAdded(String assignmentIndex, Set<String> added) {
        return added.stream().anyMatch(assignmentIndex::startsWith);
    }

    private boolean acceptForConditionalInitialization(FieldInfo fieldInfo, Expression value) {
        List<Variable> variables = value.variables(true);
        return variables.stream().noneMatch(v -> v instanceof FieldReference fr && fieldInfo.equals(fr.fieldInfo));
    }

    @Override
    public boolean containsMessage(Message.Label messageLabel) {
        return localMessageStream().anyMatch(message -> message.message() == messageLabel &&
                message.location().equals(location()));
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
    public Location location() {
        return location;
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
                createParametersThisAndVariablesFromClosure(evaluationContext, currentMethod);
                return;
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.rawVariableStream()
                // never copy a return variable from the parent
                .filter(e -> previous != null || !(e.getValue().current().variable() instanceof ReturnVariable))
                .forEach(e -> copyVariableFromPreviousInIteration0(e, copyFrom,
                        previous == null, previous == null ? null : previous.index(), false));

        flowData.initialiseAssignmentIds(copyFrom.flowData());
    }

    private void createParametersThisAndVariablesFromClosure(EvaluationContext evaluationContext, MethodInfo currentMethod) {
        // at the beginning of a method, we create parameters; also those from closures
        assert evaluationContext != null;
        EvaluationContext closure = evaluationContext;
        boolean inClosure = false;
        while (closure != null) {
            if (closure.getCurrentMethod() != null) {
                for (ParameterInfo parameterInfo : closure.getCurrentMethod().getMethodInspection().getParameters()) {
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
                    copyVariableFromPreviousInIteration0(e, closure4Local.getCurrentStatement().getStatementAnalysis(),
                            true, null, true));
        }
    }

    private void copyVariableFromPreviousInIteration0(Map.Entry<String, VariableInfoContainer> entry,
                                                      StatementAnalysis copyFrom,
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
        } else if (doNotCopyToNextStatement(copyFrom, vic, variable, previousIsParent, indexOfPrevious)) {
            return; // skip; note: order is important, this check has to come before the next one (e.g., Var_2)
        } else if (conditionsToMoveVariableInsideLoop(variable, vic)) {
            // move a local variable, not defined in this loop, inside the loop
            // the result is a va
            newVic = VariableInfoContainerImpl.existingLocalVariableIntoLoop(vic, index, previousIsParent);
        } else {
            // make a simple reference copy; potentially resetting localVariableInLoopDefinedOutside
            newVic = VariableInfoContainerImpl.existingVariable(vic, index, previousIsParent, navigationData.hasSubBlocks());
        }
        putVariable(fqn, newVic);
    }

    private boolean doNotCopyToNextStatement(StatementAnalysis copyFrom,
                                             VariableInfoContainer vic,
                                             Variable variable,
                                             boolean previousIsParent,
                                             String indexOfPrevious) {
        if (vic.variableNature().doNotCopyToNextStatement(previousIsParent, indexOfPrevious, index)) return true;
        IsVariableExpression ive;
        if (variable instanceof FieldReference fr
                && fr.scope != null
                && ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null)) {
            // recursively check!
            VariableInfoContainer scopeVic = copyFrom.getVariable(ive.variable().fullyQualifiedName());
            return doNotCopyToNextStatement(copyFrom, scopeVic, ive.variable(), previousIsParent, indexOfPrevious);
        }
        return false;
    }

    private boolean conditionsToMoveVariableInsideLoop(Variable variable,
                                                       VariableInfoContainer vic) {
        if (!(statement instanceof LoopStatement)) return false; // we must move inside a loop
        return variable.isLocal() && !(vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
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
        if (copyIsParent) {
            // see variableEntryStream(EVALUATION) -> ignore those that have merges but no eval; see e.g. Basics_7
            return !vic.hasMerge() || vic.hasEvaluation();
            // we'd only copy fields if they are used somewhere in the block. BUT there are "hidden" fields
            // such as local variables with an array initialiser containing fields as a value; conclusion: copy all, but don't merge unless used.
        }
        // don't continue loop and resource variables beyond the loop
        // FIXME: also don't copy fields on such variables!
        if (copyFrom.index().equals(vic.variableNature().getStatementIndexOfBlockVariable())) return false;
        Variable variable = vic.current().variable();
        IsVariableExpression ive;
        if (variable instanceof FieldReference fr && fr.scope != null && ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null)) {
            VariableInfoContainer scopeVic = copyFrom.getVariable(ive.variable().fullyQualifiedName());
            return explicitlyPropagate(copyFrom, false, scopeVic);
        }
        return true;
    }


    /*
    Do not add IMMUTABLE to this set! (computed from external, formal, context)
     */
    public static final Set<Property> FROM_PARAMETER_ANALYSER_TO_PROPERTIES
            = Set.of(IDENTITY, EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE, CONTAINER);

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
                    for (Property property : FROM_PARAMETER_ANALYSER_TO_PROPERTIES) {
                        DV value = parameterAnalysis.getProperty(property);
                        // we have given a value in the first iteration for e.g. @Container
                        // we'll not get back to that (will happen in EVAL rather than here)
                        // hence the 'true' to ensure that we don't cause exceptions
                        vic.setProperty(property, value, true, INITIAL);
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
            DV formalImmutable = analyserContext.defaultImmutable(variable.parameterizedType(), false);
            vic.setProperty(IMMUTABLE, formalImmutable, INITIAL);
        }
        // update @Independent
        TypeInfo bestType = variable.parameterizedType().bestTypeInfo();
        if (bestType != null) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
            DV currentIndependent = vi.getProperty(Property.INDEPENDENT);
            if (currentIndependent.isDelayed()) {
                DV independent = typeAnalysis.getProperty(Property.INDEPENDENT);
                vic.setProperty(Property.INDEPENDENT, independent, INITIAL);
            }
        }
    }

    private void fromFieldAnalyserIntoInitial(EvaluationContext evaluationContext,
                                              VariableInfoContainer vic,
                                              FieldReference fieldReference) {
        VariableInfo viInitial = vic.best(INITIAL);

        Properties map = fieldPropertyMap(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo);
        Expression initialValue;
        FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);

        if (!viInitial.valueIsSet()) {
            // we don't have an initial value yet; the initial field value is only visible in constructors
            // and then only to direct references (this.field)
            if (methodAnalysis.getMethodInfo().isConstructor && fieldReference.scopeIsThis()) {
                initialValue = fieldAnalysis.getInitializerValue();
            } else {
                initialValue = fieldAnalysis.getValueForStatementAnalyser(fieldReference);
            }
            Properties valueMap = evaluationContext.getValueProperties(viInitial.variable().parameterizedType(), initialValue);
            valueMap.stream().forEach(e -> map.merge(e.getKey(), e.getValue(), DV::max));

            // copy into initial

            vic.setValue(initialValue, LinkedVariables.EMPTY, map, true);
        } else {
            // only set properties copied from the field
            map.stream().forEach(e -> vic.setProperty(e.getKey(), e.getValue(), INITIAL));
            initialValue = viInitial.getValue();
            assert initialValue.isDone();
            // add the value properties from the current value to combined (do not set to initial!!)
            Properties valueMap = evaluationContext.getValueProperties(viInitial.variable().parameterizedType(), viInitial.getValue());
            valueMap.stream().forEach(e -> map.merge(e.getKey(), e.getValue(), DV::max));
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
                map.removeAll(GroupPropertyValues.PROPERTIES);
                vic.setValue(initialValue, viInitial.getLinkedVariables(), map, false);
            }
        }
    }

    private static final Set<Property> FROM_FIELD_ANALYSER_TO_PROPERTIES = Set.of(EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE);

    @Override
    public void ensureMessages(Stream<Message> messageStream) {
        messageStream.forEach(this::ensure);
    }

    /*
    output the statement, but take into account the list of variables, there may be name clashes to be resolved

     */
    public OutputBuilder output(Qualification qualification) {
        return statement.output(qualification, this);
    }

    @Override
    public boolean assignsToFields() {
        return variableStream().anyMatch(vi -> vi.variable() instanceof FieldReference && vi.isAssigned());
    }

    @Override
    public boolean noIncompatiblePrecondition() {
        return !(methodLevelData.combinedPrecondition.isFinal()
                && methodLevelData.combinedPrecondition.get().expression().isBoolValueFalse());
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

    public void ensureLocalVariableAssignedInThisLoop(String name) {
        if (!(localVariablesAssignedInThisLoop.isFrozen()) &&
                !localVariablesAssignedInThisLoop.contains(name)) {
            localVariablesAssignedInThisLoop.add(name);
        }
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

    private record PrepareMerge(List<VariableInfoContainer> toMerge,
                                List<VariableInfoContainer> toIgnore) {
        public PrepareMerge() {
            this(new LinkedList<>(), new LinkedList<>());
        }
    }

    private PrepareMerge mergeActions(List<ConditionAndLastStatement> lastStatements) {
        PrepareMerge pm = new PrepareMerge();
        // some variables will be picked up in the sub-blocks, others in the current block, others in both
        // we want to see them only once, though
        Set<Variable> seen = new HashSet<>();
        mergeAction(pm, seen, variables.stream().map(Map.Entry::getValue), vn -> vn.acceptVariableForMerging(index));
        Stream<VariableInfoContainer> stream = lastStatements.stream()
                .flatMap(st -> st.lastStatement().getStatementAnalysis().rawVariableStream().map(Map.Entry::getValue));
        mergeAction(pm, seen, stream, vn -> vn.acceptForSubBlockMerging(index));
        return pm;
    }

    private void mergeAction(PrepareMerge prepareMerge,
                             Set<Variable> seen,
                             Stream<VariableInfoContainer> stream,
                             Predicate<VariableNature> predicate) {
        stream.forEach(vic -> {
            Variable variable = vic.current().variable();
            if (seen.add(variable)) {
                if (predicate.test(vic.variableNature())) {
                    prepareMerge.toMerge.add(vic);
                } else {
                    prepareMerge.toIgnore.add(vic);
                }
            }
        });
    }

    public record ConditionAndLastStatement(Expression condition,
                                            String firstStatementIndexForOldStyleSwitch,
                                            StatementAnalyser lastStatement,
                                            boolean alwaysEscapes) {
    }

    /**
     * From child blocks into the parent block; determine the value and properties for the current statement
     *
     * @param evaluationContext       for expression evaluations
     * @param lastStatements          the last statement of each of the blocks
     * @param atLeastOneBlockExecuted true if we can (potentially) discard the current value
     * @param statementTime           the statement time of subBlocks
     */
    public AnalysisStatus mergeVariablesFromSubBlocks(EvaluationContext evaluationContext,
                                                      Expression stateOfConditionManagerBeforeExecution,
                                                      Expression postProcessState,
                                                      List<ConditionAndLastStatement> lastStatements,
                                                      boolean atLeastOneBlockExecuted,
                                                      int statementTime) {

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        // we copy to a list, because we may be adding variables (ConditionalInitialization copies)
        PrepareMerge prepareMerge = mergeActions(lastStatements);
        Map<Variable, LinkedVariables> linkedVariablesMap = new HashMap<>();
        Set<Variable> variablesWhereMergeOverwrites = new HashSet<>();

        for (VariableInfoContainer vic : prepareMerge.toMerge) {
            VariableInfo current = vic.current();
            Variable variable = current.variable();
            String fqn = variable.fullyQualifiedName();

            // the variable stream comes from multiple blocks; we ensure that merging takes place once only
            VariableInfoContainer destination;
            if (!variables.isSet(fqn)) {
                VariableNature nature = vic.variableNature();
                destination = createVariable(evaluationContext, variable, statementTime, nature);
            } else {
                destination = vic;
            }
            boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;

            Merge.ExpressionAndProperties overwriteValue = overwrite(evaluationContext, variable);
            List<ConditionAndVariableInfo> toMerge = filterSubBlocks(evaluationContext, lastStatements, variable,
                    fqn, inSwitchStatementOldStyle);
            if (toMerge.size() > 0) {
                try {
                    Merge merge = new Merge(evaluationContext, destination);
                    merge.merge(stateOfConditionManagerBeforeExecution, postProcessState, overwriteValue,
                            atLeastOneBlockExecuted, toMerge, groupPropertyValues);

                    LinkedVariables linkedVariables = toMerge.stream().map(cav -> cav.variableInfo().getLinkedVariables())
                            .reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
                    linkedVariablesMap.put(variable, linkedVariables);

                    if (atLeastOneBlockExecuted) variablesWhereMergeOverwrites.add(variable);

                    if (atLeastOneBlockExecuted &&
                            checkForOverwritingPreviousAssignment(variable, current, vic.variableNature(), toMerge)) {
                        ensure(Message.newMessage(new LocationImpl(methodAnalysis.getMethodInfo(), index,
                                        statement.getIdentifier()),
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
                linkedVariablesMap.put(variable, destination.best(MERGE).getLinkedVariables());
            } else {
                for (Property property : GroupPropertyValues.PROPERTIES) {
                    groupPropertyValues.set(property, variable, current.getProperty(property));
                }
            }

            // CNN_FOR_PARENT overwrite; see Basics_14, from throws statement back to condition
            contextNotNullForParent(lastStatements, groupPropertyValues, fqn);
        }

        for (VariableInfoContainer vic : prepareMerge.toIgnore) {
            VariableInfo current = vic.current();
            for (Property property : GroupPropertyValues.PROPERTIES) {
                groupPropertyValues.set(property, current.variable(), current.getProperty(property));
            }
        }
        return linkingAndGroupProperties(evaluationContext, groupPropertyValues, linkedVariablesMap,
                variablesWhereMergeOverwrites);
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

    private List<ConditionAndVariableInfo> filterSubBlocks(EvaluationContext evaluationContext, List<ConditionAndLastStatement> lastStatements, Variable variable, String fqn, boolean inSwitchStatementOldStyle) {
        return lastStatements.stream()
                .filter(e2 -> e2.lastStatement().getStatementAnalysis().variableIsSet(fqn))
                .map(e2 -> {
                    VariableInfoContainer vic2 = e2.lastStatement().getStatementAnalysis().getVariable(fqn);
                    return new ConditionAndVariableInfo(e2.condition(),
                            vic2.current(), e2.alwaysEscapes(),
                            vic2.variableNature(), e2.firstStatementIndexForOldStyleSwitch(),
                            e2.lastStatement().getStatementAnalysis().index(),
                            index,
                            e2.lastStatement().getStatementAnalysis(),
                            variable,
                            evaluationContext);
                })
                .filter(cav -> acceptVariableForMerging(cav, inSwitchStatementOldStyle)).toList();
    }

    private void contextNotNullForParent(List<ConditionAndLastStatement> lastStatements, GroupPropertyValues groupPropertyValues, String fqn) {
        lastStatements.stream().filter(cal -> cal.lastStatement().getStatementAnalysis().variableIsSet(fqn)).forEach(cal -> {
            VariableInfoContainer calVic = cal.lastStatement().getStatementAnalysis().getVariable(fqn);
            VariableInfo calVi = calVic.best(EVALUATION);
            DV cnn4Parent = calVi.getProperty(CONTEXT_NOT_NULL_FOR_PARENT, null);
            if (cnn4Parent != null) {
                // we copy, delayed or not!
                groupPropertyValues.set(Property.CONTEXT_NOT_NULL, calVi.variable(), cnn4Parent);
            }
        });
    }

    private AnalysisStatus linkingAndGroupProperties(EvaluationContext evaluationContext,
                                                     GroupPropertyValues groupPropertyValues,
                                                     Map<Variable, LinkedVariables> linkedVariablesMap,
                                                     Set<Variable> variablesWhereMergeOverwrites) {
        // then, per cluster of variables
        Function<Variable, LinkedVariables> linkedVariablesFromBlocks =
                v -> linkedVariablesMap.getOrDefault(v, LinkedVariables.EMPTY);
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(this, MERGE,
                (vic, v) -> !linkedVariablesMap.containsKey(v),
                variablesWhereMergeOverwrites,
                linkedVariablesFromBlocks, evaluationContext);
        computeLinkedVariables.writeLinkedVariables(this::isLoopVariableWillDisappearInNextStatement);

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

        return AnalysisStatus.of(ennStatus.merge(cnnStatus).merge(cmStatus).merge(extImmStatus).merge(cImmStatus));
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

    private boolean isLoopVariableWillDisappearInNextStatement(VariableInfoContainer vic) {
        if (vic == null) return true;
        if (vic.variableNature() instanceof VariableNature.LoopVariable lv) {
            return index.equals(lv.statementIndex());
        }
        if (vic.variableNature() instanceof VariableNature.Pattern pattern) {
            return index.equals(pattern.parentBlockIndex());
        }
        return false;
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
                        // we'll need to double check that there was no reading before the assignment!
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
                                                VariableNature variableInLoop) {
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) {
            throw new UnsupportedOperationException("Already exists: " +
                    fqn + " in " + index + ", " + methodAnalysis.getMethodInfo().fullyQualifiedName);
        }

        VariableInfoContainer vic = VariableInfoContainerImpl.newVariable(location(), variable,
                variableInLoop, navigationData.hasSubBlocks());
        putVariable(variable.fullyQualifiedName(), vic);

        // linked variables travel from the parameters via the statements to the fields
        if (variable instanceof ReturnVariable returnVariable) {
            initializeReturnVariable(vic, returnVariable);

        } else if (variable instanceof This thisVar) {
            initializeThis(vic, evaluationContext.getAnalyserContext(), thisVar);

        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            initializeParameter(vic, evaluationContext, parameterInfo);

        } else if (variable instanceof FieldReference fieldReference) {
            initializeFieldReference(vic, evaluationContext, fieldReference);

        } else if (variable instanceof LocalVariableReference || variable instanceof DependentVariable) {
            // nothing spectacular; everything handled at the place of creation
            if (variableInLoop instanceof VariableNature.LoopVariable) {
                initializeLoopVariable(vic, variable, evaluationContext.getAnalyserContext());
            } else {
                initializeLocalOrDependentVariable(vic, variable);
            }
        } else {
            throw new UnsupportedOperationException("? initialize variable of type " + variable.getClass());
        }
        return vic;
    }

    private void initializeLoopVariable(VariableInfoContainer vic, Variable variable, AnalyserContext analyserContext) {
        // but, because we don't evaluate the assignment, we need to assign some value to the loop variable
        // otherwise we'll get delays
        // especially in the case of forEach, the lvc.expression is empty (e.g., 'String s') anyway
        // an assignment may be difficult.
        // FIXME if the loop variable is of enclosed or own type, we'll get delays
        // we should not worry about them
        ParameterizedType parameterizedType = variable.parameterizedType();
        Properties valueProperties = analyserContext.defaultValueProperties(parameterizedType, true);
        valueProperties.replaceDelaysByMinimalValue();
        Instance instance = Instance.forLoopVariable(index(), variable, valueProperties);
        Properties properties = Properties.of(Map.of(
                CONTEXT_MODIFIED, DV.FALSE_DV,
                EXTERNAL_NOT_NULL, NOT_INVOLVED_DV,
                CONTEXT_NOT_NULL, valueProperties.get(NOT_NULL_EXPRESSION),
                EXTERNAL_IMMUTABLE, NOT_INVOLVED_DV,
                CONTEXT_IMMUTABLE, valueProperties.get(IMMUTABLE)
        ));
        Properties allProperties = valueProperties.combine(properties);
        vic.setValue(instance, LinkedVariables.EMPTY, allProperties, true);
        // the linking (normal, and content) can only be done after evaluating the expression over which we iterate
    }

    private void initializeLocalOrDependentVariable(VariableInfoContainer vic, Variable variable) {
        DV defaultNotNull = AnalysisProvider.defaultNotNull(variable.parameterizedType());
        Properties map = sharedContext(defaultNotNull);
        map.put(EXTERNAL_NOT_NULL, NOT_INVOLVED_DV);
        map.put(EXTERNAL_IMMUTABLE, NOT_INVOLVED_DV);
        vic.setValue(new UnknownExpression(variable.parameterizedType(), UnknownExpression.NOT_YET_ASSIGNED),
                LinkedVariables.EMPTY, map, true);
    }

    private void initializeReturnVariable(VariableInfoContainer vic, ReturnVariable returnVariable) {
        DV defaultNotNull = AnalysisProvider.defaultNotNull(methodAnalysis.getMethodInfo().returnType());
        Properties properties = sharedContext(defaultNotNull);

        properties.put(NOT_NULL_EXPRESSION, defaultNotNull);
        properties.put(IDENTITY, IDENTITY.falseDv);
        properties.put(CONTAINER, CONTAINER.falseDv);
        properties.put(IMMUTABLE, MUTABLE_DV);
        properties.put(INDEPENDENT, INDEPENDENT.falseDv);

        properties.put(EXTERNAL_NOT_NULL, NOT_INVOLVED_DV);
        properties.put(EXTERNAL_IMMUTABLE, NOT_INVOLVED_DV);

        UnknownExpression value = new UnknownExpression(returnVariable.returnType, UnknownExpression.RETURN_VALUE);
        vic.setValue(value, LinkedVariables.EMPTY, properties, true);
    }

    private void initializeThis(VariableInfoContainer vic, AnalyserContext analyserContext, This thisVar) {
        // context properties
        Properties properties = sharedContext(MultiLevel.EFFECTIVELY_NOT_NULL_DV);

        // value properties
        properties.put(IDENTITY, IDENTITY.falseDv);
        properties.put(CONTAINER, CONTAINER.falseDv);
        properties.put(IMMUTABLE, IMMUTABLE.falseDv);
        properties.put(INDEPENDENT, INDEPENDENT.falseDv);
        properties.put(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);

        // external: not relevant
        properties.put(EXTERNAL_IMMUTABLE, NOT_INVOLVED_DV);
        properties.put(EXTERNAL_NOT_NULL, NOT_INVOLVED_DV);

        Instance value = Instance.forCatchOrThis(index, thisVar, analyserContext);
        vic.setValue(value, LinkedVariables.of(thisVar, LinkedVariables.STATICALLY_ASSIGNED_DV), properties, true);
    }

    private void initializeFieldReference(VariableInfoContainer vic, EvaluationContext evaluationContext, FieldReference fieldReference) {
        FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);

        // start with context properties
        Properties properties = sharedContext(AnalysisProvider.defaultNotNull(fieldReference.fieldInfo.type));

        // the value and its properties are taken from the field analyser
        Expression value = fieldAnalysis.getValueForStatementAnalyser(fieldReference);
        Properties valueProps = evaluationContext.getValueProperties(value);
        Properties combined = properties.combine(valueProps);

        // the external properties
        DV extNotNull = fieldAnalysis.getProperty(EXTERNAL_NOT_NULL);
        combined.put(EXTERNAL_NOT_NULL, extNotNull);
        DV extImm = fieldAnalysis.getProperty(EXTERNAL_IMMUTABLE);
        combined.put(EXTERNAL_IMMUTABLE, extImm);

        vic.setValue(value, LinkedVariables.EMPTY, combined, true);
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

        DV formallyImmutable = evaluationContext.getAnalyserContext().defaultImmutable(type, false);
        DV immutable = IMMUTABLE.max(parameterAnalysis.getProperty(IMMUTABLE), formallyImmutable)
                .replaceDelayBy(MUTABLE_DV);
        properties.put(IMMUTABLE, immutable);

        DV formallyIndependent = evaluationContext.getAnalyserContext().defaultIndependent(type);
        DV independent = INDEPENDENT.max(parameterAnalysis.getProperty(INDEPENDENT), formallyIndependent)
                .replaceDelayBy(MultiLevel.DEPENDENT_DV);
        properties.put(INDEPENDENT, independent);

        // if the parameter is not explicitly annotated as a container, we can take a default value
        DV container = parameterAnalysis.getProperty(CONTAINER).maxIgnoreDelay(DV.FALSE_DV);
        properties.put(CONTAINER, container);

        boolean identity = parameterInfo.index == 0;
        properties.put(IDENTITY, DV.fromBoolDv(identity));

        // the external properties may be delayed, but if so they're delayed in the correct way!
        DV extNotNull = parameterAnalysis.getProperty(EXTERNAL_NOT_NULL);
        properties.put(EXTERNAL_NOT_NULL, extNotNull);
        DV extImm = parameterAnalysis.getProperty(EXTERNAL_IMMUTABLE);
        properties.put(EXTERNAL_IMMUTABLE, extImm);
        DV mom = parameterAnalysis.getProperty(MODIFIED_OUTSIDE_METHOD);
        properties.put(MODIFIED_OUTSIDE_METHOD, mom);

        Expression value = Instance.initialValueOfParameter(parameterInfo, properties);
        vic.setValue(value, LinkedVariables.EMPTY, properties, true);
    }

    @Override
    public int statementTimeForVariable(AnalyserContext analyserContext, Variable variable, int statementTime) {
        if (variable instanceof FieldReference fieldReference) {
            boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                    MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
            if (inPartOfConstruction) return VariableInfoContainer.NOT_A_VARIABLE_FIELD;

            DV effectivelyFinal = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getProperty(Property.FINAL);
            if (effectivelyFinal.isDelayed()) {
                return VariableInfoContainer.VARIABLE_FIELD_DELAY;
            }
            if (effectivelyFinal.valueIsFalse()) {
                return statementTime;
            }
        }
        return VariableInfoContainer.NOT_A_VARIABLE_FIELD;
    }

    private Properties sharedContext(DV contextNotNull) {
        Properties result = Properties.writable();
        result.put(CONTEXT_NOT_NULL, contextNotNull);
        result.put(CONTEXT_IMMUTABLE, MUTABLE_DV);
        result.put(CONTEXT_MODIFIED, DV.FALSE_DV);
        return result;
    }

    private Properties fieldPropertyMap(AnalyserContext analyserContext,
                                        FieldInfo fieldInfo) {
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
        Properties result = sharedContext(AnalysisProvider.defaultNotNull(fieldInfo.type));

        for (Property vp : FROM_FIELD_ANALYSER_TO_PROPERTIES) {
            DV value = fieldAnalysis.getFieldProperty(analyserContext, fieldInfo, fieldInfo.type.bestTypeInfo(), vp);
            // IMPROVE we're not passing on 'our' analyserContext instead relying on that of the field, which does not know the lambda we're in at the moment
            result.put(vp, value);
        }
        return result;
    }

    @Override
    public int statementTime(VariableInfoContainer.Level level) {
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
     * helper method
     *
     * @param variable the variable
     * @return the most current variable info object
     * @throws IllegalArgumentException when the variable does not yet exist
     */
    @Override
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
    @Override
    public VariableInfo findOrNull(@NotNull Variable variable, VariableInfoContainer.Level level) {
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
        if (vic == null)
            throw new UnsupportedOperationException("Have not yet evaluated " + variable.fullyQualifiedName());
        return vic.current();
    }

    @Override
    public boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        if (!variables.isSet(variableName)) return false;
        VariableInfoContainer vic = variables.get(variableName);
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) return false;
        VariableInfo variableInfo = vic.current();
        if (!variableInfo.variable().isLocal()) return false;

        if (vic.variableNature() instanceof VariableNature.LoopVariable loopVariable) {
            // a loop variable is not merged back to its defining level, so it is local to this block
            // exactly when the parent is the defining statement
            return parent != null && loopVariable.statementIndex().equals(parent.index());
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
        return variables.stream().map(Map.Entry::getValue).map(VariableInfoContainer::current);
    }

    @Override
    public Stream<Map.Entry<String, VariableInfoContainer>> rawVariableStream() {
        return variables.stream();
    }

    @Override
    public Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(VariableInfoContainer.Level level) {
        return variables.stream()
                .filter(e -> switch (level) {
                    case INITIAL -> throw new UnsupportedOperationException();
                    case EVALUATION -> e.getValue().hasEvaluation();
                    case MERGE -> true;
                });
    }


    @Override
    public Expression notNullValuesAsExpression(EvaluationContext evaluationContext) {
        return And.and(evaluationContext, variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference
                        && vi.isAssigned()
                        && index().equals(vi.getAssignmentIds().getLatestAssignmentIndex()))
                .map(vi -> {
                    if (vi.variable() instanceof FieldReference fieldReference) {
                        if (vi.getValue() instanceof NullConstant) {
                            return new Pair<>(vi, DV.MIN_INT_DV);
                        }
                        DV notNull = evaluationContext.getProperty(new VariableExpression(fieldReference),
                                NOT_NULL_EXPRESSION, false, false);
                        return new Pair<>(vi, notNull);
                    }
                    return null;
                })
                .filter(e -> e != null && (e.v == DV.MIN_INT_DV || e.v.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)))
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
        return new SimpleSet(location(), CauseOfDelay.Cause.LOCAL_VARS_ASSIGNED); // DELAY
    }

    @Override
    public DV isEscapeAlwaysExecutedInCurrentBlock() {
        if (!flowData().interruptsFlowIsSet()) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
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
        return lvc.declarations.get(0).localVariableReference();
    }

    // updates variables.get(loopVar)
    @Override
    public CausesOfDelay evaluationOfForEachVariable(Variable loopVar,
                                                     Expression evaluatedIterable,
                                                     CausesOfDelay someValueWasDelayed,
                                                     EvaluationContext evaluationContext) {
        LinkedVariables linked = evaluatedIterable.linkedVariables(evaluationContext);
        VariableInfoContainer vic = findForWriting(loopVar);
        vic.ensureEvaluation(location(), new AssignmentIds(index() + EVALUATION), VariableInfoContainer.NOT_YET_READ,
                Set.of());
        ParameterizedType parameterizedType = loopVar.parameterizedType();
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();

        DV nne = notNullOfLoopVariable(evaluationContext, evaluatedIterable, someValueWasDelayed);
        Properties valueProperties = analyserContext.defaultValueProperties(parameterizedType, nne, true);

        CausesOfDelay delayed = valueProperties.delays();
        Expression value;
        if (evaluatedIterable.isDelayed() || delayed.isDelayed()) {
            CausesOfDelay causes = evaluatedIterable.isDelayed() ? evaluatedIterable.causesOfDelay() : delayed;
            value = DelayedVariableExpression.forLocalVariableInLoop(loopVar, causes);
        } else {
            value = Instance.forLoopVariable(index(), loopVar, valueProperties);
        }
        vic.setValue(value, LinkedVariables.EMPTY, valueProperties, false);
        vic.setLinkedVariables(linked, EVALUATION);
        return value.causesOfDelay();
    }

    private static DV notNullOfLoopVariable(EvaluationContext evaluationContext, Expression value, CausesOfDelay delays) {
        if (delays.isDelayed()) {
            // we want to avoid a particular value on EVAL for the loop variable
            return delays;
        }
        DV nne = evaluationContext.getProperty(value, NOT_NULL_EXPRESSION, false, false);
        return nne.isDelayed() || nne.lt(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV)
                ? nne : MultiLevel.composeOneLevelLessNotNull(nne);
    }


    /*
    not directly in EvaluationResult, because we could have ENN = 0 on a local field copy, and ENN = 1 on the field itself.
    that is only "leveled out" using the dependency graph of static assignments

    the presence of the IN_NOT_NULL_CONTEXT flag implies that CNN was 0
     */
    @Override
    public void potentiallyRaiseErrorsOnNotNullInContext(Map<Variable, EvaluationResult.ChangeData> changeDataMap) {
        for (Map.Entry<Variable, EvaluationResult.ChangeData> e : changeDataMap.entrySet()) {
            Variable variable = e.getKey();
            EvaluationResult.ChangeData changeData = e.getValue();
            if (changeData.getProperty(IN_NOT_NULL_CONTEXT).valueIsTrue()) {
                VariableInfoContainer vic = findOrNull(variable);
                VariableInfo vi = vic.best(EVALUATION);
                if (vi != null && !(vi.variable() instanceof ParameterInfo)) {
                    DV externalNotNull = vi.getProperty(Property.EXTERNAL_NOT_NULL);
                    DV notNullExpression = vi.getProperty(NOT_NULL_EXPRESSION);
                    if (vi.valueIsSet() && externalNotNull.equals(MultiLevel.NULLABLE_DV)
                            && notNullExpression.equals(MultiLevel.NULLABLE_DV)) {
                        ensure(Message.newMessage(location(), Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION,
                                "Variable: " + variable.simpleName()));
                    }
                }
            }
            if (changeData.getProperty(CANDIDATE_FOR_NULL_PTR_WARNING).valueIsTrue()) {
                ensureCandidateVariableForNullPtrWarning(variable);
            }
        }
    }

    /*
    if(x == null) { ...} when we know that x will never be null (or the other way around) based
    on properties of x rather than the value of x
     */
    @Override
    public void potentiallyRaiseNullPointerWarningENN() {
        candidateVariablesForNullPtrWarningStream().forEach(variable -> {
            VariableInfo vi = findOrNull(variable, VariableInfoContainer.Level.MERGE);
            DV cnn = vi.getProperty(CONTEXT_NOT_NULL); // after merge, CNN should still be too low
            if (cnn.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                ensure(Message.newMessage(location(), Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN,
                        "Variable: " + variable.fullyQualifiedName()));
            }
        });
    }

    @Override
    public CausesOfDelay applyPrecondition(Precondition precondition,
                                           EvaluationContext evaluationContext,
                                           ConditionManager localConditionManager) {
        if (precondition != null) {
            Expression preconditionExpression = precondition.expression();
            if (preconditionExpression.isBoolValueFalse()) {
                ensure(Message.newMessage(location, Message.Label.INCOMPATIBLE_PRECONDITION));
                stateData().setPreconditionAllowEquals(Precondition.empty(primitives()));
            } else {
                Expression translated = evaluationContext.acceptAndTranslatePrecondition(precondition.expression());
                if (translated != null) {
                    Precondition pc = new Precondition(translated, precondition.causes());
                    stateData().setPrecondition(pc, preconditionExpression.isDelayed());
                }
                if (preconditionExpression.isDelayed()) {
                    log(DELAYED, "Apply of {}, {} is delayed because of precondition",
                            index(), methodAnalysis.getMethodInfo().fullyQualifiedName);
                    stateData.setPrecondition(new Precondition(preconditionExpression,
                            precondition.causes()), true);
                    return preconditionExpression.causesOfDelay();
                }
                Expression result = localConditionManager.evaluate(evaluationContext, preconditionExpression);
                if (result.isBoolValueFalse()) {
                    ensure(Message.newMessage(location, Message.Label.INCOMPATIBLE_PRECONDITION));
                }
            }
        } else if (!stateData().preconditionIsFinal()) {
            // undo a potential previous delay, so that no precondition is seen to be present
            stateData().setPrecondition(null, true);
        }
        return CausesOfDelay.EMPTY;
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
    public void ensureVariables(EvaluationContext evaluationContext,
                                Variable variable,
                                EvaluationResult.ChangeData changeData,
                                int newStatementTime) {
        VariableInfoContainer vic;
        if (!variableIsSet(variable.fullyQualifiedName())) {
            assert variable.variableNature() instanceof VariableNature.NormalLocalVariable :
                    "Encountering variable " + variable.fullyQualifiedName() + " of nature " + variable.variableNature();
            vic = createVariable(evaluationContext, variable, flowData().getInitialTime(),
                    VariableNature.normal(variable, index()));
        } else {
            vic = getVariable(variable.fullyQualifiedName());

        }
        String id = index() + EVALUATION;
        VariableInfo initial = vic.getPreviousOrInitial();
        AssignmentIds assignmentIds = changeData.markAssignment() ? new AssignmentIds(id) : initial.getAssignmentIds();
        // we do not set readId to the empty set when markAssignment... we'd rather keep the old value
        // we will compare the recency anyway

        String readId = changeData.readAtStatementTime().isEmpty() ? initial.getReadId() : id;

        vic.ensureEvaluation(location, assignmentIds, readId, changeData.readAtStatementTime());
        if (evaluationContext.isMyself(variable)) vic.setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV, EVALUATION);
    }


    /*
     we keep track which local variables are assigned in a loop

     add this variable name to all parent loop statements until definition of the local variable
     in this way, a version can be introduced to be used before this assignment.

     at the same time, a new local copy has to be created in this statement to be used after the assignment
     */

    @Override
    public void addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName) {
        StatementAnalysis sa = this;
        String loopIndex = null;
        while (sa != null) {
            if (!sa.variableIsSet(fullyQualifiedName)) return;
            VariableInfoContainer localVic = sa.getVariable(fullyQualifiedName);
            if (!(localVic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop)) return;
            if (sa.statement() instanceof LoopStatement) {
                ((StatementAnalysisImpl) sa).ensureLocalVariableAssignedInThisLoop(fullyQualifiedName);
                loopIndex = sa.index();
                break; // we've found the loop
            }
            sa = sa.parent();
        }
        assert loopIndex != null;
    }
}
