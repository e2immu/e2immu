package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.ConditionAndVariableInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Stage.EVALUATION;

public class PrepareMergeVariables {

    // input fields

    private final EvaluationContext evaluationContext;
    private final StatementAnalysis statementAnalysis;
    private final String index;

    PrepareMergeVariables(EvaluationContext evaluationContext,
                          StatementAnalysis statementAnalysis) {
        this.evaluationContext = evaluationContext;
        this.statementAnalysis = statementAnalysis;
        index = statementAnalysis.index();
    }

    // work variables

    private final List<VariableInfoContainer> toMerge = new ArrayList<>();
    private final List<VariableInfoContainer> toIgnore = new ArrayList<>();
    private final TranslationMapImpl.Builder translationMap = new TranslationMapImpl.Builder();
    private final TranslationMapImpl.Builder bestValueForToRemove = new TranslationMapImpl.Builder();
    private final Set<Variable> toRemove = new HashSet<>();
    private final Map<Variable, Variable> renames = new HashMap<>();

    /**
     * @param toMerge  variables which will go through the merge process, they will get a -M VariableInfo
     * @param toIgnore variables to be ignored by the merge process, they will not get a -M VariableInfo
     * @param toRemove references to these variables (in value, in scope of field ref) will be replaced by Instance objects
     */
    record Data(List<VariableInfoContainer> toMerge,
                List<VariableInfoContainer> toIgnore,
                Set<Variable> toRemove,
                Map<Variable, Variable> renames,
                Set<LocalVariableReference> newScopeVariables,
                TranslationMap translationMap) {


        // we go over the list of variables to merge, and try to find if we need to rename them because
        // some scope has to be removed (field reference x.y where x cannot exist anymore).
        // this method relies on bestValueForToRemove
        // at the same time, when BOTH are present in the toMerge (in a subsequent iteration)
        // we remove the non-renamed
        // return new scope variables to be created and assigned

    }

    // as a general remark: This and ReturnVariable variables are always merged, never removed
    private void mergeActions(List<MergeVariables.ConditionAndLastStatement> lastStatements,
                              Set<Variable> mergeEvenIfNotInSubBlocks) {

        // some variables will be picked up in the sub-blocks, others in the current block, others in both
        // we want to deal with them only once, though.
        Set<Variable> seen = new HashSet<>();
        Map<Variable, VariableInfoContainer> useVic = statementAnalysis.rawVariableStream()
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
        mergeAction(seen, fromSubBlocks, vn -> vn.removeInSubBlockMerge(index), v -> true, useVic);

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
        Set<Variable> staticallyLinked = toMerge.stream()
                .flatMap(vic -> vic.best(EVALUATION).getLinkedVariables().staticallyAssignedStream())
                .collect(Collectors.toUnmodifiableSet());
        alsoMergeStaticallyAssigned.addAll(staticallyLinked);

        Stream<VariableInfoContainer> atTopLevel = statementAnalysis.rawVariableStream().map(Map.Entry::getValue);
        mergeAction(seen, atTopLevel, vn -> vn.removeInMerge(index),
                alsoMergeStaticallyAssigned::contains, null);
    }

    Set<LocalVariableReference> newScopeVariables() {
        TranslationMap renameMap = bestValueForToRemove.build();
        Set<LocalVariableReference> newScopeVariables = new HashSet<>();
        toMerge.removeIf(vic -> prepareRenameDecideToRemove(vic, renameMap, newScopeVariables));
        toIgnore.removeIf(vic -> prepareRenameDecideToRemove(vic, renameMap, newScopeVariables));
        return Set.copyOf(newScopeVariables);
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

    private void mergeAction(Set<Variable> seen,
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
                        toIgnore.add(vicToAdd);
                    }
                    toRemove.add(variable);
                } else {
                    if (mergeWhenNotRemove.test(variable)) {
                        VariableInfoContainer vicMerge = vicToAdd == null ? vic : vicToAdd;
                        if (useVic != null && vicToAdd == null) {
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
                        toMerge.add(vicMerge);
                    } else if (vicToAdd != null) {
                        toIgnore.add(vicToAdd);
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


    private boolean isVariableInLoopDefinedOutside(Expression value) {
        IsVariableExpression ive;
        if ((ive = value.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfoContainer vic = statementAnalysis.findOrNull(ive.variable());
            return vic != null && index.equals(vic.variableNature().getStatementIndexOfBlockVariable());
        }
        return false;
    }


    Data go(Function<Variable,
            List<ConditionAndVariableInfo>> filterSub,
            List<MergeVariables.ConditionAndLastStatement> lastStatements,
            Set<Variable> mergeEvenIfNotInSubBlock) {
        mergeActions(lastStatements, mergeEvenIfNotInSubBlock);
        // 2 more steps: fill in PrepareMerge.bestValueForToRemove, then compute renames
        TranslationMapImpl.Builder outOfScopeBuilder = new TranslationMapImpl.Builder();
        Map<Variable, Expression> afterFiltering = new HashMap<>();
        for (Variable toRemove : toRemove) {

            List<ConditionAndVariableInfo> toMerge = filterSub.apply(toRemove);
            if (!toMerge.isEmpty()) { // a try statement can have more than one, it's only the first we're interested
                // and finally, copy the result into prepareMerge
                VariableInfo best = toMerge.get(0).variableInfo();
                Identifier identifier = best.getIdentifier();
                Expression outOfScopeValue;
                org.e2immu.analyser.analyser.Properties bestProperties = best.valueProperties();
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
        /*
        Recurse: yes, if the value has self references; no, to keep the new scope variable system alive
         */
        TranslationMap instances = outOfScopeBuilder.setRecurseIntoScopeVariables(true).build();
        for (Map.Entry<Variable, Expression> entry : afterFiltering.entrySet()) {
            Expression expression = entry.getValue();
            Variable toRemove = entry.getKey();
            Expression bestValue = expression.translate(evaluationContext.getAnalyserContext(), instances);
            bestValueForToRemove.addVariableExpression(toRemove, bestValue);
            translationMap.addVariableExpression(toRemove, bestValue);
            translationMap.put(expression, bestValue);
        }
        Set<LocalVariableReference> newScopeVariables = newScopeVariables();
        return new Data(List.copyOf(toMerge), List.copyOf(toIgnore), Set.copyOf(toRemove), Map.copyOf(renames),
                newScopeVariables, translationMap.build());
    }
}
