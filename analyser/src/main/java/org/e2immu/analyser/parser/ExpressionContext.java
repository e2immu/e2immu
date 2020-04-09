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

package org.e2immu.analyser.parser;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.UnionType;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.parser.expr.*;
import org.e2immu.analyser.util.Pair;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CONTEXT;
import static org.e2immu.analyser.util.Logger.log;

// cannot even be a @Container, since the VariableContext passed on to us gets modified along the way
public class ExpressionContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionContext.class);

    public final TypeContext typeContext;
    public final TypeInfo enclosingType;
    public final TypeInfo primaryType;
    public final VariableContext variableContext; // gets modified! so this class cannot even be a container...
    public final TopLevel topLevel;
    public final Set<TypeInfo> dependenciesOnOtherTypes;
    public final Set<WithInspectionAndAnalysis> dependenciesOnOtherMethodsAndFields;

    public static class TopLevel {
        final Map<TypeInfo, AtomicInteger> anonymousClassCounter = new HashMap<>();

        public int newIndex(TypeInfo enclosingType) {
            return anonymousClassCounter.computeIfAbsent(enclosingType, t -> new AtomicInteger()).incrementAndGet();
        }
    }

    public static ExpressionContext forInspectionOfPrimaryType(@NullNotAllowed @NotModified TypeInfo typeInfo,
                                                               @NullNotAllowed @NotModified TypeContext typeContext) {
        log(CONTEXT, "Creating a new expression context for {}", typeInfo.fullyQualifiedName);
        return new ExpressionContext(Objects.requireNonNull(typeInfo), typeInfo,
                Objects.requireNonNull(typeContext),
                VariableContext.initialVariableContext(new HashMap<>()),
                new TopLevel(),
                new HashSet<>(),
                null);
    }

    public static ExpressionContext forBodyParsing(@NullNotAllowed @NotModified TypeInfo enclosingType,
                                                   @NullNotAllowed @NotModified TypeInfo primaryType,
                                                   @NullNotAllowed @NotModified TypeContext typeContext,
                                                   @NullNotAllowed Set<TypeInfo> dependenciesOnOtherTypes) {
        Map<String, FieldReference> staticallyImportedFields = typeContext.staticFieldImports();
        log(CONTEXT, "Creating a new expression context for {}", enclosingType.fullyQualifiedName);
        return new ExpressionContext(Objects.requireNonNull(enclosingType),
                Objects.requireNonNull(primaryType),
                Objects.requireNonNull(typeContext),
                VariableContext.initialVariableContext(staticallyImportedFields),
                new TopLevel(),
                Objects.requireNonNull(dependenciesOnOtherTypes),
                null);
    }

    private ExpressionContext(TypeInfo enclosingType,
                              TypeInfo primaryType,
                              TypeContext typeContext,
                              VariableContext variableContext,
                              TopLevel topLevel,
                              Set<TypeInfo> dependenciesOnOtherTypes,
                              Set<WithInspectionAndAnalysis> dependenciesOnOtherMethods) {
        this.typeContext = typeContext;
        this.primaryType = primaryType;
        this.enclosingType = enclosingType;
        this.topLevel = topLevel;
        this.variableContext = variableContext;
        this.dependenciesOnOtherTypes = dependenciesOnOtherTypes;
        this.dependenciesOnOtherMethodsAndFields = dependenciesOnOtherMethods;
    }

    public ExpressionContext newVariableContext(String reason) {
        log(CONTEXT, "Creating a new variable context for {}", reason);
        return new ExpressionContext(enclosingType, primaryType,
                typeContext, VariableContext.dependentVariableContext(variableContext),
                topLevel, dependenciesOnOtherTypes, dependenciesOnOtherMethodsAndFields);
    }

    public ExpressionContext newVariableContext(@NullNotAllowed VariableContext newVariableContext, String reason) {
        log(CONTEXT, "Creating a new variable context for {}", reason);
        return new ExpressionContext(enclosingType, primaryType, typeContext,
                newVariableContext, topLevel, dependenciesOnOtherTypes, dependenciesOnOtherMethodsAndFields);
    }

    public ExpressionContext newSubType(TypeInfo subType) {
        log(CONTEXT, "Creating a new type context for subtype {}", subType.simpleName);
        return new ExpressionContext(subType, primaryType,
                new TypeContext(typeContext), variableContext, topLevel, dependenciesOnOtherTypes, new HashSet<>());
    }

    public ExpressionContext newTypeContext(String reason) {
        log(CONTEXT, "Creating a new type context for {}", reason);
        return new ExpressionContext(enclosingType, primaryType,
                new TypeContext(typeContext), variableContext, topLevel, dependenciesOnOtherTypes, new HashSet<>());
    }

    // method makes changes to variableContext
    @NotNull
    public Block parseBlockOrStatement(@NullNotAllowed @NotModified Statement stmt) {
        Block.BlockBuilder blockBuilder = new Block.BlockBuilder();
        if (stmt.isBlockStmt()) {
            for (Statement statement : stmt.asBlockStmt().getStatements()) {
                parseStatement(blockBuilder, statement);
            }
        } else {
            parseStatement(blockBuilder, stmt);
        }
        return blockBuilder.build();
    }

    // method modifies the blockBuilder...
    private void parseStatement(@NullNotAllowed Block.BlockBuilder blockBuilder, @NullNotAllowed @NotModified Statement statement) {
        try {
            org.e2immu.analyser.model.Statement newStatement;
            if (statement.isReturnStmt()) {
                newStatement = new ReturnStatement(parseExpression(((ReturnStmt) statement).getExpression()));
            } else if (statement.isExpressionStmt()) {
                newStatement = new ExpressionAsStatement(parseExpression(((ExpressionStmt) statement).getExpression()));
            } else if (statement.isForEachStmt()) {
                newStatement = forEachStatement((ForEachStmt) statement);
            } else if (statement.isWhileStmt()) {
                newStatement = whileStatement((WhileStmt) statement);
            } else if (statement.isBlockStmt()) {
                newStatement = newVariableContext("block").parseBlockOrStatement(statement);
            } else if (statement.isIfStmt()) {
                newStatement = ifThenElseStatement((IfStmt) statement);
            } else if (statement.isSynchronizedStmt()) {
                newStatement = synchronizedStatement((SynchronizedStmt) statement);
            } else if (statement.isThrowStmt()) {
                newStatement = new ThrowStatement(parseExpression(((ThrowStmt) statement).getExpression()));
            } else if (statement.isLocalClassDeclarationStmt()) {
                newStatement = localClassDeclaration((LocalClassDeclarationStmt) statement);
            } else if (statement.isExplicitConstructorInvocationStmt()) {
                // this( ... )
                newStatement = explicitConstructorInvocation((ExplicitConstructorInvocationStmt) statement);
            } else if (statement.isTryStmt()) {
                newStatement = tryStatement(statement.asTryStmt());
            } else if (statement.isContinueStmt()) {
                newStatement = new ContinueStatement();
            } else if (statement.isBreakStmt()) {
                newStatement = new BreakStatement();
            } else if (statement.isDoStmt()) {
                newStatement = doStatement(statement.asDoStmt());
            } else if (statement.isForStmt()) {
                newStatement = forStatement(statement.asForStmt());
            } else if (statement.isAssertStmt()) {
                newStatement = assertStatement(statement.asAssertStmt());
            } else if (statement.isEmptyStmt()) {
                newStatement = EmptyStatement.EMPTY_STATEMENT;
            } else if (statement.isSwitchStmt()) {
                newStatement = switchStatement(statement.asSwitchStmt());
            } else if (statement.isUnparsableStmt()) {
                LOGGER.warn("Skipping unparsable statement at {}", statement.getBegin());
                newStatement = null;
            } else if (statement.isLabeledStmt()) {
                LabeledStmt labeledStmt = statement.asLabeledStmt();
                String label = labeledStmt.getLabel().asString();
                Block.BlockBuilder labelStatementBuilder = new Block.BlockBuilder();
                parseStatement(labelStatementBuilder, labeledStmt.getStatement());
                org.e2immu.analyser.model.Statement inLabeled = blockBuilder.build().statements.get(0);
                newStatement = new LabeledStatement(label, inLabeled);
            } else {
                throw new UnsupportedOperationException("Statement " + statement.getClass() + " not implemented");
            }

            if (newStatement != null) {
                List<LocalVariableReference> newLocalVariables = newStatement.newLocalVariables();
                if (newLocalVariables == null)
                    throw new NullPointerException("Statement of " + newStatement.getClass() + " produces null local vars");
                variableContext.addAll(newLocalVariables);
                blockBuilder.addStatement(newStatement);
            }
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while parsing statement at line {}", statement.getBegin().orElse(null));
            throw rte;
        }
    }

    private org.e2immu.analyser.model.Statement switchStatement(SwitchStmt switchStmt) {
        Expression selector = parseExpression(switchStmt.getSelector());
        ExpressionContext newExpressionContext;
        TypeInfo enumType = selectorIsEnumType(selector);
        if (enumType != null) {
            newExpressionContext = newVariableContext("switch-statement");
            Variable scope = new This(enumType);
            enumType.typeInspection.get().fields.forEach(fieldInfo -> newExpressionContext.variableContext.add(new FieldReference(fieldInfo, scope)));
        } else {
            newExpressionContext = this;
        }
        List<SwitchEntry> entries = switchStmt.getEntries().stream().map(newExpressionContext::switchEntry).collect(Collectors.toList());
        return new SwitchStatement(selector, entries);
    }

    private TypeInfo selectorIsEnumType(Expression selector) {
        TypeInfo typeInfo = selector.returnType().typeInfo;
        if (typeInfo != null && typeInfo.typeInspection.get().typeNature == TypeNature.ENUM) {
            return typeInfo;
        }
        return null;
    }

    private SwitchEntry switchEntry(com.github.javaparser.ast.stmt.SwitchEntry switchEntry) {
        List<Expression> labels = switchEntry.getLabels().stream().map(this::parseExpression).collect(Collectors.toList());
        switch (switchEntry.getType()) {
            case EXPRESSION:
            case THROWS_STATEMENT:
            case STATEMENT_GROUP:
                Block.BlockBuilder blockBuilder = new Block.BlockBuilder();
                for (Statement statement : switchEntry.getStatements()) {
                    parseStatement(blockBuilder, statement);
                }
                boolean java12Style = switchEntry.getType() != com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP;
                return new SwitchEntry.StatementsEntry(java12Style, labels, blockBuilder.build().statements);
            case BLOCK:
                Block block = parseBlockOrStatement(switchEntry.getStatements().get(0));
                return new SwitchEntry.BlockEntry(labels, block);
            default:
                throw new UnsupportedOperationException("Unknown type " + switchEntry.getType());
        }
    }

    private org.e2immu.analyser.model.Statement forStatement(ForStmt forStmt) {
        List<Expression> initialisers = forStmt.getInitialization().stream().map(this::parseExpression).collect(Collectors.toList());
        ExpressionContext newExpressionContext = newVariableContext("for-loop");
        for (Expression initialiser : initialisers) {
            List<LocalVariableReference> newLocalVariables = initialiser.newLocalVariables();
            if (newLocalVariables == null)
                throw new NullPointerException("Statement of " + initialiser.getClass() + " produces null local vars");
            newExpressionContext.variableContext.addAll(newLocalVariables);
        }
        Expression condition = forStmt.getCompare().map(newExpressionContext::parseExpression).orElse(EmptyExpression.EMPTY_EXPRESSION);
        List<Expression> updaters = forStmt.getUpdate().stream().map(newExpressionContext::parseExpression).collect(Collectors.toList());
        Block block = newExpressionContext.parseBlockOrStatement(forStmt.getBody());
        return new ForStatement(initialisers, condition, updaters, block);
    }

    private org.e2immu.analyser.model.Statement assertStatement(AssertStmt assertStmt) {
        Expression check = parseExpression(assertStmt.getCheck());
        Expression message = assertStmt.getMessage().map(this::parseExpression).orElse(null);
        return new AssertStatement(check, message);
    }

    private org.e2immu.analyser.model.Statement tryStatement(TryStmt tryStmt) {
        List<Expression> resources = new ArrayList<>();
        ExpressionContext newExpressionContext = newVariableContext("try-resources");
        for (com.github.javaparser.ast.expr.Expression resource : tryStmt.getResources()) {
            LocalVariableCreation localVariableCreation = (LocalVariableCreation) newExpressionContext.parseExpression(resource);
            newExpressionContext.variableContext.add(localVariableCreation.localVariable, List.of(localVariableCreation.expression));
            resources.add(localVariableCreation);
        }
        Block tryBlock = newExpressionContext.parseBlockOrStatement(tryStmt.getTryBlock());
        List<Pair<TryStatement.CatchParameter, Block>> catchClauses = new ArrayList<>();
        for (CatchClause catchClause : tryStmt.getCatchClauses()) {
            Parameter parameter = catchClause.getParameter();
            List<ParameterizedType> unionOfTypes;
            ParameterizedType typeOfVariable;
            if (parameter.getType().isUnionType()) {
                UnionType unionType = parameter.getType().asUnionType();
                unionOfTypes = unionType.getElements()
                        .stream()
                        .map(rt -> ParameterizedType.from(newExpressionContext.typeContext, rt)).collect(Collectors.toList());
                typeOfVariable = typeContext.typeStore.get("java.lang.Exception").asParameterizedType();
            } else {
                typeOfVariable = ParameterizedType.from(newExpressionContext.typeContext, parameter.getType());
                unionOfTypes = List.of(typeOfVariable);
            }
            String name = parameter.getName().asString();
            LocalVariable localVariable = new LocalVariable.LocalVariableBuilder().setName(name).setParameterizedType(typeOfVariable).build();
            TryStatement.CatchParameter catchParameter = new TryStatement.CatchParameter(name, unionOfTypes);
            newExpressionContext.variableContext.add(localVariable, List.of());
            Block block = newExpressionContext.parseBlockOrStatement(catchClause.getBody());
            catchClauses.add(new Pair<>(catchParameter, block));
        }
        Block finallyBlock = tryStmt.getFinallyBlock().map(this::parseBlockOrStatement).orElse(Block.EMPTY_BLOCK);
        return new TryStatement(resources, tryBlock, catchClauses, finallyBlock);
    }

    private org.e2immu.analyser.model.Statement whileStatement(WhileStmt statement) {
        Block block = parseBlockOrStatement(statement.getBody());
        org.e2immu.analyser.model.Expression expression = parseExpression(statement.getCondition());
        return new WhileStatement(expression, block);
    }

    private org.e2immu.analyser.model.Statement doStatement(DoStmt statement) {
        Block block = parseBlockOrStatement(statement.getBody());
        org.e2immu.analyser.model.Expression expression = parseExpression(statement.getCondition());
        return new DoStatement(expression, block);
    }

    private org.e2immu.analyser.model.Statement explicitConstructorInvocation(ExplicitConstructorInvocationStmt statement) {
        List<org.e2immu.analyser.model.Expression> parameterExpressions = statement.getArguments()
                .stream().map(this::parseExpression).collect(Collectors.toList());
        return new ExplicitConstructorInvocation(parameterExpressions);
    }

    private org.e2immu.analyser.model.Statement localClassDeclaration(LocalClassDeclarationStmt statement) {
        String localName = statement.getClassDeclaration().getNameAsString();
        int index = topLevel.newIndex(enclosingType);
        String fullyQualifiedName = enclosingType.fullyQualifiedName + "." + index + "." + localName;
        TypeInfo typeInfo = new TypeInfo(localName);
        typeInfo.inspectLocalClassDeclaration(this, statement.getClassDeclaration());
        typeContext.addToContext(typeInfo);
        new Resolver().sortTypes(Map.of(typeInfo, typeContext));
        return new LocalClassDeclaration(typeInfo);
    }

    private org.e2immu.analyser.model.Statement synchronizedStatement(SynchronizedStmt statement) {
        Block block = parseBlockOrStatement(statement.getBody());
        org.e2immu.analyser.model.Expression expression = parseExpression(statement.getExpression());
        return new SynchronizedStatement(expression, block);
    }

    private org.e2immu.analyser.model.Statement forEachStatement(ForEachStmt forEachStmt) {
        VariableContext newVariableContext = VariableContext.dependentVariableContext(variableContext);
        VariableDeclarationExpr vde = forEachStmt.getVariable();
        LocalVariable localVariable = new LocalVariable.LocalVariableBuilder()
                .setName(vde.getVariables().get(0).getNameAsString())
                .setParameterizedType(ParameterizedType.from(typeContext, vde.getVariables().get(0).getType()))
                .build();
        org.e2immu.analyser.model.Expression expression = parseExpression(forEachStmt.getIterable());
        newVariableContext.add(localVariable, List.of(expression));
        Block block = newVariableContext(newVariableContext, "for-loop").parseBlockOrStatement(forEachStmt.getBody().asBlockStmt());
        return new ForEachStatement(localVariable, expression, block);
    }

    private org.e2immu.analyser.model.Statement ifThenElseStatement(IfStmt statement) {
        Block ifBlock = parseBlockOrStatement(statement.getThenStmt());
        org.e2immu.analyser.model.Expression conditional = parseExpression(statement.getCondition());
        Block elseBlock = statement.getElseStmt().map(this::parseBlockOrStatement).orElse(Block.EMPTY_BLOCK);
        return new IfElseStatement(conditional, ifBlock, elseBlock);
    }

    @NotNull
    public org.e2immu.analyser.model.Expression parseExpression(@NullNotAllowed Optional<com.github.javaparser.ast.expr.Expression> oExpression) {
        if (oExpression.isPresent()) {
            return parseExpression(oExpression.get());
        }
        return EmptyExpression.EMPTY_EXPRESSION;
    }

    @NotNull
    public org.e2immu.analyser.model.Expression parseExpression(@NullNotAllowed com.github.javaparser.ast.expr.Expression expression) {
        return parseExpression(expression, null);
    }

    public org.e2immu.analyser.model.Expression parseExpression(com.github.javaparser.ast.expr.Expression expression, MethodTypeParameterMap singleAbstractMethod) {
        try {
            if (expression.isStringLiteralExpr()) {
                StringLiteralExpr stringLiteralExpr = (StringLiteralExpr) expression;
                return new StringConstant(stringLiteralExpr.asString());
            }
            if (expression.isIntegerLiteralExpr()) {
                IntegerLiteralExpr integerLiteralExpr = (IntegerLiteralExpr) expression;
                return new IntConstant(integerLiteralExpr.asInt());
            }
            if (expression.isBooleanLiteralExpr()) {
                BooleanLiteralExpr booleanLiteralExpr = (BooleanLiteralExpr) expression;
                return new BooleanConstant(booleanLiteralExpr.getValue());
            }
            if (expression.isNullLiteralExpr()) {
                return NullConstant.nullConstant;
            }
            if (expression.isCastExpr()) {
                CastExpr castExpr = (CastExpr) expression;
                ParameterizedType parameterizedType = ParameterizedType.from(typeContext, castExpr.getType());
                dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
                return new Cast(parseExpression(castExpr.getExpression()), parameterizedType);
            }
            if (expression.isBinaryExpr()) {
                BinaryExpr binaryExpr = (BinaryExpr) expression;
                org.e2immu.analyser.model.Expression lhs = parseExpression(binaryExpr.getLeft());
                org.e2immu.analyser.model.Expression rhs = parseExpression(binaryExpr.getRight());
                TypeInfo typeInfo = null;
                if (lhs.returnType().isPrimitiveOrStringNotVoid() || rhs.returnType().isPrimitiveOrStringNotVoid()) {
                    ParameterizedType widestType = Primitives.PRIMITIVES.widestType(lhs.returnType(), rhs.returnType());
                    if (!widestType.isType())
                        throw new UnsupportedOperationException("? for " + lhs.returnType() + " and " + rhs.returnType());
                    typeInfo = widestType.typeInfo;
                }
                return new BinaryOperator(
                        lhs,
                        BinaryOperator.getOperator(binaryExpr.getOperator(), typeInfo),
                        rhs,
                        BinaryOperator.precedence(binaryExpr.getOperator()));
            }
            if (expression.isUnaryExpr()) {
                UnaryExpr unaryExpr = (UnaryExpr) expression;
                org.e2immu.analyser.model.Expression exp = parseExpression(unaryExpr.getExpression());
                ParameterizedType pt = exp.returnType();
                if (!pt.isType()) throw new UnsupportedOperationException("??");
                return new UnaryOperator(
                        UnaryOperator.getOperator(unaryExpr.getOperator(), pt.typeInfo),
                        exp,
                        UnaryOperator.precedence(unaryExpr.getOperator())
                );
            }
            if (expression.isThisExpr()) {
                Variable variable = new This(enclosingType);
                return new VariableExpression(variable);
            }
            if (expression.isSuperExpr()) {
                Variable variable = new This(enclosingType); // TODO
                return new VariableExpression(variable);
            }
            if (expression.isTypeExpr()) {
                // note that "System.out" is a type expression; ParameterizedType.from can handle this, but we'd rather see a field access
                TypeExpr typeExpr = (TypeExpr) expression;
                if (typeExpr.getType().isClassOrInterfaceType()) {
                    ClassOrInterfaceType cit = typeExpr.getType().asClassOrInterfaceType();
                    if (cit.getScope().isPresent()) {
                        // System.out, we'll return a field access!
                        ParameterizedType parameterizedType = ParameterizedType.from(typeContext, cit.getScope().get());
                        dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
                        TypeExpression typeExpression = new TypeExpression(parameterizedType);
                        return ParseFieldAccessExpr.createFieldAccess(this, typeExpression, cit.getNameAsString(), expression.getBegin().orElseThrow());
                    }
                }
                // there is a real possibility that the type expression is NOT a type but a local field...
                // therefore we check the variable context first
                Variable variable = variableContext.get(typeExpr.getTypeAsString(), false);
                if (variable != null) {
                    dependenciesOnOtherTypes.addAll(variable.parameterizedType().typeInfoSet());
                    return ParseNameExpr.fromVariableToExpression(this, variable);
                }
                ParameterizedType parameterizedType = ParameterizedType.from(typeContext, typeExpr.getType());
                dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
                return new TypeExpression(parameterizedType);
            }
            if (expression.isClassExpr()) {
                ClassExpr classExpr = (ClassExpr) expression;
                ParameterizedType parameterizedType = ParameterizedType.from(typeContext, classExpr.getType());
                dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
                return new ClassExpression(parameterizedType);
            }
            if (expression.isNameExpr()) {
                return ParseNameExpr.parse(this, expression.asNameExpr());
            }
            if (expression.isObjectCreationExpr()) {
                ObjectCreationExpr objectCreationExpr = (ObjectCreationExpr) expression;
                return ParseObjectCreationExpr.parse(this, objectCreationExpr, singleAbstractMethod);
            }
            if (expression.isVariableDeclarationExpr()) {
                VariableDeclarationExpr vde = (VariableDeclarationExpr) expression;
                VariableDeclarator var = vde.getVariable(0);
                ParameterizedType parameterizedType = ParameterizedType.from(typeContext, var.getType());
                dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
                LocalVariable.LocalVariableBuilder localVariable = new LocalVariable.LocalVariableBuilder()
                        .setName(var.getNameAsString())
                        .setParameterizedType(parameterizedType);
                vde.getAnnotations().forEach(ae -> localVariable.addAnnotation(AnnotationExpression.from(ae, this)));
                vde.getModifiers().forEach(m -> localVariable.addModifier(LocalVariableModifier.from(m)));
                org.e2immu.analyser.model.Expression initializer = var.getInitializer()
                        .map(i -> parseExpression(i, parameterizedType.findSingleAbstractMethodOfInterface(typeContext)))
                        .orElse(EmptyExpression.EMPTY_EXPRESSION);
                return new LocalVariableCreation(localVariable.build(), initializer);
            }
            if (expression.isAssignExpr()) {
                AssignExpr assignExpr = (AssignExpr) expression;
                org.e2immu.analyser.model.Expression target = parseExpression(assignExpr.getTarget());
                org.e2immu.analyser.model.Expression value = parseExpression(assignExpr.getValue(), target.returnType().findSingleAbstractMethodOfInterface(typeContext));
                if (value.returnType().isType() && value.returnType().typeInfo.isPrimitive() &&
                        target.returnType().isType() && target.returnType().typeInfo.isPrimitive()) {
                    ParameterizedType widestType = Primitives.PRIMITIVES.widestType(value.returnType(), target.returnType());
                    MethodInfo primitiveOperator = Assignment.operator(assignExpr.getOperator(), widestType.typeInfo);
                    return new Assignment(target, value, primitiveOperator);
                }
                return new Assignment(target, value);
            }
            if (expression.isMethodCallExpr()) {
                return ParseMethodCallExpr.parse(this, expression.asMethodCallExpr(), singleAbstractMethod);
            }
            if (expression.isMethodReferenceExpr()) {
                return ParseMethodReferenceExpr.parse(this, expression.asMethodReferenceExpr(), singleAbstractMethod);
            }
            if (expression.isConditionalExpr()) {
                ConditionalExpr conditionalExpr = (ConditionalExpr) expression;
                org.e2immu.analyser.model.Expression condition = parseExpression(conditionalExpr.getCondition());
                org.e2immu.analyser.model.Expression ifTrue = parseExpression(conditionalExpr.getThenExpr());
                org.e2immu.analyser.model.Expression ifFalse = parseExpression(conditionalExpr.getElseExpr());
                return new InlineConditionalOperator(condition, ifTrue, ifFalse);
            }
            if (expression.isFieldAccessExpr()) {
                return ParseFieldAccessExpr.parse(this, expression.asFieldAccessExpr());
            }
            if (expression.isLambdaExpr()) {
                return ParseLambdaExpr.parse(this, expression.asLambdaExpr(), singleAbstractMethod);
            }
            if (expression.isArrayCreationExpr()) {
                return ParseArrayCreationExpr.parse(this, expression.asArrayCreationExpr());
            }
            if (expression.isArrayInitializerExpr()) {
                return new ArrayInitializer(expression.asArrayInitializerExpr().getValues().stream()
                        .map(this::parseExpression).collect(Collectors.toList()));
            }
            if (expression.isEnclosedExpr()) {
                return new EnclosedExpression(parseExpression(((EnclosedExpr) expression).getInner()));
            }
            if (expression.isLongLiteralExpr()) {
                String valueWithL = expression.asLongLiteralExpr().getValue();
                String value = valueWithL.endsWith("L") || valueWithL.endsWith("l") ? valueWithL.substring(0, valueWithL.length() - 1) : valueWithL;
                return new LongConstant(Long.parseLong(value));
            }
            if (expression.isDoubleLiteralExpr()) {
                String valueWithD = expression.asDoubleLiteralExpr().getValue();
                String value = valueWithD.endsWith("D") || valueWithD.endsWith("d") ? valueWithD.substring(0, valueWithD.length() - 1) : valueWithD;
                return new DoubleConstant(Double.parseDouble(value));
            }
            if (expression.isCharLiteralExpr()) {
                return new CharConstant(expression.asCharLiteralExpr().asChar());
            }
            if (expression.isArrayAccessExpr()) {
                ArrayAccessExpr arrayAccessExpr = expression.asArrayAccessExpr();
                Expression scope = parseExpression(arrayAccessExpr.getName());
                Expression index = parseExpression(arrayAccessExpr.getIndex());
                return new ArrayAccess(scope, index);
            }
            if (expression.isInstanceOfExpr()) {
                InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
                Expression e = parseExpression(instanceOfExpr.getExpression());
                ParameterizedType type = ParameterizedType.from(typeContext, instanceOfExpr.getType());
                return new InstanceOf(e, type);
            }
            throw new UnsupportedOperationException("Unknown expression type " + expression +
                    " class " + expression.getClass() + " at " + expression.getBegin());
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while parsing expression of {} from {} to {}",
                    expression.getClass(),
                    expression.getBegin().orElse(null), expression.getEnd().orElse(null));
            throw rte;
        }
    }
}
