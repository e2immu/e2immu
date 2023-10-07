package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.util.ComputeLinkedVariables;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.analyser.Stage.MERGE;

class MergeLinkingAndGroupProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeLinkingAndGroupProperties.class);

    private final StatementAnalysis statementAnalysis;
    private final EvaluationContext evaluationContext;

    private record BackLinkForEachResult(Set<Variable> newlyCreated, CausesOfDelay delays) {
    }

    MergeLinkingAndGroupProperties(EvaluationContext evaluationContext, StatementAnalysis statementAnalysis) {
        this.statementAnalysis = statementAnalysis;
        this.evaluationContext = evaluationContext;
    }

    ProgressAndDelay linkingAndGroupProperties(GroupPropertyValues groupPropertyValues,
                                               Map<Variable, LinkedVariables> linkedVariablesMap,
                                               Map<Variable, Integer> modificationTimes,
                                               int statementTime,
                                               Set<Variable> variablesWhereMergeOverwrites,
                                               Set<LocalVariableReference> newlyCreatedScopeVariables,
                                               Map<Variable, Variable> renames,
                                               Set<Variable> toRemove,
                                               List<VariableInfoContainer> toIgnore,
                                               Map<Variable, DV> setCnnVariables,
                                               TranslationMap translationMap,
                                               CausesOfDelay conditionCauses) {

        BackLinkForEachResult backLink;
        if (statementAnalysis.statement() instanceof ForEachStatement) {
            backLink = backLinkIterable(evaluationContext, linkedVariablesMap);
        } else {
            backLink = new BackLinkForEachResult(Set.of(), CausesOfDelay.EMPTY);
        }
        for (VariableInfoContainer vic : toIgnore) {
            Variable variable = vic.current().variable();
            Variable renamed = renames.get(variable);
            if (renamed != null) {
                ensureDestination(renamed, vic, evaluationContext, statementAnalysis.statementTime(MERGE));
            }
        }

        // then, per cluster of variables
        // which variables should we consider? linkedVariablesMap provides the linked variables from the sub-blocks
        // create looks at these+previous, minus those to be removed.
        Function<Variable, LinkedVariables> linkedVariablesFromBlocks =
                v -> linkedVariablesMap.getOrDefault(v, LinkedVariables.EMPTY);
        // we include -E in touched, see Basics_8 (j, k in statement 4)
        Set<Variable> touched = touchedStream(linkedVariablesMap, newlyCreatedScopeVariables, toRemove,
                renames.keySet());
        boolean oneBranchHasBecomeUnreachable = oneBranchHasBecomeUnreachable();
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(statementAnalysis, MERGE,
                oneBranchHasBecomeUnreachable,
                (vic, v) -> !touched.contains(v),
                variablesWhereMergeOverwrites,
                linkedVariablesFromBlocks, evaluationContext);

        boolean progress = computeLinkedVariables.writeLinkedVariables(computeLinkedVariables, touched,
                toRemove, linkedVariablesMap.keySet());

        for (Variable variable : touched) {
            if ((!linkedVariablesMap.containsKey(variable) || backLink.newlyCreated.contains(variable)) &&
                    !(variable instanceof LocalVariableReference lvr && newlyCreatedScopeVariables.contains(lvr))) {
                VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
                Variable renamed = renames.get(variable);
                if (renamed != null) {
                    // copy from vic into the renamed variable
                    VariableInfoContainer vicRenamed = statementAnalysis.getVariable(renamed.fullyQualifiedName());
                    progress |= vic.copyNonContextFromPreviousOrEvalToMergeOfOther(groupPropertyValues, vicRenamed);
                } else {
                    progress |= vic.copyNonContextFromPreviousOrEvalToMerge(groupPropertyValues);
                }
            }
        }
        HashSet<VariableInfoContainer> ignoredNotTouched = new HashSet<>(toIgnore);
        ignoredNotTouched.removeIf(vic -> touched.contains(vic.current().variable())
                || renames.containsKey(vic.current().variable()));

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

        int statementTimeDelta = statementTime - statementAnalysis.statementTime(EVALUATION);
        ProgressAndDelay cmStatus = computeLinkedVariables.writeContextModified(evaluationContext.getAnalyserContext(),
                groupPropertyValues.getMap(CONTEXT_MODIFIED), Map.of(), statementTimeDelta, modificationTimes,
                conditionCauses, true);

        CausesOfDelay delaysOfValuesOfIgnored = toIgnore.stream()
                .map(vic -> vic.best(MERGE).getValue().causesOfDelay()).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        return new ProgressAndDelay(false, backLink.delays)
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

        StatementAnalysis first = statementAnalysis.navigationData().blocks.get().get(0).orElse(null);
        StatementAnalysis last = first == null ? null : first.lastStatement();
        if (last != null) {
            Expression initialiser = statementAnalysis.statement().getStructure().initialisers().get(0);
            if (initialiser instanceof LocalVariableCreation lvc) {
                assert lvc.hasSingleDeclaration();
                Variable loopVar = lvc.localVariableReference;
                VariableInfo latestVariableInfo = last.getLatestVariableInfo(loopVar.fullyQualifiedName());
                if (latestVariableInfo != null) {
                    LinkedVariables linkedOfLoopVar = latestVariableInfo.getLinkedVariables();
                    // any --3--> link to a variable not local to the loop, we also point to the iterable as <--4-->

                    EvaluationResult context = EvaluationResultImpl.from(evaluationContext);
                    LinkedVariables linkedVariables = statementAnalysis.stateData().valueOfExpressionGet()
                            .linkedVariables(context);

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
        if (statementAnalysis.navigationData().hasSubBlocks()) {
            return statementAnalysis.navigationData().blocks.get().stream()
                    .anyMatch(optSa -> optSa.isPresent() && optSa.get().flowData().isUnreachable());
        }
        return false;
    }

    private Set<Variable> touchedStream(Map<Variable, LinkedVariables> linkedVariablesMap,
                                        Set<? extends Variable> newlyCreatedScopeVariables,
                                        Set<Variable> toRemove,
                                        Set<Variable> toRename) {
        Stream<Variable> currentVariableStream = statementAnalysis.variableInfoContainerStream()
                .filter(vic -> vic.hasEvaluation() ||
                        // the following condition is necessary to include fields with a scope in
                        // newlyCreatedScopeVariables, see e.g. InstanceOf_16
                        vic.current().variable().containsAtLeastOneOf(newlyCreatedScopeVariables))
                .map(e -> e.current().variable());
        Stream<Variable> linkedVariableStream = linkedVariablesMap.values().stream()
                .flatMap(lv -> lv.variables().keySet().stream());
        return Stream.concat(newlyCreatedScopeVariables.stream(), Stream.concat(
                        Stream.concat(linkedVariablesMap.keySet().stream(), linkedVariableStream),
                        currentVariableStream))
                .filter(v -> !toRemove.contains(v)
                        && !toRename.contains(v)
                        && statementAnalysis.variableIsSet(v.fullyQualifiedName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void ensureDestination(Variable renamed,
                                   VariableInfoContainer vic,
                                   EvaluationContext evaluationContext,
                                   int statementTime) {
        if (!statementAnalysis.variableIsSet(renamed.fullyQualifiedName())) {
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
            statementAnalysis.createVariable(evaluationContext, renamed, statementTime, variableNature);
        }
    }

}
