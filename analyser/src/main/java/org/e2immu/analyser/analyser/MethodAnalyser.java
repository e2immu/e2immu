/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

/*
 * computes for the method:
 *
 * @Identity, @Fluent
 * @StaticSideEffectsOnly
 * @NotModified
 *
 * and for the parameters
 *
 * @NotModified
 * @NullNotAllowed
 *
 */

public class MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);

    private final TypeContext typeContext;
    private final ParameterAnalyser parameterAnalyser;

    public MethodAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
        this.parameterAnalyser = new ParameterAnalyser(typeContext);
    }

    public void check(MethodInfo methodInfo) {
        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(methodInfo, Independent.class, typeContext.independent.get());
        check(methodInfo, NotModified.class, typeContext.notModified.get());

        if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
            check(methodInfo, NotNull.class, typeContext.notNull.get());
            check(methodInfo, Fluent.class, typeContext.fluent.get());
            check(methodInfo, Identity.class, typeContext.identity.get());

            // NOTE: the reason we do not check @Constant in the same way is that there can be many types
            // of constants, and we have not yet provided them all in @Constant. At the same time,
            // singleReturnValue is used in expressions; this is faster and more reliable
            Value singleReturnValue = methodInfo.methodAnalysis.singleReturnValue.isSet() ? methodInfo.methodAnalysis.singleReturnValue.get() : UnknownValue.NO_VALUE;
            boolean haveConstantAnnotation =
                    CheckConstant.checkConstant(singleReturnValue, methodInfo.returnType(), methodInfo.methodInspection.get().annotations,
                            (valueToTest, typeMsg) -> typeContext.addMessage(Message.Severity.ERROR, "Method " + methodInfo.fullyQualifiedName() +
                                    ": expected constant value " + valueToTest + " of type " + typeMsg + ", got " + singleReturnValue));
            if (haveConstantAnnotation && singleReturnValue == UnknownValue.NO_VALUE) {
                typeContext.addMessage(Message.Severity.ERROR, "Method " + methodInfo.fullyQualifiedName() + " has no single return value");
            }
        }
        methodInfo.methodAnalysis.unusedLocalVariables.visit((lv, b) -> {
            if (b)
                typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                        ", local variable " + lv.name + " is not used");
        });

        methodInfo.methodInspection.get().parameters.forEach(parameterAnalyser::check);
    }

    private void check(MethodInfo methodInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Method " + methodInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @" + annotation.getTypeName()));
    }

    public boolean analyse(MethodInfo methodInfo, VariableProperties methodProperties) {
        List<Statement> statements = methodInfo.methodInspection.get().methodBody.get().statements;
        if (!statements.isEmpty()) {
            return analyseMethod(methodInfo, methodProperties, statements);
        }
        return false;
    }

    // return when there have been changes
    private boolean analyseMethod(MethodInfo methodInfo, VariableProperties methodProperties, List<Statement> statements) {
        boolean changes = false;

        log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());

        if (!methodInfo.methodAnalysis.numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            recursivelyCreateNumberedStatements(statements, indices, numberedStatements, new SideEffectContext(typeContext, methodInfo));
            methodInfo.methodAnalysis.numberedStatements.set(ImmutableList.copyOf(numberedStatements));
            changes = true;
        }
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            methodProperties.create(parameterInfo, new VariableValue(parameterInfo));
        }
        if (analyseFlow(methodInfo, methodProperties)) changes = true;
        return changes;
    }

    public static NumberedStatement recursivelyCreateNumberedStatements(List<Statement> statements,
                                                                        Stack<Integer> indices,
                                                                        List<NumberedStatement> numberedStatements,
                                                                        SideEffectContext sideEffectContext) {
        int statementIndex = 0;
        NumberedStatement first = null;
        NumberedStatement previous = null;
        for (Statement statement : statements) {
            NumberedStatement numberedStatement = new NumberedStatement(sideEffectContext, statement, join(indices, statementIndex));
            numberedStatements.add(numberedStatement);
            if (previous != null) previous.next.set(Optional.of(numberedStatement));
            previous = numberedStatement;
            if (first == null) first = numberedStatement;
            indices.push(statementIndex);

            int blockIndex = 0;
            List<NumberedStatement> blocks = new ArrayList<>();
            CodeOrganization codeOrganization = statement.codeOrganization();
            if (codeOrganization.statements != Block.EMPTY_BLOCK) {
                blockIndex = createBlock(indices, numberedStatements, sideEffectContext, blockIndex, blocks, codeOrganization.statements);
            }
            for (CodeOrganization subStatements : codeOrganization.subStatements) {
                if (subStatements.statements != Block.EMPTY_BLOCK) {
                    blockIndex = createBlock(indices, numberedStatements, sideEffectContext, blockIndex, blocks, subStatements.statements);
                }
            }
            numberedStatement.blocks.set(ImmutableList.copyOf(blocks));
            indices.pop();

            ++statementIndex;
        }
        if (previous != null)
            previous.next.set(Optional.empty());
        return first;
    }

    private static int createBlock(Stack<Integer> indices, List<NumberedStatement> numberedStatements,
                                   SideEffectContext sideEffectContext, int blockIndex,
                                   List<NumberedStatement> blocks, HasStatements statements) {
        indices.push(blockIndex);
        NumberedStatement firstOfBlock =
                recursivelyCreateNumberedStatements(statements.getStatements(), indices, numberedStatements, sideEffectContext);
        blocks.add(firstOfBlock);
        indices.pop();
        return blockIndex + 1;
    }

    @NotModified
    private static int[] join(@NotModified @NullNotAllowed List<Integer> baseIndices, int index) {
        int[] res = new int[baseIndices.size() + 1];
        int i = 0;
        for (Integer bi : baseIndices) res[i++] = bi;
        res[i] = index;
        return res;
    }

    private boolean analyseFlow(MethodInfo methodInfo, VariableProperties methodProperties) {
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;

            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();
            LOGGER.debug("Analysing {} statements", numberedStatements.size());

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeVariablePropertiesOfMethod(numberedStatements, methodInfo, methodProperties)) changes = true;
            if (updateIndependence(methodInfo)) changes = true;

            if (!methodAnalysis.staticMethodCallsOnly.isSet()) {
                if (methodInfo.isStatic) {
                    methodAnalysis.staticMethodCallsOnly.set(true);
                } else {
                    AtomicBoolean atLeastOneCallOnThis = new AtomicBoolean(false);
                    statementVisitor(numberedStatements, numberedStatement -> {
                        Stream<MethodCall> methodCalls = numberedStatement.statement.codeOrganization().findExpressionRecursivelyInStatements(MethodCall.class);
                        boolean callOnThis = methodCalls.anyMatch(methodCall ->
                                !methodCall.methodInfo.isStatic &&
                                        methodCall.object == null || ((methodCall.object instanceof This) &&
                                        ((This) methodCall.object).typeInfo == methodInfo.typeInfo));
                        if (callOnThis) atLeastOneCallOnThis.set(true);
                        return !callOnThis; // we have not found a call on This yet, so we keep on searching!
                    });
                    boolean staticMethodCallsOnly = !atLeastOneCallOnThis.get();
                    log(STATIC_METHOD_CALLS, "Method {} is not static, does it have no calls on <this> scope? {}", methodInfo.fullyQualifiedName(), staticMethodCallsOnly);
                    methodAnalysis.staticMethodCallsOnly.set(staticMethodCallsOnly);
                }
                changes = true;
            }
            long returnStatements = numberedStatements.stream().filter(ns -> ns.statement instanceof ReturnStatement).count();
            if (returnStatements > 0) {

                //@Identity
                boolean identity = numberedStatements.stream().filter(ns -> onReturnStatement(ns,
                        e -> ReturnStatement.isIdentity(typeContext, e) == Boolean.TRUE))
                        .count() == returnStatements;
                if (identity && !methodAnalysis.annotations.isSet(typeContext.identity.get())) {
                    methodAnalysis.annotations.put(typeContext.identity.get(), true);
                    log(ANALYSER, "Set @Identity");
                    changes = true;
                }
                boolean identityFalse = numberedStatements.stream().anyMatch(ns -> onReturnStatement(ns,
                        e -> ReturnStatement.isIdentity(typeContext, e) == Boolean.FALSE));
                if (identityFalse && !methodAnalysis.annotations.isSet(typeContext.identity.get())) {
                    methodAnalysis.annotations.put(typeContext.identity.get(), false);
                    log(ANALYSER, "Set NOT @Identity");
                    changes = true;
                }

                //@Fluent
                boolean fluent = numberedStatements.stream().filter(ns -> onReturnStatement(ns,
                        e -> ReturnStatement.isFluent(typeContext, e) == Boolean.TRUE))
                        .count() == returnStatements;
                if (fluent && !methodAnalysis.annotations.isSet(typeContext.fluent.get())) {
                    methodAnalysis.annotations.put(typeContext.fluent.get(), true);
                    changes = true;
                    log(ANALYSER, "Set @Fluent");
                }
                boolean fluentFalse = numberedStatements.stream().anyMatch(ns -> onReturnStatement(ns,
                        e -> ReturnStatement.isFluent(typeContext, e) == Boolean.FALSE));
                if (fluentFalse && !methodAnalysis.annotations.isSet(typeContext.fluent.get())) {
                    methodAnalysis.annotations.put(typeContext.fluent.get(), false);
                    log(ANALYSER, "Set NOT @Fluent");
                    changes = true;
                }

                // @NotNull
                boolean notNull = numberedStatements.stream().filter(ns -> ns.returnsNotNull.isSet() && ns.returnsNotNull.get() == Boolean.TRUE)
                        .count() == returnStatements;
                if (notNull && !methodAnalysis.annotations.isSet(typeContext.notNull.get())) {
                    methodAnalysis.annotations.put(typeContext.notNull.get(), true);
                    log(NOT_NULL, "Set @NotNull on {}", methodInfo.fullyQualifiedName());
                    changes = true;
                }
                boolean notNullFalse = numberedStatements.stream().anyMatch(ns -> ns.returnsNotNull.isSet() && Boolean.FALSE == ns.returnsNotNull.get());
                if (notNullFalse && !methodAnalysis.annotations.isSet(typeContext.notNull.get())) {
                    methodAnalysis.annotations.put(typeContext.notNull.get(), false);
                    log(NOT_NULL, "Set NOT @NotNull on {}", methodInfo.fullyQualifiedName());
                    changes = true;
                }

                // @Constant,
                // this runs in the first pass
                if (!methodAnalysis.singleReturnValue.isSet()) {
                    Value value;
                    if (returnStatements == 1) {
                        value = numberedStatements.stream()
                                .filter(ns -> ns.returnValue.isSet())
                                .map(ns -> ns.returnValue.get())
                                .findAny().orElse(UnknownValue.NO_VALUE);
                    } else {
                        value = new Instance(methodInfo.returnType());
                    }
                    if (value != UnknownValue.NO_VALUE) {
                        methodAnalysis.singleReturnValue.set(value);
                        AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
                        methodAnalysis.annotations.put(constantAnnotation, true);
                        log(CONSTANT, "Added @Constant annotation on {}", methodInfo.fullyQualifiedName());
                        changes = true;
                    }
                }
            }

            // detect pure will rely on @Identity, @Fluent...
            if (!methodInfo.isConstructor) {
                if (methodInfo.isStatic) {
                    if (!methodAnalysis.createObjectOfSelf.isSet()) {
                        boolean createSelf = numberedStatements.stream().flatMap(ns -> Statement.findExpressionRecursivelyInStatements(ns.statement, NewObject.class))
                                .anyMatch(no -> no.parameterizedType.typeInfo == methodInfo.typeInfo);
                        log(UTILITY_CLASS, "Is {} a static non-constructor method that creates self? {}", methodInfo.fullyQualifiedName(), createSelf);
                        methodAnalysis.createObjectOfSelf.set(createSelf);
                        changes = true;
                    }
                }
                if (methodIsNotModified(methodInfo, methodAnalysis)) changes = true;
            }
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    private static boolean emptyOrStaticIfTrue(SetOnceMap<FieldInfo, Boolean> map) {
        if (map.isEmpty()) return true;
        return map.stream().allMatch(e -> !e.getValue() || e.getKey().isStatic());
    }

    // depending on the nature of the static method, try to ensure if it is @PureFunction, @PureSupplier, @PureConsumer
    private boolean methodIsNotModified(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        // already done the work?
        if (methodAnalysis.annotations.isSet(typeContext.notModified.get())) return false;

        if (!methodAnalysis.complainedAboutMissingStaticStatement.isSet()) {
            if (!methodInfo.isStatic) {
                // we need to check if there's fields being read/assigned/
                if (emptyOrStaticIfTrue(methodAnalysis.fieldRead) &&
                        emptyOrStaticIfTrue(methodAnalysis.fieldAssignments) &&
                        (!methodAnalysis.thisRead.isSet() || !methodAnalysis.thisRead.get()) &&
                        !methodInfo.hasOverrides() &&
                        !methodInfo.isDefaultImplementation &&
                        methodAnalysis.staticMethodCallsOnly.isSet() && methodAnalysis.staticMethodCallsOnly.get()) {
                    typeContext.addMessage(Message.Severity.ERROR, "Method " + methodInfo.fullyQualifiedName() +
                            " should be marked static");
                    methodAnalysis.complainedAboutMissingStaticStatement.set(true);
                    return false;
                }
            }
            methodAnalysis.complainedAboutMissingStaticStatement.set(false);
        }

        // second step

        Boolean isNotModified = methodInfo.isAllParametersNotModified(typeContext);
        if (isNotModified == null) {
            log(NOT_MODIFIED, "Method {}: Not deciding on @NotModified yet, delaying because of parameters",
                    methodInfo.fullyQualifiedName());
            return false;
        }
        if (!isNotModified) {
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some parameters are not @NotModified",
                    methodInfo.fullyQualifiedName());
        } else {
            log(NOT_MODIFIED, "Mark method {} as @NotModified", methodInfo.fullyQualifiedName());
        }
        methodAnalysis.annotations.put(typeContext.notModified.get(), isNotModified);
        return true;
    }

    private boolean updateVariablesLinkedToMethodResult(List<NumberedStatement> numberedStatements,
                                                        MethodInfo methodInfo,
                                                        VariableProperties methodProperties) {
        if (methodInfo.methodAnalysis.variablesLinkedToMethodResult.isSet()) return false;

        Set<Variable> variables = new HashSet<>();
        for (NumberedStatement numberedStatement : numberedStatements) {
            if (numberedStatement.statement instanceof ReturnStatement) {
                if (numberedStatement.linkedVariables.isSet()) { // this implies the statement is a return statement
                    for (Variable variable : numberedStatement.linkedVariables.get()) {
                        Set<Variable> dependencies;
                        if (variable instanceof FieldReference) {
                            if (!((FieldReference) variable).fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
                                log(LINKED_VARIABLES, "Dependencies of {} have not yet been established", variable.detailedString());
                                return false;
                            }
                            dependencies = ((FieldReference) variable).fieldInfo.fieldAnalysis.variablesLinkedToMe.get();
                        } else if (variable instanceof ParameterInfo) {
                            dependencies = Set.of(variable);
                        } else if (variable instanceof LocalVariableReference) {
                            dependencies = methodProperties.variablesLinkedToFieldsAndParameters.getOrDefault(variable, Set.of());
                        } else {
                            dependencies = Set.of(); // TODO This...
                        }
                        log(LINKED_VARIABLES, "Dependencies of {} are [{}]", variable.detailedString(), Variable.detailedString(dependencies));
                        variables.addAll(dependencies);
                    }
                } else {
                    log(LINKED_VARIABLES, "Not yet ready to compute linked variables of method {}", methodInfo.fullyQualifiedName());
                    return false;
                }
            }
        }
        methodInfo.methodAnalysis.variablesLinkedToMethodResult.set(variables);
        methodInfo.methodAnalysis.annotations.put(typeContext.linked.get(), !variables.isEmpty());
        log(LINKED_VARIABLES, "Set variables linked to result of {} to [{}]", methodInfo.fullyQualifiedName(), Variable.detailedString(variables));
        return true;
    }

    // goal: we need to establish that in this method, recursively, a given field is linked to one or more fields or parameters
    // we need to find out if a parameter is linked, recursively, to another field or parameter
    // local variables need to be taken out of the loop

    // in essence: moving from the dependency graph to the MethodAnalysis.variablesLinkedToFieldsAndParameters data structure
    // gets rid of local vars and transitive links
    private static boolean establishLinks(MethodInfo methodInfo, VariableProperties methodProperties) {
        log(LINKED_VARIABLES, "Establishing links, copying from dependency graph of size {}",
                methodProperties.dependencyGraph.size());
        AtomicBoolean changes = new AtomicBoolean();
        methodProperties.dependencyGraph.visit((variable, dependencies) -> {
            Set<Variable> terminals = new HashSet<>(methodProperties.dependencyGraph.dependencies(variable));
            if (dependencies != null) {
                dependencies.stream().filter(d -> d instanceof ParameterInfo).forEach(terminals::add);
            }
            terminals.remove(variable); // removing myself
            methodProperties.variablesLinkedToFieldsAndParameters.put(variable, terminals);
            log(LINKED_VARIABLES, "MA: Set terminals of {} in {} to {}", variable.detailedString(),
                    methodInfo.fullyQualifiedName(), Variable.detailedString(terminals));

            if (variable instanceof FieldReference) {
                if (!methodInfo.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(variable)) {
                    methodInfo.methodAnalysis.fieldsLinkedToFieldsAndVariables.put(variable, terminals);
                    changes.set(true);
                    log(LINKED_VARIABLES, "MA: Decide on links of {} in {} to {}", variable.detailedString(),
                            methodInfo.fullyQualifiedName(), Variable.detailedString(terminals));
                }
            }
        });
        return changes.get();
    }


    // we need a recursive structure because local variables can be defined in blocks, a little later,
    // they disappear again. But, we should also be able to add properties simply for a block, so that those
    // properties disappear when that level disappears

    private boolean computeVariablePropertiesOfMethod(List<NumberedStatement> statements, MethodInfo methodInfo,
                                                      VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
        StatementAnalyser statementAnalyser = new StatementAnalyser(typeContext, methodInfo);
        if (statementAnalyser.computeVariablePropertiesOfBlock(statements.get(0), methodProperties)) changes = true;

        if (establishLinks(methodInfo, methodProperties)) changes = true;

        if (!methodInfo.isConstructor && updateVariablesLinkedToMethodResult(statements, methodInfo, methodProperties))
            changes = true;

        if (updateAnnotationsFromMethodProperties(methodAnalysis, methodProperties)) changes = true;
        if (updateParameterAnnotationsFromMethodProperties(methodInfo, methodProperties)) changes = true;
        if (updateFieldAnnotationsFromMethodProperties(methodInfo, methodProperties)) changes = true;

        return changes;
    }

    private boolean updateIndependence(MethodInfo methodInfo) {
        if (methodInfo.methodAnalysis.annotations.isSet(typeContext.independent.get())) return false;
        Boolean mark = null;
        if (methodInfo.isConstructor) {
            List<FieldInfo> fields = methodInfo.typeInfo.typeInspection.get().fields;
            boolean allDefined = fields.stream().allMatch(f -> f.fieldAnalysis.variablesLinkedToMe.isSet());
            if (allDefined) {
                mark = fields.stream().allMatch(f -> Collections.disjoint(f.fieldAnalysis.variablesLinkedToMe.get(),
                        methodInfo.methodInspection.get().parameters));
            }
        } else {
            if (methodInfo.returnType().isPrimitiveOrStringNotVoid()) mark = true;
            if (methodInfo.returnType().isEffectivelyImmutable(typeContext) == Boolean.TRUE) mark = true;
            Boolean linked = methodInfo.methodAnalysis.annotations.getOtherwiseNull(typeContext.linked.get());
            if (linked != null) mark = !linked;
        }
        if (mark != null) {
            methodInfo.methodAnalysis.annotations.put(typeContext.independent.get(), mark);
            log(INDEPENDENT, "Mark method {} " + (mark ? "" : "not ") + "independent",
                    methodInfo.fullyQualifiedName());
        }
        return mark != null;
    }


    private boolean updateAnnotationsFromMethodProperties(MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        boolean changes = false;
        for (Variable variable : methodProperties.variableProperties.keySet()) {
            Set<Variable> linkedVariables = allVariablesLinkedToIncludingMyself(methodProperties, variable);
            Boolean directContentModification = summarizeModification(methodProperties, linkedVariables);
            log(MODIFY_CONTENT, "Starting at {}, we loop over {} to set direct modification {}", variable.detailedString(),
                    Variable.detailedString(linkedVariables), directContentModification);
            for (Variable linkedVariable : linkedVariables) {
                if ((linkedVariable instanceof FieldReference)) {
                    if (!methodAnalysis.contentModifications.isSet(linkedVariable)) {
                        boolean directlyModifiedField = directContentModification == Boolean.TRUE;
                        log(MODIFY_CONTENT, "MA: Mark that the content of {} has " + (directlyModifiedField ? "" : "not ") +
                                "been modified", linkedVariable.detailedString());
                        methodAnalysis.contentModifications.put(linkedVariable, directlyModifiedField);
                        changes = true;
                    }
                } else if (linkedVariable instanceof ParameterInfo) {
                    ParameterInfo parameterInfo = (ParameterInfo) linkedVariable;
                    if (!parameterInfo.isNotModifiedByDefinition(typeContext)) {
                        if (directContentModification != null) {
                            boolean notModified = !directContentModification;
                            if (!parameterInfo.parameterAnalysis.annotations.isSet(typeContext.notModified.get())) {
                                log(MODIFY_CONTENT, "MA: Mark {} not modified? {}", parameterInfo.detailedString(), notModified);
                                parameterInfo.parameterAnalysis.annotations.put(typeContext.notModified.get(), notModified);
                                changes = true;
                            }
                        } else {
                            log(MODIFY_CONTENT, "Delaying setting parameter not modified on {}",
                                    Variable.detailedString(linkedVariables));
                        }
                    }
                }
            }
        }
        return changes;
    }

    private Boolean summarizeModification(VariableProperties methodProperties, Set<Variable> linkedVariables) {
        boolean hasDelays = false;
        for (Variable variable : linkedVariables) {
            if (variable instanceof FieldReference) {
                Boolean notModified = ((FieldReference) variable).fieldInfo.isNotModified(typeContext);
                if (notModified == null) hasDelays = true;
                else if (!notModified) return true;
            }
            // local, parameter, field... data from statement analyser
            VariableProperties.AboutVariable properties = methodProperties.variableProperties.get(variable);
            // properties can be null (variable out of scope)
            if (properties != null && properties.properties.contains(VariableProperty.CONTENT_MODIFIED)) return true;
        }
        return hasDelays ? null : false;
    }

    /**
     * Pure convenience method
     *
     * @param statements the statements to visit
     * @param visitor    will accept a statement; must return true for the visiting to continue
     */
    private static void statementVisitor(List<NumberedStatement> statements, Function<NumberedStatement, Boolean> visitor) {
        if (!statements.isEmpty()) {
            statementVisitor(statements.get(0), visitor);
        }
    }

    /**
     * @param start   starting point, according to the way we have organized statement flows
     * @param visitor will accept a statement; must return true for the visiting to continue
     */
    private static boolean statementVisitor(NumberedStatement start, Function<NumberedStatement, Boolean> visitor) {
        NumberedStatement ns = start;
        while (ns != null) {
            if (!visitor.apply(ns)) return false;
            for (NumberedStatement sub : ns.blocks.get()) {
                if (!statementVisitor(sub, visitor)) return false;
            }
            ns = ns.next.get().orElse(null);
        }
        return true; // continue
    }

    private Set<Variable> allVariablesLinkedToIncludingMyself(VariableProperties methodProperties, Variable variable) {
        Set<Variable> result = new HashSet<>();
        recursivelyAddLinkedVariables(methodProperties, variable, result);
        return result;
    }

    private void recursivelyAddLinkedVariables(VariableProperties methodProperties, Variable variable, Set<Variable> result) {
        if (result.contains(variable)) return;
        result.add(variable);
        Set<Variable> linked = methodProperties.variablesLinkedToFieldsAndParameters.get(variable);
        if (linked != null) {
            for (Variable v : linked) recursivelyAddLinkedVariables(methodProperties, v, result);
        }
        // reverse linking
        List<Variable> reverse = methodProperties.variablesLinkedToFieldsAndParameters.entrySet()
                .stream().filter(e -> e.getValue().contains(variable)).map(Map.Entry::getKey).collect(Collectors.toList());
        reverse.forEach(v -> recursivelyAddLinkedVariables(methodProperties, v, result));
    }

    private boolean updateParameterAnnotationsFromMethodProperties(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : methodProperties.variableProperties.entrySet()) {
            Set<VariableProperty> properties = entry.getValue().properties;
            Variable variable = entry.getKey();
            if (variable instanceof ParameterInfo) {
                if (properties.contains(VariableProperty.ASSIGNED)
                        && !methodInfo.methodAnalysis.parameterAssignments.isSet((ParameterInfo) variable)) {
                    typeContext.addMessage(Message.Severity.ERROR,
                            "Parameter " + variable.detailedString() + " should not be assigned to");
                    methodInfo.methodAnalysis.parameterAssignments.put((ParameterInfo) variable, true);
                    changes = true;
                }
            }
        }
        return changes;
    }

    private static boolean updateFieldAnnotationsFromMethodProperties(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : methodProperties.variableProperties.entrySet()) {
            Variable variable = entry.getKey();
            Set<VariableProperty> properties = entry.getValue().properties;
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                    boolean isModified = properties.contains(VariableProperty.ASSIGNED);
                    methodAnalysis.fieldAssignments.put(fieldInfo, isModified);
                    log(ANALYSER, "Mark that {} is modified? {} in {}", fieldInfo.name, isModified, methodInfo.fullyQualifiedName());
                    changes = true;
                }
                Value currentValue = entry.getValue().getCurrentValue();
                if (currentValue != UnknownValue.NO_VALUE && properties.contains(VariableProperty.ASSIGNED) &&
                        !properties.contains(VariableProperty.ASSIGNED_MULTIPLE_TIMES) &&
                        !methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)) {
                    log(ANALYSER, "Single assignment of field {} to {}", fieldInfo.fullyQualifiedName(), currentValue);
                    methodAnalysis.fieldAssignmentValues.put(fieldInfo, currentValue);
                    changes = true;
                }
                if (properties.contains(VariableProperty.READ) && !methodAnalysis.fieldRead.isSet(fieldInfo)) {
                    log(ANALYSER, "Mark that the content of field {} has been read", variable.detailedString());
                    methodAnalysis.fieldRead.put(fieldInfo, true);
                    changes = true;
                }
            } else if (variable instanceof This && properties.contains(VariableProperty.READ)) {
                if (!methodAnalysis.thisRead.isSet()) {
                    log(ANALYSER, "Mark that 'this' has been read in {}", variable.detailedString());
                    methodAnalysis.thisRead.set(true);
                    changes = true;
                }
            }
        }

        for (FieldInfo fieldInfo : methodInfo.typeInfo.typeInspection.get().fields) {
            if (!methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                methodAnalysis.fieldAssignments.put(fieldInfo, false);
                changes = true;
                log(ANALYSER, "Mark field {} not modified in {}, not present", fieldInfo.fullyQualifiedName(), methodInfo.name);
            }
            //FieldReference fieldReference = new FieldReference(fieldInfo, methodProperties.thisVariable);
            //if (!methodAnalysis.directContentModifications.isSet(fieldReference)) {
            //methodAnalysis.directContentModifications.put(fieldReference, false);
            //changes = true;
            //log(MODIFY_CONTENT, "Mark field {}'s content not modified in {}, not present, not delayed",
            //        fieldInfo.fullyQualifiedName(), methodInfo.name);
            //}
            if (!methodAnalysis.fieldRead.isSet(fieldInfo)) {
                methodAnalysis.fieldRead.put(fieldInfo, false);
                log(ANALYSER, "Mark field {} as ignore in {}, not present", fieldInfo.fullyQualifiedName(), methodInfo.name);
                changes = true;
            }
        }
        return changes;
    }

    private static Boolean onReturnStatement(NumberedStatement ns, Predicate<Expression> predicate) {
        if (ns.statement instanceof ReturnStatement) {
            ReturnStatement ret = (ReturnStatement) ns.statement;
            return predicate.test(ret.expression);
        }
        return false;
    }
}
