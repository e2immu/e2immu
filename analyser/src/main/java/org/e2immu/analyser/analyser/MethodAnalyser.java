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
import org.e2immu.analyser.analyser.check.CheckOnly;
import org.e2immu.analyser.analyser.check.CheckPrecondition;
import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.analyser.methodanalysercomponent.StaticModifier;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.pattern.JoinReturnStatements;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);

    private final TypeContext typeContext;
    private final ParameterAnalyser parameterAnalyser;
    private final ComputeLinking computeLinking;

    public MethodAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
        this.parameterAnalyser = new ParameterAnalyser(typeContext);
        this.computeLinking = new ComputeLinking(typeContext);
    }

    public void check(MethodInfo methodInfo) {
        // before we check, we copy the properties into annotations
        methodInfo.methodAnalysis.get().transferPropertiesToAnnotations(typeContext);

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(methodInfo, Independent.class, typeContext.independent.get());
        check(methodInfo, NotModified.class, typeContext.notModified.get());

        if (!methodInfo.isConstructor) {
            if (!methodInfo.isVoid()) {
                check(methodInfo, NotNull.class, typeContext.notNull.get());
                check(methodInfo, Fluent.class, typeContext.fluent.get());
                check(methodInfo, Identity.class, typeContext.identity.get());
                check(methodInfo, E1Immutable.class, typeContext.e1Immutable.get());
                check(methodInfo, E1Container.class, typeContext.e1Container.get());
                check(methodInfo, Container.class, typeContext.container.get());
                check(methodInfo, E2Immutable.class, typeContext.e2Immutable.get());
                check(methodInfo, E2Container.class, typeContext.e2Container.get());
                CheckConstant.checkConstantForMethods(typeContext, methodInfo);
            }


            // opposites
            check(methodInfo, Dependent.class, typeContext.dependent.get());
            check(methodInfo, Nullable.class, typeContext.nullable.get());
        }
        check(methodInfo, Modified.class, typeContext.modified.get());

        CheckSize.checkSizeForMethods(typeContext, methodInfo);
        CheckPrecondition.checkPrecondition(typeContext, methodInfo);
        CheckOnly.checkOnly(typeContext, methodInfo);
        CheckOnly.checkMark(typeContext, methodInfo);

        methodInfo.methodInspection.get().parameters.forEach(parameterAnalyser::check);
    }

    private void check(MethodInfo methodInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(methodInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            typeContext.addMessage(error);
        });
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

        if (!methodInfo.methodAnalysis.get().numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            CreateNumberedStatements.recursivelyCreateNumberedStatements(null, statements, indices, numberedStatements,
                    new SideEffectContext(methodInfo));
            methodInfo.methodAnalysis.get().numberedStatements.set(ImmutableList.copyOf(numberedStatements));
            changes = true;
        }
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            methodProperties.createLocalVariableOrParameter(parameterInfo);
        }
        if (analyseFlow(methodInfo, methodProperties)) changes = true;
        return changes;
    }

    private boolean analyseFlow(MethodInfo methodInfo, VariableProperties methodProperties) {
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeLinking.computeVariablePropertiesOfMethod(numberedStatements, methodInfo, methodProperties))
                changes = true;
            if (methodIsIndependent(methodInfo, methodAnalysis)) changes = true;
            if (StaticModifier.computeStaticMethodCallsOnly(methodInfo, methodAnalysis, numberedStatements))
                changes = true;

            if (!methodInfo.isConstructor) {
                if (!methodAnalysis.returnStatementSummaries.isEmpty()) {
                    if (propertiesOfReturnStatements(methodInfo, methodAnalysis))
                        changes = true;
                    // methodIsConstant makes use of methodIsNotNull, so order is important
                    if (methodIsConstant(methodInfo, methodAnalysis, methodProperties)) changes = true;
                }
                if (methodInfo.isStatic) {
                    if (methodCreatesObjectOfSelf(numberedStatements, methodInfo, methodAnalysis)) changes = true;
                }
                StaticModifier.detectMissingStaticModifier(typeContext, methodInfo, methodAnalysis);
            }
            // even though all constructors should be modifying...
            if (methodIsNotModified(methodInfo, methodAnalysis)) changes = true;

            // @Only, @Mark comes after modifications
            if (computeOnlyMarkPrepWork(methodInfo, methodAnalysis)) changes = true;
            if (computeOnlyMarkAnnotate(methodInfo, methodAnalysis)) changes = true;

            // size comes after modifications
            if (computeSize(methodInfo, methodAnalysis)) changes = true;
            if (computeSizeCopy(methodInfo, methodAnalysis)) changes = true;

            if (parameterAnalyser.analyse(methodProperties)) changes = true;
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    private boolean computeOnlyMarkAnnotate(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.onlyData.isSet()) return false; // done
        if (!methodAnalysis.preconditionForOnlyData.isSet()) return false;
        Value precondition = methodAnalysis.preconditionForOnlyData.get();
        if (precondition == UnknownValue.NO_VALUE) return false;
        SetOnceMap<Value, String> approvedPreconditions = methodInfo.typeInfo.typeAnalysis.get().approvedPreconditions;
        boolean after;
        String markLabel;
        if (approvedPreconditions.isSet(precondition)) {
            after = false;
            markLabel = approvedPreconditions.get(precondition);
        } else {
            Value negated = NegatedValue.negate(precondition);
            if (approvedPreconditions.isSet(negated)) {
                after = true;
                markLabel = approvedPreconditions.get(negated);
            } else {
                log(MARK, "No approved preconditions for {} in {}", precondition, methodInfo.distinguishingName());
                if (!methodAnalysis.annotations.isSet(typeContext.mark.get())) {
                    methodAnalysis.annotations.put(typeContext.mark.get(), false);
                }
                if (!methodAnalysis.annotations.isSet(typeContext.only.get())) {
                    methodAnalysis.annotations.put(typeContext.only.get(), false);
                }
                return false;
            }
        }
        boolean mark;
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return false;
        }
        if (modified == Level.FALSE) {
            log(MARK, "Method {} is @NotModified, so it'll be @Only rather than @Mark", methodInfo.distinguishingName());
            mark = false;
        } else {
            // modifying method. we need to find a statement that reverses the precondition, or, at least, modifies one of the fields
            Set<Variable> variables = precondition.variables();
            mark = variables.stream().anyMatch(variable -> {
                TransferValue tv = methodAnalysis.fieldSummaries.get(((FieldReference) variable).fieldInfo);
                boolean assigned = tv.properties.get(VariableProperty.ASSIGNED) >= Level.TRUE;
                log(MARK, "Field {} is assigned in {}? {}", variable.name(), methodInfo.distinguishingName(), assigned);
                return assigned;
            });
        }
        MethodAnalysis.OnlyData onlyData = new MethodAnalysis.OnlyData(precondition, markLabel, mark, after);
        methodAnalysis.onlyData.set(onlyData);
        log(MARK, "Marking {} with only data {}", methodInfo.distinguishingName(), onlyData);
        if (mark) {
            AnnotationExpression markAnnotation = AnnotationExpression.fromAnalyserExpressions(typeContext.mark.get().typeInfo,
                    List.of(new MemberValuePair("value", new StringConstant(markLabel))));
            methodAnalysis.annotations.put(markAnnotation, true);
            methodAnalysis.annotations.put(typeContext.only.get(), false);
        } else {
            AnnotationExpression onlyAnnotation = AnnotationExpression.fromAnalyserExpressions(typeContext.only.get().typeInfo,
                    List.of(new MemberValuePair(after ? "after" : "before", new StringConstant(markLabel))));
            methodAnalysis.annotations.put(onlyAnnotation, true);
            methodAnalysis.annotations.put(typeContext.mark.get(), false);
        }
        return true;
    }

    private boolean computeOnlyMarkPrepWork(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.preconditionForOnlyData.isSet()) return false;
        boolean allFieldsFinalValueKnown = methodInfo.typeInfo.typeInspection.get().fields.stream().allMatch(field ->
                field.fieldAnalysis.get().getProperty(VariableProperty.FINAL) != Level.DELAY);
        if (!allFieldsFinalValueKnown) {
            log(DELAYED, "Delaying compute @Only and @Mark, not all field's final state known in {}", methodInfo.distinguishingName());
            return false;
        }
        if (!methodAnalysis.precondition.isSet()) {
            methodAnalysis.preconditionForOnlyData.set(UnknownValue.NO_VALUE);
            return true;
        }
        Value precondition = methodAnalysis.precondition.get();
        Set<Variable> variables = precondition.variables();
        boolean allVariablesAreFieldsOfMyOwnType = variables.stream().allMatch(v -> v instanceof FieldReference && ((FieldReference) v).scope instanceof This
                && ((This) ((FieldReference) v).scope).typeInfo == methodInfo.typeInfo);
        if (!allVariablesAreFieldsOfMyOwnType || variables.isEmpty()) {
            log(MARK, "No @Mark annotation in {}: not all variables are fields of my type, or there are no variables in the precondition", methodInfo.distinguishingName());
            methodAnalysis.preconditionForOnlyData.set(UnknownValue.NO_VALUE);
            return true;
        }
        log(MARK, "Did prep work for @Only, @Mark, found precondition {} on variables {} in {}", precondition, variables, methodInfo.distinguishingName());
        methodAnalysis.preconditionForOnlyData.set(precondition);
        return true;
    }

    // part of @UtilityClass computation in the type analyser
    private boolean methodCreatesObjectOfSelf(List<NumberedStatement> numberedStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.createObjectOfSelf.isSet()) {
            boolean createSelf = numberedStatements.stream().flatMap(ns -> Statement.findExpressionRecursivelyInStatements(ns.statement, NewObject.class))
                    .anyMatch(no -> no.parameterizedType.typeInfo == methodInfo.typeInfo);
            log(UTILITY_CLASS, "Is {} a static non-constructor method that creates self? {}", methodInfo.fullyQualifiedName(), createSelf);
            methodAnalysis.createObjectOfSelf.set(createSelf);
            return true;
        }
        return false;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private boolean methodIsConstant(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (methodAnalysis.singleReturnValue.isSet()) return false;

        boolean allReturnValuesSet = methodAnalysis.returnStatementSummaries.stream().allMatch(e -> e.getValue().value.isSet());
        if (!allReturnValuesSet) {
            log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
            return false;
        }
        List<TransferValue> remainingReturnStatementSummaries;
        if (methodAnalysis.returnStatementSummaries.size() > 1) {
            remainingReturnStatementSummaries = applyJoinReturnStatementPatterns(methodInfo, methodAnalysis, methodProperties);
            if (remainingReturnStatementSummaries.isEmpty()) {
                log(DELAYED, "Not everything known for pattern analysis in {}, delaying", methodInfo.distinguishingName());
                return false;
            }
        } else {
            remainingReturnStatementSummaries = methodAnalysis.returnStatementSummaries.stream().map(Map.Entry::getValue).collect(Collectors.toList());
        }

        Value value = null;
        if (remainingReturnStatementSummaries.size() == 1) {
            Value single = remainingReturnStatementSummaries.get(0).value.get();
            methodAnalysis.setReturnedObjectFlow(single.getObjectFlow());
            if (single.isConstant()) {
                value = single;
            } else if (methodInfo.isStatic && single.isExpressionOfParameters()) {
                value = new InlineValue(methodInfo, single);
            }
        }
        // fallback
        if (value == null) {
            value = UnknownValue.RETURN_VALUE;
        }
        boolean isConstant = value.isConstant();

        methodAnalysis.singleReturnValue.set(value);
        if (isConstant) {
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
            methodAnalysis.annotations.put(constantAnnotation, true);
        } else {
            methodAnalysis.annotations.put(typeContext.constant.get(), false);
        }
        methodAnalysis.setProperty(VariableProperty.CONSTANT, isConstant);

        log(CONSTANT, "Mark method {} as " + (isConstant ? "" : "NOT ") + "@Constant", methodInfo.fullyQualifiedName());
        return true;
    }

    @NotNull
    private List<TransferValue> applyJoinReturnStatementPatterns(MethodInfo methodInfo, MethodAnalysis methodAnalysis, EvaluationContext evaluationContext) {
        JoinReturnStatements joinReturnStatements = new JoinReturnStatements(evaluationContext);
        List<NumberedStatement> topLevelStatements = methodAnalysis.numberedStatements.get().stream()
                .filter(ns -> ns.indices.length == 1).collect(Collectors.toList());
        JoinReturnStatements.JoinResult result = joinReturnStatements.joinReturnStatementsInIfThenElse(topLevelStatements);
        if (result != null) {
            log(PATTERN, "Successfully applied JoinReturnStatementsInIfThenElse in {}", methodInfo.distinguishingName());
            return applyJoinResult(methodAnalysis, result);
        }
        JoinReturnStatements.JoinResult result2 = joinReturnStatements.joinReturnStatements(topLevelStatements);
        if (result2 != null) {
            log(PATTERN, "Successfully applied JoinReturnStatements in {}", methodInfo.distinguishingName());
            return applyJoinResult(methodAnalysis, result2);
        }
        return methodAnalysis.returnStatementSummaries.stream().map(Map.Entry::getValue).collect(Collectors.toList());
    }

    private List<TransferValue> applyJoinResult(MethodAnalysis methodAnalysis, JoinReturnStatements.JoinResult result) {
        if (result.value == UnknownValue.NO_VALUE) return List.of(); // DELAY
        List<TransferValue> old = methodAnalysis.returnStatementSummaries.stream()
                .filter(e -> !result.statementIdsReduced.contains(e.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.toList());
        TransferValue transferValue = new TransferValue();
        transferValue.value.set(result.value);
        return ListUtil.immutableConcat(old, List.of(transferValue));
    }

    private boolean propertiesOfReturnStatements(MethodInfo methodInfo,
                                                 MethodAnalysis methodAnalysis) {
        boolean changes = false;
        for (VariableProperty variableProperty : VariableProperty.RETURN_VALUE_PROPERTIES_IN_METHOD_ANALYSER) {
            if (propertyOfReturnStatements(variableProperty, methodInfo, methodAnalysis))
                changes = true;
        }
        return changes;
    }

    private boolean propertyOfReturnStatements(VariableProperty variableProperty,
                                               MethodInfo methodInfo,
                                               MethodAnalysis methodAnalysis) {
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (currentValue != Level.DELAY) return false;

        boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry ->
                entry.getValue().properties.getOtherwise(variableProperty, Level.DELAY) == Level.DELAY);
        if (delays) {
            log(DELAYED, "Return statement value not yet set");
            return false;
        }
        IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                .mapToInt(entry -> entry.getValue().properties.getOtherwise(variableProperty, Level.DELAY));
        int value = variableProperty == VariableProperty.SIZE ?
                safeMinimum(typeContext, new Location(methodInfo), stream) :
                stream.min().orElse(Level.DELAY);

        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return true;
    }

    private boolean computeSize(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        int current = methodAnalysis.getProperty(VariableProperty.SIZE);
        if (current != Level.DELAY) return false;

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} because waiting for @Modified", methodInfo.distinguishingName());
            return false;
        }
        if (modified == Level.FALSE) {
            if (methodInfo.isConstructor) return false; // non-modifying constructor would be weird anyway
            if (methodInfo.returnType().hasSize()) {
                // non-modifying method that returns a type with @Size (like Collection, Map, ...)

                // then try @Size(min, equals)
                boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry ->
                        entry.getValue().properties.getOtherwise(VariableProperty.SIZE, Level.DELAY) == Level.DELAY);
                if (delays) {
                    log(DELAYED, "Return statement value not yet set for @Size on {}", methodInfo.distinguishingName());
                    return false;
                }
                IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                        .mapToInt(entry -> entry.getValue().properties.getOtherwise(VariableProperty.SIZE, Level.DELAY));
                return writeSize(methodInfo, methodAnalysis, VariableProperty.SIZE, safeMinimum(typeContext, new Location(methodInfo), stream));
            }

            // non-modifying method that defines @Size (size(), isEmpty())
            return writeSize(methodInfo, methodAnalysis, VariableProperty.SIZE, propagateSizeAnnotations(methodInfo, methodAnalysis));
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        if (methodInfo.typeInfo.hasSize()) {
            return sizeModifying(methodInfo, methodAnalysis);

        }
        return false;
    }


    private boolean computeSizeCopy(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        int current = methodAnalysis.getProperty(VariableProperty.SIZE_COPY);
        if (current != Level.DELAY) return false;

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size(copy) on {} because waiting for @Modified", methodInfo.distinguishingName());
            return false;
        }
        if (modified == Level.FALSE) {
            if (methodInfo.isConstructor) return false; // non-modifying constructor would be weird anyway
            if (methodInfo.returnType().hasSize()) {
                // first try @Size(copy ...)
                boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry ->
                        entry.getValue().properties.getOtherwise(VariableProperty.SIZE_COPY, Level.DELAY) == Level.DELAY);
                if (delays) {
                    log(DELAYED, "Return statement value not yet set for SIZE_COPY on {}", methodInfo.distinguishingName());
                    return false;
                }
                IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                        .mapToInt(entry -> entry.getValue().properties.getOtherwise(VariableProperty.SIZE_COPY, Level.DELAY));
                int min = stream.min().orElse(Level.DELAY);
                return writeSize(methodInfo, methodAnalysis, VariableProperty.SIZE_COPY, min);
            }
            return false;
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        if (methodInfo.typeInfo.hasSize()) {
            // TODO not implemented yet
        }
        return false;
    }

    // there is a modification that alters the @Size of this type (e.g. put() will cause a @Size(min = 1))
    private boolean sizeModifying(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        return false; // TODO NYI
    }

    private boolean writeSize(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperty variableProperty, int value) {
        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return true;
    }

    private int propagateSizeAnnotations(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.returnStatementSummaries.size() != 1) {
            return Level.DELAY;
        }
        TransferValue tv = methodAnalysis.returnStatementSummaries.stream().findFirst().orElseThrow().getValue();
        if (!tv.value.isSet()) {
            return Level.DELAY;
        }
        Value value = tv.value.get();
        if (methodInfo.returnType().isDiscrete() && value instanceof ConstrainedNumericValue) {
            // very specific situation, we see if the return statement is a @Size method; if so, we propagate that info
            ConstrainedNumericValue cnv = (ConstrainedNumericValue) value;
            if (cnv.value instanceof MethodValue) {
                MethodValue methodValue = (MethodValue) cnv.value;
                MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod();
                if (methodValue.methodInfo == theSizeMethod && cnv.lowerBound >= 0 && cnv.upperBound == ConstrainedNumericValue.MAX) {
                    return Analysis.encodeSizeMin((int) cnv.lowerBound);
                }
            }
        } else if (methodInfo.returnType().isBoolean()) {
            // very specific situation, we see if the return statement is a predicate on a @Size method; if so we propagate that info
            // size restrictions are ALWAYS int == size() or -int + size() >= 0
            if (value instanceof EqualsValue) {
                EqualsValue equalsValue = (EqualsValue) value;
                if (equalsValue.lhs instanceof IntValue && equalsValue.rhs instanceof ConstrainedNumericValue) {
                    int i = ((IntValue) equalsValue.lhs).value;
                    ConstrainedNumericValue cnv = (ConstrainedNumericValue) equalsValue.rhs;
                    if (cnv.value instanceof MethodValue) {
                        MethodValue methodValue = (MethodValue) cnv.value;
                        MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod();
                        if (methodValue.methodInfo == theSizeMethod) {
                            return Analysis.encodeSizeEquals(i);
                        }
                    }
                }
            } else if (value instanceof GreaterThanZeroValue) {
                GreaterThanZeroValue.XB xb = ((GreaterThanZeroValue) value).extract();
                if (!xb.lessThan && xb.x instanceof ConstrainedNumericValue) {
                    ConstrainedNumericValue cnv = (ConstrainedNumericValue) xb.x;
                    if (cnv.value instanceof MethodValue) {
                        MethodValue methodValue = (MethodValue) cnv.value;
                        MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod();
                        if (methodValue.methodInfo == theSizeMethod) {
                            return Analysis.encodeSizeMin((int) xb.b);
                        }
                    }
                }
            }
        }
        return Level.DELAY;
    }

    static int safeMinimum(TypeContext typeContext, Location location, IntStream intStream) {
        int res = intStream.reduce(Integer.MAX_VALUE, (v1, v2) -> {
            if (Analysis.haveEquals(v1) && Analysis.haveEquals(v2) && v1 != v2) {
                typeContext.addMessage(Message.newMessage(location, Message.POTENTIAL_SIZE_PROBLEM,
                        "Equal to " + Analysis.decodeSizeEquals(v1) + ", equal to " + Analysis.decodeSizeEquals(v2)));
            }
            return Math.min(v1, v2);
        });
        return res == Integer.MAX_VALUE ? Level.DELAY : res;
    }

    private boolean methodIsNotModified(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.getProperty(VariableProperty.MODIFIED) != Level.DELAY) return false;

        // first step, check field assignments
        boolean fieldAssignments = methodAnalysis.fieldSummaries.stream()
                .map(Map.Entry::getValue)
                .anyMatch(tv -> tv.properties.getOtherwise(VariableProperty.ASSIGNED, Level.DELAY) >= Level.TRUE);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} cannot be @NotModified/is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            return true;
        }
        // second step, check that fields modifications
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Method {}: Not deciding on @Modified yet, delaying because linking not computed",
                    methodInfo.distinguishingName());
            return false;
        }
        boolean isModified = methodAnalysis.fieldSummaries.stream().map(Map.Entry::getValue)
                .anyMatch(tv -> tv.properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY) == Level.TRUE);
        if (isModified && isLogEnabled(NOT_MODIFIED)) {
            List<String> fieldsWithContentModifications =
                    methodAnalysis.fieldSummaries.stream()
                            .filter(e -> e.getValue().properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY) == Level.TRUE)
                            .map(e -> e.getKey().fullyQualifiedName()).collect(Collectors.toList());
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (!isModified) {
            boolean localMethodsCalled = methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.METHOD_CALLED, Level.DELAY) == Level.TRUE;
            if (localMethodsCalled) {
                int thisModified = methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY);
                int methodDelay = methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.METHOD_DELAY, Level.FALSE);

                if (thisModified == Level.DELAY && methodDelay == Level.TRUE) {
                    log(DELAYED, "In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                            methodInfo.distinguishingName());
                    return false;
                }
                isModified = thisModified == Level.TRUE;
                log(NOT_MODIFIED, "Mark method {} as {}", methodInfo.distinguishingName(),
                        isModified ? "@Modified" : "@NotModified");
            }
        } // else: already true, so no need to look at this
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(VariableProperty.MODIFIED, isModified);
        return true;
    }

    private boolean methodIsIndependent(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.getProperty(VariableProperty.INDEPENDENT) != Level.DELAY) return false;
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, links not computed", methodInfo.fullyQualifiedName());
            return false;
        }

        // PART 1: check the return object, if it is there

        boolean returnObjectIsIndependent;
        if (!methodInfo.isConstructor && !methodInfo.returnType().isVoid()) {
            if (!methodAnalysis.variablesLinkedToMethodResult.isSet()) {
                log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                        methodInfo.fullyQualifiedName());
                return false;
            }
            Set<Variable> variables = methodAnalysis.variablesLinkedToMethodResult.get();
            int e2ImmutableStatusOfFieldRefs = variables.stream()
                    .filter(v -> v instanceof FieldReference)
                    .mapToInt(v -> Level.value(((FieldReference) v).fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE))
                    .min().orElse(Level.TRUE);
            if (e2ImmutableStatusOfFieldRefs == Level.DELAY) {
                log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                        variables.stream()
                                .filter(v -> v instanceof FieldReference &&
                                        Level.value(((FieldReference) v).fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
                                                Level.E2IMMUTABLE) == Level.DELAY)
                                .map(Variable::detailedString)
                                .collect(Collectors.joining(", ")));
                return false;
            }
            returnObjectIsIndependent = e2ImmutableStatusOfFieldRefs == Level.TRUE;
        } else {
            returnObjectIsIndependent = true;
        }

        // PART 2: check parameters
        List<ParameterInfo> parameters = methodInfo.methodInspection.get().parameters;
        boolean parametersIndependentOfFields = methodAnalysis.fieldSummaries.stream()
                .peek(e -> {
                    if (!e.getValue().linkedVariables.isSet())
                        LOGGER.warn("Field {} has no linked variables set in {}", e.getKey().name, methodInfo.distinguishingName());
                })
                .map(e -> e.getValue().linkedVariables.get())
                .allMatch(set -> Collections.disjoint(set, parameters));

        // conclusion

        boolean independent = parametersIndependentOfFields && returnObjectIsIndependent;
        methodAnalysis.setProperty(VariableProperty.INDEPENDENT, independent);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return true;
    }
}
