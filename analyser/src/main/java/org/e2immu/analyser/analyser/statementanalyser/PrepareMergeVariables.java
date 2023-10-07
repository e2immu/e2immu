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

import static org.e2immu.analyser.analyser.Stage.EVALUATION;

class PrepareMergeVariables {

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

    // work variables: they'll be returned in an immutable version by the main method of this class.

    private final List<VariableInfoContainer> toMerge = new ArrayList<>();
    private final List<VariableInfoContainer> toIgnore = new ArrayList<>();
    private final Set<Variable> toRemove = new HashSet<>();
    private final Map<Variable, Variable> renames = new HashMap<>();
    private final Set<LocalVariableReference> newScopeVariables = new HashSet<>();
    private final TranslationMapImpl.Builder translationMapBuilder = new TranslationMapImpl.Builder();

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
    }

    Data go(Function<Variable, List<ConditionAndVariableInfo>> filterSub,
            List<MergeVariables.ConditionAndLastStatement> lastStatements,
            Set<Variable> mergeEvenIfNotInSubBlock) {
        part1ComputeToMergeToIgnoreToRemove(lastStatements, mergeEvenIfNotInSubBlock);
        TranslationMap renameMap = part2ComputeTranslationsForToRemove(filterSub);
        part3ComputeRenamesAndNewScopeVariables(renameMap);
        return new Data(List.copyOf(toMerge), List.copyOf(toIgnore), Set.copyOf(toRemove), Map.copyOf(renames),
                Set.copyOf(newScopeVariables), translationMapBuilder.build());
    }

    // ******************************************

    // as a general remark: This and ReturnVariable variables are always merged, never removed
    private void part1ComputeToMergeToIgnoreToRemove(List<MergeVariables.ConditionAndLastStatement> lastStatements,
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
        lastStatements.stream()
                .flatMap(st -> st.lastStatement().getStatementAnalysis().rawVariableStream().map(Map.Entry::getValue))
                .filter(vic -> vic.hasBeenAccessedInThisBlock(index))
                .forEach(vic -> mergeAction(vic, seen, vn -> vn.removeInSubBlockMerge(index), v -> true, useVic));

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

        statementAnalysis.rawVariableStream()
                .map(Map.Entry::getValue)
                .forEach(vic -> mergeAction(vic, seen, vn -> vn.removeInMerge(index),
                        alsoMergeStaticallyAssigned::contains, null));
    }

    private void mergeAction(VariableInfoContainer vic,
                             Set<Variable> seen,
                             Predicate<VariableNature> remove,
                             Predicate<Variable> mergeWhenNotRemove,
                             Map<Variable, VariableInfoContainer> useVic) {
        Variable variable = vic.current().variable();
        if (variable.hasScopeVariableCreatedAt(evaluationContext.statementIndex())) {
            // ignore completely: this variable will be the result of renames computed further
            return;
        }
        /*
         vicToAdd is null when there is no VIC at this level. In the case a merge, we'll need to create one,
         and we can send on the current one. In the case of toIgnore, we must skip this variable!
         see Loops_19 as an example (variable "key", to be ignored in statement "1")
         */
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
            }
        }
    }

    // ******************************************

    /*
    PART 2
    Compute the translations for toRemove variables.
     */

    private TranslationMap part2ComputeTranslationsForToRemove(Function<Variable, List<ConditionAndVariableInfo>> filterSub) {
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
        TranslationMapImpl.Builder bestValueForToRemove = new TranslationMapImpl.Builder();

        for (Map.Entry<Variable, Expression> entry : afterFiltering.entrySet()) {
            Expression expression = entry.getValue();
            Variable toRemove = entry.getKey();
            Expression bestValue = expression.translate(evaluationContext.getAnalyserContext(), instances);
            bestValueForToRemove.addVariableExpression(toRemove, bestValue);
            translationMapBuilder.addVariableExpression(toRemove, bestValue);
            translationMapBuilder.put(expression, bestValue);
        }
        return bestValueForToRemove.build();
    }

    private boolean isVariableInLoopDefinedOutside(Expression value) {
        IsVariableExpression ive;
        if ((ive = value.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfoContainer vic = statementAnalysis.findOrNull(ive.variable());
            return vic != null && index.equals(vic.variableNature().getStatementIndexOfBlockVariable());
        }
        return false;
    }

    // ******************************************

    /*
    PART 3
    Compute renames, new scope variables.

    return newScopeVariables (they'll be translated into, and created later)
    remove from toMerge, toIgnore
    add to renames, translationMap

    we go over the list of variables to merge, and try to find if we need to rename them because
    some scope has to be removed (field reference x.y where x cannot exist anymore).
    this method relies on bestValueForToRemove
    at the same time, when BOTH are present in the toMerge (in a subsequent iteration)
    we remove the non-renamed
    return new scope variables to be created and assigned
     */

    private void part3ComputeRenamesAndNewScopeVariables(TranslationMap renameMap) {
        toMerge.forEach(vic -> computeRename(vic, renameMap));
        toIgnore.forEach(vic -> computeRename(vic, renameMap));
    }

    private void computeRename(VariableInfoContainer vic, TranslationMap renameMap) {
        Variable variable = vic.current().variable();
        RenameVariableResult rvr = renameVariable(variable, renameMap);
        if (rvr != null) {
            newScopeVariables.add(rvr.newScopeVariable);
            renames.put(variable, rvr.fieldReference);
            translationMapBuilder.addVariableExpression(variable, rvr.variableExpression);
            translationMapBuilder.put(variable, rvr.fieldReference);
        }
    }

    private record RenameVariableResult(FieldReference fieldReference,
                                        VariableExpression variableExpression,
                                        LocalVariableReference newScopeVariable) {
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
                return new RenameVariableResult(newFr, ve, scopeVariable);
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
