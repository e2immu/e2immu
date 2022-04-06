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

package org.e2immu.analyser.inspector.impl;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.UnionType;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.inspector.expr.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

// cannot even be a @Container, since the VariableContext passed on to us gets modified along the way
public record ExpressionContextImpl(ExpressionContext.ResolverRecursion resolver,
                                    TypeInfo enclosingType,
                                    TypeInfo uninspectedEnclosingType,
                                    MethodInfo enclosingMethod,
                                    FieldInfo enclosingField,
                                    ForwardReturnTypeInfo typeOfEnclosingSwitchExpression,
                                    TypeInfo primaryType,
                                    TypeContext typeContext,
                                    VariableContext variableContext,
                                    AnonymousTypeCounters anonymousTypeCounters) implements ExpressionContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionContextImpl.class);

    public static ExpressionContext forInspectionOfPrimaryType(
            ResolverRecursion resolver,
            @NotNull @NotModified TypeInfo typeInfo,
            @NotNull @NotModified TypeContext typeContext,
            @NotNull @NotModified AnonymousTypeCounters anonymousTypeCounters) {
        LOGGER.debug("Creating a new expression context for {}", typeInfo.fullyQualifiedName);
        return new ExpressionContextImpl(resolver, Objects.requireNonNull(typeInfo),
                null, null,
                null,
                null, typeInfo,
                Objects.requireNonNull(typeContext),
                VariableContext.initialVariableContext(null, new HashMap<>()),
                Objects.requireNonNull(anonymousTypeCounters));
    }

    public static ExpressionContext forTypeBodyParsing(
            ResolverRecursion resolver,
            @NotNull @NotModified TypeInfo enclosingType,
            @NotNull @NotModified TypeInfo primaryType,
            @NotNull @NotModified ExpressionContext expressionContextOfType) {
        Map<String, FieldReference> staticallyImportedFields = expressionContextOfType.typeContext().staticFieldImports();
        LOGGER.debug("Creating a new expression context for {}", enclosingType.fullyQualifiedName);
        return new ExpressionContextImpl(resolver, Objects.requireNonNull(enclosingType), null,
                null,
                null, null,
                Objects.requireNonNull(primaryType),
                Objects.requireNonNull(expressionContextOfType.typeContext()),
                VariableContext.initialVariableContext(expressionContextOfType.variableContext(), staticallyImportedFields),
                Objects.requireNonNull(expressionContextOfType.anonymousTypeCounters()));
    }

    @Override
    public ExpressionContext newVariableContext(MethodInfo methodInfo, ForwardReturnTypeInfo forwardReturnTypeInfo) {
        LOGGER.debug("Creating a new variable context for method {}", methodInfo.fullyQualifiedName);
        return new ExpressionContextImpl(resolver, enclosingType, null,
                methodInfo, null, typeOfEnclosingSwitchExpression,
                primaryType, typeContext, VariableContext.dependentVariableContext(variableContext), anonymousTypeCounters);
    }

    @Override
    public ExpressionContextImpl newVariableContext(@NotNull String reason) {
        LOGGER.debug("Creating a new variable context for {}", reason);
        return new ExpressionContextImpl(resolver, enclosingType, uninspectedEnclosingType,
                enclosingMethod,
                enclosingField, typeOfEnclosingSwitchExpression,
                primaryType, typeContext, VariableContext.dependentVariableContext(variableContext),
                anonymousTypeCounters);
    }

    @Override
    public ExpressionContext newVariableContextForEachLoop(@NotNull VariableContext newVariableContext) {
        LOGGER.debug("Creating a new variable context for for-each loop");
        return new ExpressionContextImpl(resolver, enclosingType, uninspectedEnclosingType, enclosingMethod,
                enclosingField,
                typeOfEnclosingSwitchExpression,
                primaryType, typeContext, newVariableContext, anonymousTypeCounters);
    }

    @Override
    public ExpressionContext newSwitchExpressionContext(TypeInfo subType,
                                                        VariableContext variableContext,
                                                        ForwardReturnTypeInfo typeOfEnclosingSwitchExpression) {
        LOGGER.debug("Creating a new switch expression context");
        return new ExpressionContextImpl(resolver, enclosingType, subType,
                null,
                null, typeOfEnclosingSwitchExpression,
                primaryType, typeContext, variableContext,
                anonymousTypeCounters);
    }

    @Override
    public ExpressionContext newLambdaContext(TypeInfo subType, VariableContext variableContext) {
        LOGGER.debug("Creating a new type context for lambda, sub-type {}", subType.fullyQualifiedName);
        return new ExpressionContextImpl(resolver, enclosingType, subType, null,
                null, null, primaryType,
                typeContext, variableContext, anonymousTypeCounters);
    }

    @Override
    public ExpressionContext newSubType(@NotNull TypeInfo subType) {
        LOGGER.debug("Creating a new type context for subtype {}", subType.simpleName);
        return new ExpressionContextImpl(resolver, subType, null,
                null, null, null, primaryType,
                new TypeContext(typeContext), variableContext, anonymousTypeCounters);
    }

    @Override
    public ExpressionContext newTypeContext(String reason) {
        LOGGER.debug("Creating a new type context for {}", reason);
        return new ExpressionContextImpl(resolver, enclosingType, uninspectedEnclosingType, enclosingMethod,
                enclosingField, typeOfEnclosingSwitchExpression, primaryType,
                new TypeContext(typeContext), variableContext, anonymousTypeCounters);
    }

    @Override
    public ExpressionContext newTypeContext(FieldInfo fieldInfo) {
        LOGGER.debug("Creating a new type context for initialiser of field {}", fieldInfo.fullyQualifiedName());
        return new ExpressionContextImpl(resolver, enclosingType, null, null,
                fieldInfo, null, primaryType,
                new TypeContext(typeContext), variableContext, anonymousTypeCounters);
    }

    /*
    used for compact constructors: we have already added some synthetic statements
     */
    @Override
    public Block continueParsingBlock(BlockStmt blockStmt, Block.BlockBuilder blockBuilder) {
        for (Statement statement : blockStmt.getStatements()) {
            parseStatement(blockBuilder, statement, null);
        }
        return blockBuilder.build();
    }

    @Override
    public Block parseBlockOrStatement(Statement stmt) {
        return parseBlockOrStatement(stmt, null);
    }

    // method makes changes to variableContext

    private Block parseBlockOrStatement(Statement stmt, String labelOfBlock) {
        Block.BlockBuilder blockBuilder = new Block.BlockBuilder(Identifier.from(stmt)).setLabel(labelOfBlock);
        if (stmt.isBlockStmt()) {
            for (Statement statement : stmt.asBlockStmt().getStatements()) {
                parseStatement(blockBuilder, statement, null);
            }
        } else {
            parseStatement(blockBuilder, stmt, null);
        }
        return blockBuilder.build();
    }

    // method modifies the blockBuilder...
    private void parseStatement(@NotNull Block.BlockBuilder blockBuilder, @NotNull @NotModified Statement statement, String labelOfStatement) {
        try {
            Identifier identifier = Identifier.from(statement);
            if (statement.isLabeledStmt()) {
                if (labelOfStatement != null) throw new UnsupportedOperationException();
                String label = statement.asLabeledStmt().getLabel().asString();
                parseStatement(blockBuilder, statement.asLabeledStmt().getStatement(), label);
                return;
            }

            org.e2immu.analyser.model.Statement newStatement;
            if (statement.isReturnStmt()) {
                Expression expression = statement.asReturnStmt().getExpression()
                        .map(e -> {
                            if (enclosingMethod != null) {
                                ParameterizedType returnType = typeContext.getMethodInspection(enclosingMethod).getReturnType();
                                ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(returnType);
                                return parseExpression(e, forward);
                            } // else: this is possible, when we're parsing a lambda
                            return parseExpressionStartVoid(e);
                        }).orElse(EmptyExpression.EMPTY_EXPRESSION);
                newStatement = new ReturnStatement(identifier, expression);
            } else if (statement.isYieldStmt()) {
                Expression expr = parseExpression(((YieldStmt) statement).getExpression(),
                        Objects.requireNonNull(typeOfEnclosingSwitchExpression));
                newStatement = new YieldStatement(identifier, expr);
            } else if (statement.isExpressionStmt()) {
                Expression expression = parseExpressionStartVoid(statement.asExpressionStmt().getExpression());
                newStatement = new ExpressionAsStatement(identifier, expression);
                variableContext.addAll(expression.newLocalVariables());
            } else if (statement.isForEachStmt()) {
                newStatement = forEachStatement(labelOfStatement, (ForEachStmt) statement);
            } else if (statement.isWhileStmt()) {
                newStatement = whileStatement(labelOfStatement, (WhileStmt) statement, identifier);
            } else if (statement.isBlockStmt()) {
                ExpressionContextImpl context = newVariableContext("block");
                newStatement = context.parseBlockOrStatement(statement, labelOfStatement);
            } else if (statement.isIfStmt()) {
                newStatement = ifThenElseStatement((IfStmt) statement, identifier);
            } else if (statement.isSynchronizedStmt()) {
                newStatement = synchronizedStatement((SynchronizedStmt) statement, identifier);
            } else if (statement.isThrowStmt()) {
                newStatement = new ThrowStatement(identifier, parseExpressionStartVoid(statement.asThrowStmt().getExpression()));
            } else if (statement.isLocalClassDeclarationStmt()) {
                newStatement = localClassDeclaration((LocalClassDeclarationStmt) statement, identifier);
            } else if (statement.isExplicitConstructorInvocationStmt()) {
                // this( ... )
                newStatement = explicitConstructorInvocation((ExplicitConstructorInvocationStmt) statement, identifier);
            } else if (statement.isTryStmt()) {
                newStatement = tryStatement(statement.asTryStmt(), identifier);
            } else if (statement.isContinueStmt()) {
                String label = statement.asContinueStmt().getLabel().map(SimpleName::asString).orElse(null);
                newStatement = new ContinueStatement(identifier, label);
            } else if (statement.isBreakStmt()) {
                String label = statement.asBreakStmt().getLabel().map(SimpleName::asString).orElse(null);
                newStatement = new BreakStatement(identifier, label);
            } else if (statement.isDoStmt()) {
                newStatement = doStatement(labelOfStatement, statement.asDoStmt(), identifier);
            } else if (statement.isForStmt()) {
                newStatement = forStatement(labelOfStatement, statement.asForStmt(), identifier);
            } else if (statement.isAssertStmt()) {
                newStatement = assertStatement(statement.asAssertStmt(), identifier);
            } else if (statement.isEmptyStmt()) {
                newStatement = new EmptyStatement(identifier);
            } else if (statement.isSwitchStmt()) {
                newStatement = switchStatement(statement.asSwitchStmt(), identifier);
            } else if (statement.isUnparsableStmt()) {
                LOGGER.warn("Skipping unparsable statement at {}", statement.getBegin());
                newStatement = null;
            } else {
                throw new UnsupportedOperationException("Statement " + statement.getClass() + " not implemented");
            }

            if (newStatement != null) {
                blockBuilder.addStatement(newStatement);
            }
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while parsing statement at line {}", statement.getBegin().orElse(null));
            throw rte;
        }
    }

    private org.e2immu.analyser.model.Statement switchStatement(@NotNull SwitchStmt switchStmt, Identifier identifier) {
        Expression selector = parseExpressionStartVoid(switchStmt.getSelector());
        ExpressionContextImpl newExpressionContext;
        TypeInfo enumType = selectorIsEnumType(selector);
        if (enumType != null) {
            TypeInspection enumInspection = typeContext.getTypeInspection(enumType);
            newExpressionContext = newVariableContext("switch-statement");
            enumInspection.fields().forEach(fieldInfo -> newExpressionContext.variableContext
                    .add(new FieldReference(typeContext, fieldInfo)));
        } else {
            newExpressionContext = this;
        }
        if (switchStmt.getEntries().isEmpty()) {
            return new SwitchStatementNewStyle(identifier, selector, List.of());
        }
        if (switchStmt.getEntries().stream().anyMatch(e ->
                e.getType() == com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP)) {
            return switchStatementOldStyle(newExpressionContext, selector, switchStmt, identifier);
        }
        List<SwitchEntry> entries = switchStmt.getEntries()
                .stream()
                .map(entry -> newExpressionContext.switchEntry(selector, entry))
                .collect(Collectors.toList());
        return new SwitchStatementNewStyle(identifier, selector, entries);
    }

    /*
    we group all statements, and make a list of switch labels
     */
    private org.e2immu.analyser.model.Statement switchStatementOldStyle(ExpressionContext expressionContextWithEnums,
                                                                        Expression selector,
                                                                        SwitchStmt switchStmt,
                                                                        Identifier identifier) {
        List<SwitchStatementOldStyle.SwitchLabel> labels = new ArrayList<>();
        Block.BlockBuilder blockBuilder = new Block.BlockBuilder(identifier);
        for (com.github.javaparser.ast.stmt.SwitchEntry switchEntry : switchStmt.getEntries()) {
            boolean first = true;
            for (Statement statement : switchEntry.getStatements()) {
                int from = blockBuilder.size();
                parseStatement(blockBuilder, statement, null);
                if (first) {
                    first = false;
                    if (switchEntry.getLabels().isEmpty()) {
                        // default
                        labels.add(new SwitchStatementOldStyle.SwitchLabel(EmptyExpression.DEFAULT_EXPRESSION, from));
                    } else {
                        // case X: case Y:
                        for (com.github.javaparser.ast.expr.Expression labelExpr : switchEntry.getLabels()) {
                            ForwardReturnTypeInfo info = new ForwardReturnTypeInfo(selector.returnType());
                            Expression parsedLabel = expressionContextWithEnums.parseExpression(labelExpr, info);
                            var switchLabel = new SwitchStatementOldStyle.SwitchLabel(parsedLabel, from);
                            labels.add(switchLabel);
                        }
                    }
                }
            }
        }
        return new SwitchStatementOldStyle(identifier, selector, blockBuilder.build(), labels);
    }

    @Override
    public TypeInfo selectorIsEnumType(@NotNull Expression selector) {
        TypeInfo typeInfo = selector.returnType().typeInfo;
        if (typeInfo != null && typeContext.getTypeInspection(typeInfo).typeNature() == TypeNature.ENUM) {
            return typeInfo;
        }
        return null;
    }

    @Override
    public SwitchEntry switchEntry(Expression switchVariableAsExpression,
                                   @NotNull com.github.javaparser.ast.stmt.SwitchEntry switchEntry) {
        List<Expression> labels = switchEntry.getLabels().stream()
                .map(this::parseExpressionStartVoid)
                .collect(Collectors.toList());
        switch (switchEntry.getType()) {
            case STATEMENT_GROUP -> throw new UnsupportedOperationException("In other method");

            case EXPRESSION, THROWS_STATEMENT -> {
                Block.BlockBuilder blockBuilder = new Block.BlockBuilder(Identifier.from(switchEntry));
                for (Statement statement : switchEntry.getStatements()) {
                    parseStatement(blockBuilder, statement, null);
                }
                return new SwitchEntry.StatementsEntry(Identifier.from(switchEntry),
                        typeContext.getPrimitives(),
                        switchVariableAsExpression, labels, blockBuilder.build().structure.statements());
            }
            case BLOCK -> {
                Block block = parseBlockOrStatement(switchEntry.getStatements().get(0));
                return new SwitchEntry.BlockEntry(Identifier.from(switchEntry),
                        typeContext.getPrimitives(), switchVariableAsExpression, labels, block);
            }
            default -> throw new UnsupportedOperationException("Unknown type " + switchEntry.getType());
        }
    }

    private org.e2immu.analyser.model.Statement forStatement(String label, ForStmt forStmt, Identifier identifier) {
        List<Expression> initializers = forStmt.getInitialization().stream().map(this::parseExpressionStartVoid).collect(Collectors.toList());
        ExpressionContextImpl newExpressionContext = newVariableContext("for-loop");
        for (Expression initialiser : initializers) {
            List<LocalVariableReference> newLocalVariables = initialiser.newLocalVariables();
            if (newLocalVariables == null)
                throw new NullPointerException("Statement of " + initialiser.getClass() + " produces null local vars");
            newExpressionContext.variableContext.addAll(newLocalVariables);
        }
        Expression condition = forStmt.getCompare().map(newExpressionContext::parseExpressionStartVoid).orElse(EmptyExpression.EMPTY_EXPRESSION);
        List<Expression> updaters = forStmt.getUpdate().stream().map(newExpressionContext::parseExpressionStartVoid).collect(Collectors.toList());
        Block block = newExpressionContext.parseBlockOrStatement(forStmt.getBody());

        return new ForStatement(identifier, label, initializers, condition, updaters, block);
    }

    private org.e2immu.analyser.model.Statement assertStatement(AssertStmt assertStmt, Identifier identifier) {
        Expression check = parseExpression(assertStmt.getCheck(), ForwardReturnTypeInfo.expectBoolean(typeContext));
        Expression message = assertStmt.getMessage().map(this::parseExpressionStartVoid).orElse(null);
        return new AssertStatement(identifier, check, message);
    }

    private org.e2immu.analyser.model.Statement tryStatement(TryStmt tryStmt, Identifier identifier) {
        List<Expression> resources = new ArrayList<>();
        ExpressionContextImpl tryExpressionContext = newVariableContext("try-resources");
        for (com.github.javaparser.ast.expr.Expression resource : tryStmt.getResources()) {
            LocalVariableCreation localVariableCreation = (LocalVariableCreation) tryExpressionContext
                    .parseExpressionStartVoid(resource);
            for (LocalVariableCreation.Declaration declaration : localVariableCreation.declarations) {
                tryExpressionContext.variableContext.add(declaration.localVariable(), declaration.expression());
            }
            resources.add(localVariableCreation);
        }
        Block tryBlock = tryExpressionContext.parseBlockOrStatement(tryStmt.getTryBlock());

        List<Pair<TryStatement.CatchParameter, Block>> catchClauses = new ArrayList<>();
        for (CatchClause catchClause : tryStmt.getCatchClauses()) {
            Parameter parameter = catchClause.getParameter();
            List<ParameterizedType> unionOfTypes;
            ParameterizedType typeOfVariable;
            if (parameter.getType().isUnionType()) {
                UnionType unionType = parameter.getType().asUnionType();
                unionOfTypes = unionType.getElements()
                        .stream()
                        .map(rt -> ParameterizedTypeFactory.from(typeContext, rt)).collect(Collectors.toList());
                typeOfVariable = typeContext.typeMap.get("java.lang.Exception").asParameterizedType(typeContext);
            } else {
                typeOfVariable = ParameterizedTypeFactory.from(typeContext, parameter.getType());
                unionOfTypes = List.of(typeOfVariable);
            }
            String name = parameter.getName().asString();
            LocalVariable localVariable = new LocalVariable.Builder()
                    .setOwningType(owningType())
                    .setName(name).setParameterizedType(typeOfVariable).build();
            Identifier clauseId = Identifier.from(catchClause);
            LocalVariableCreation lvc = new LocalVariableCreation(clauseId, typeContext.getPrimitives(), localVariable);
            TryStatement.CatchParameter catchParameter = new TryStatement.CatchParameter(clauseId, lvc, unionOfTypes);
            ExpressionContextImpl catchExpressionContext = newVariableContext("catch-clause");
            catchExpressionContext.variableContext.add(localVariable, EmptyExpression.EMPTY_EXPRESSION);
            Block block = catchExpressionContext.parseBlockOrStatement(catchClause.getBody());
            catchClauses.add(new Pair<>(catchParameter, block));
        }
        Block finallyBlock = tryStmt.getFinallyBlock().map(this::parseBlockOrStatement)
                .orElse(Block.emptyBlock(Identifier.generate("empty finally block")));
        return new TryStatement(identifier, resources, tryBlock, catchClauses, finallyBlock);
    }

    private org.e2immu.analyser.model.Statement whileStatement(String label, WhileStmt statement, Identifier identifier) {
        Expression expression = parseExpressionStartVoid(statement.getCondition());
        ExpressionContext context = newVariableContext("while-block");
        Block block = context.parseBlockOrStatement(statement.getBody());
        return new WhileStatement(identifier, label, expression, block);
    }

    private org.e2immu.analyser.model.Statement doStatement(String label, DoStmt statement, Identifier identifier) {
        ExpressionContext context = newVariableContext("do-block");
        Block block = context.parseBlockOrStatement(statement.getBody());
        Expression expression = parseExpressionStartVoid(statement.getCondition());
        return new DoStatement(identifier, label, expression, block);
    }

    private org.e2immu.analyser.model.Statement explicitConstructorInvocation(ExplicitConstructorInvocationStmt statement,
                                                                              Identifier identifier) {
        return ParseExplicitConstructorInvocation.parse(this, enclosingType, statement, identifier);
    }

    private org.e2immu.analyser.model.Statement localClassDeclaration(LocalClassDeclarationStmt statement,
                                                                      Identifier identifier) {
        String localName = statement.getClassDeclaration().getNameAsString();
        String typeName = StringUtil.capitalise(enclosingMethod.name) + "$" + localName + "$" + anonymousTypeCounters.newIndex(primaryType);
        TypeInfo typeInfo = new TypeInfo(enclosingType, typeName);
        typeContext.typeMap.add(typeInfo, InspectionState.STARTING_JAVA_PARSER);
        TypeInspector typeInspector = typeContext.typeMap.newTypeInspector(typeInfo, true, true);
        typeInspector.inspectLocalClassDeclaration(this, statement.getClassDeclaration());

        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        List<MethodInspection> methodAndConstructorInspections = typeInspection.methodsAndConstructors()
                .stream().map(typeContext::getMethodInspection).toList();

        resolver.resolve(typeContext, typeContext.typeMap.getE2ImmuAnnotationExpressions(),
                false, Map.of(typeInfo, this));

        typeContext.addToContext(localName, typeInfo, true);
        return new LocalClassDeclaration(identifier, typeInfo, methodAndConstructorInspections);
    }

    private org.e2immu.analyser.model.Statement synchronizedStatement(SynchronizedStmt statement, Identifier identifier) {
        Expression expression = parseExpressionStartVoid(statement.getExpression());
        ExpressionContext context = newVariableContext("synchronized-block");
        Block block = context.parseBlockOrStatement(statement.getBody());
        return new SynchronizedStatement(identifier, expression, block);
    }

    private org.e2immu.analyser.model.Statement forEachStatement(String label, ForEachStmt forEachStmt) {
        return ParseForEachStmt.parse(this, label, forEachStmt);
    }

    private org.e2immu.analyser.model.Statement ifThenElseStatement(IfStmt statement, Identifier identifier) {
        Expression conditional = parseExpression(statement.getCondition(),
                ForwardReturnTypeInfo.expectBoolean(typeContext));
        ExpressionContext ifContext = newVariableContext("if-block");
        Block ifBlock = ifContext.parseBlockOrStatement(statement.getThenStmt());
        Block elseBlock;
        if (statement.getElseStmt().isPresent()) {
            ExpressionContext elseContext = newVariableContext("else-block");
            elseBlock = elseContext.parseBlockOrStatement(statement.getElseStmt().get());
        } else {
            elseBlock = Block.emptyBlock(identifier);
        }
        return new IfElseStatement(identifier, conditional, ifBlock, elseBlock);
    }

    @Override
    @NotNull
    public Expression parseExpressionStartVoid(@NotNull com.github.javaparser.ast.expr.Expression expression) {
        return parseExpression(expression, new ForwardReturnTypeInfo());
    }

    @Override
    public Expression parseExpression(@NotNull com.github.javaparser.ast.expr.Expression expression,
                                      ForwardReturnTypeInfo forwardReturnTypeInfo) {
        assert forwardReturnTypeInfo != null;
        Identifier identifier = Identifier.from(expression);
        try {
            if (expression.isStringLiteralExpr()) {
                StringLiteralExpr stringLiteralExpr = (StringLiteralExpr) expression;
                return new StringConstant(typeContext.getPrimitives(), stringLiteralExpr.getValue());
            }
            if (expression.isIntegerLiteralExpr()) {
                IntegerLiteralExpr integerLiteralExpr = (IntegerLiteralExpr) expression;
                return new IntConstant(typeContext.getPrimitives(), (Integer) integerLiteralExpr.asNumber());
            }
            if (expression.isBooleanLiteralExpr()) {
                BooleanLiteralExpr booleanLiteralExpr = (BooleanLiteralExpr) expression;
                return new BooleanConstant(typeContext.getPrimitives(), booleanLiteralExpr.getValue());
            }
            if (expression.isNullLiteralExpr()) {
                return NullConstant.NULL_CONSTANT;
            }
            if (expression.isCastExpr()) {
                CastExpr castExpr = (CastExpr) expression;
                ParameterizedType parameterizedType = ParameterizedTypeFactory.from(typeContext, castExpr.getType());
                return new Cast(parseExpressionStartVoid(castExpr.getExpression()), parameterizedType);
            }
            if (expression.isBinaryExpr()) {
                BinaryExpr binaryExpr = (BinaryExpr) expression;
                Expression lhs = parseExpressionStartVoid(binaryExpr.getLeft());
                Expression rhs = parseExpressionStartVoid(binaryExpr.getRight());
                TypeInfo typeInfo;
                if (lhs instanceof NullConstant) {
                    typeInfo = rhs.returnType().typeInfo;
                } else if (rhs instanceof NullConstant) {
                    typeInfo = lhs.returnType().typeInfo;
                } else if (lhs.returnType().allowsForOperators() || rhs.returnType().allowsForOperators()) {
                    ParameterizedType widestType = typeContext.getPrimitives().widestType(lhs.returnType(), rhs.returnType());
                    if (!widestType.isType())
                        throw new UnsupportedOperationException("? for " + lhs.returnType() + " and " + rhs.returnType());
                    typeInfo = widestType.typeInfo;
                } else {
                    typeInfo = null;
                }
                MethodInfo operatorMethod = BinaryOperator.getOperator(typeContext.getPrimitives(), binaryExpr.getOperator(), typeInfo);
                return new BinaryOperator(identifier,
                        typeContext.getPrimitives(), lhs, operatorMethod, rhs,
                        BinaryOperator.precedence(typeContext.getPrimitives(), operatorMethod));
            }
            if (expression.isUnaryExpr()) {
                UnaryExpr unaryExpr = (UnaryExpr) expression;
                Expression exp = parseExpressionStartVoid(unaryExpr.getExpression());
                ParameterizedType pt = exp.returnType();
                if (pt.typeInfo == null) throw new UnsupportedOperationException("??");
                MethodInfo operator = UnaryOperator.getOperator(typeContext.getPrimitives(), unaryExpr.getOperator(), pt.typeInfo);
                Primitives primitives = typeContext.getPrimitives();
                if (primitives.isPreOrPostFixOperator(operator)) {
                    boolean isPrefix = primitives.isPrefixOperator(operator);
                    MethodInfo associatedAssignment = primitives.prePostFixToAssignment(operator);
                    return new Assignment(identifier, typeContext.getPrimitives(),
                            exp, new IntConstant(typeContext.getPrimitives(), 1), associatedAssignment, isPrefix,
                            true);
                }
                return new UnaryOperator(identifier, operator, exp, UnaryOperator.precedence(unaryExpr.getOperator()));
            }

            if (expression.isThisExpr()) {
                ThisExpr thisExpr = expression.asThisExpr();
                Variable thisVar = This.create(typeContext, false, enclosingType,
                        thisExpr.getTypeName().map(Name::asString).orElse(null));
                return new VariableExpression(thisVar);
            }

            if (expression.isSuperExpr()) {
                SuperExpr superExpr = expression.asSuperExpr();
                Variable superVar = This.create(typeContext, true, enclosingType,
                        superExpr.getTypeName().map(Name::asString).orElse(null));
                return new VariableExpression(superVar);
            }

            if (expression.isTypeExpr()) {
                // note that "System.out" is a type expression; ParameterizedType.from can handle this, but we'd rather see a field access
                TypeExpr typeExpr = (TypeExpr) expression;
                if (typeExpr.getType().isClassOrInterfaceType()) {
                    ClassOrInterfaceType cit = typeExpr.getType().asClassOrInterfaceType();
                    if (cit.getScope().isPresent()) {
                        // System.out, we'll return a field access, scope is System
                        // but: expressionContext.typeContext, scope is expressionContext = variable!
                        Variable variable = variableContext.get(cit.getScope().get().getNameAsString(), false);
                        Expression scope;
                        if (variable != null) {
                            scope = new VariableExpression(variable);
                        } else {
                            ParameterizedType parameterizedType = ParameterizedTypeFactory.from(typeContext, cit.getScope().get());
                            scope = new TypeExpression(parameterizedType, Diamond.NO);
                        }
                        return ParseFieldAccessExpr.createFieldAccess(typeContext, scope, cit.getNameAsString(),
                                expression.getBegin().orElseThrow(), enclosingType);
                    }
                    // there is a real possibility that the type expression is NOT a type but a local field...
                    // therefore we check the variable context first
                    Variable variable = variableContext.get(typeExpr.getTypeAsString(), false);
                    if (variable != null) {
                        return new VariableExpression(variable);
                    }
                }
                ParameterizedType parameterizedType = ParameterizedTypeFactory.from(typeContext, typeExpr.getType());
                return new TypeExpression(parameterizedType, Diamond.SHOW_ALL);
            }
            if (expression.isClassExpr()) {
                ClassExpr classExpr = (ClassExpr) expression;
                ParameterizedType parameterizedType = ParameterizedTypeFactory.from(typeContext, classExpr.getType());
                return new ClassExpression(typeContext.getPrimitives(), parameterizedType);
            }
            if (expression.isNameExpr()) {
                return ParseNameExpr.parse(this, expression.asNameExpr());
            }
            if (expression.isObjectCreationExpr()) {
                ObjectCreationExpr objectCreationExpr = (ObjectCreationExpr) expression;
                if (forwardReturnTypeInfo.erasure()) {
                    return ParseObjectCreationExpr.erasure(this, objectCreationExpr);
                }
                return ParseObjectCreationExpr.parse(this, objectCreationExpr, forwardReturnTypeInfo);
            }
            if (expression.isVariableDeclarationExpr()) {
                VariableDeclarationExpr vde = (VariableDeclarationExpr) expression;
                VariableDeclarator vd0 = vde.getVariable(0);
                List<AnnotationExpression> annotations = vde.getAnnotations().stream()
                        .map(ae -> AnnotationInspector.inspect(this, ae)).toList();
                Set<LocalVariableModifier> modifiers = vde.getModifiers().stream()
                        .map(LocalVariableModifier::from).collect(Collectors.toUnmodifiableSet());
                boolean isVar = "var".equals(vd0.getType().asString());
                List<LocalVariableCreation.Declaration> declarations = new LinkedList<>();
                for (VariableDeclarator variableDeclarator : vde.getVariables()) {
                    ParameterizedType parameterizedType;
                    Expression initializer;

                    if (isVar) {
                        // run initializer without info
                        initializer = variableDeclarator.getInitializer()
                                .map(this::parseExpressionStartVoid).orElse(EmptyExpression.EMPTY_EXPRESSION);
                        parameterizedType = initializer.returnType();
                    } else {
                        parameterizedType = ParameterizedTypeFactory.from(typeContext, variableDeclarator.getType());
                        initializer = variableDeclarator.getInitializer()
                                .map(i -> parseExpression(i, new ForwardReturnTypeInfo(parameterizedType)))
                                .orElse(EmptyExpression.EMPTY_EXPRESSION);
                    }
                    LocalVariable.Builder localVariable = new LocalVariable.Builder()
                            .setName(variableDeclarator.getNameAsString())
                            .setParameterizedType(parameterizedType);
                    localVariable.setAnnotations(annotations);
                    localVariable.setModifiers(modifiers);
                    LocalVariable lv = localVariable.setOwningType(owningType()).build();
                    LocalVariableCreation.Declaration declaration = new LocalVariableCreation.Declaration
                            (Identifier.from(variableDeclarator), lv, initializer);
                    declarations.add(declaration);
                    variableContext.add(lv, initializer);
                }
                return new LocalVariableCreation(typeContext.getPrimitives(), List.copyOf(declarations), isVar);
            }
            if (expression.isAssignExpr()) {
                AssignExpr assignExpr = (AssignExpr) expression;
                Expression target = parseExpressionStartVoid(assignExpr.getTarget());
                Expression value = parseExpression(assignExpr.getValue(),
                        new ForwardReturnTypeInfo(target.returnType()));
                if (value.returnType().isType() && value.returnType().isPrimitiveExcludingVoid() &&
                        target.returnType().isType() && target.returnType().isPrimitiveExcludingVoid()) {
                    ParameterizedType widestType = typeContext.getPrimitives().widestType(value.returnType(), target.returnType());
                    MethodInfo primitiveOperator = Assignment.operator(typeContext.getPrimitives(), assignExpr.getOperator(), widestType.typeInfo);
                    return new Assignment(identifier, typeContext.getPrimitives(), target, value, primitiveOperator,
                            null, true);
                }
                return new Assignment(identifier, typeContext.getPrimitives(), target, value);
            }
            if (expression.isMethodCallExpr()) {
                if (forwardReturnTypeInfo.erasure()) {
                    return new ParseMethodCallExpr(typeContext).erasure(this, expression.asMethodCallExpr());
                }
                return new ParseMethodCallExpr(typeContext).parse(this, expression.asMethodCallExpr(), forwardReturnTypeInfo);
            }
            if (expression.isMethodReferenceExpr()) {
                if (forwardReturnTypeInfo.erasure()) {
                    return ParseMethodReferenceExpr.erasure(this, expression.asMethodReferenceExpr());
                }
                return ParseMethodReferenceExpr.parse(this, expression.asMethodReferenceExpr(), forwardReturnTypeInfo);
            }
            if (expression.isConditionalExpr()) {
                ConditionalExpr conditionalExpr = (ConditionalExpr) expression;
                Expression condition = parseExpression(conditionalExpr.getCondition(),
                        ForwardReturnTypeInfo.expectBoolean(typeContext));
                Expression ifTrue = parseExpression(conditionalExpr.getThenExpr(),
                        forwardReturnTypeInfo);
                Expression ifFalse = parseExpression(conditionalExpr.getElseExpr(),
                        forwardReturnTypeInfo);
                return new InlineConditional(identifier, typeContext, condition, ifTrue, ifFalse);
            }
            if (expression.isFieldAccessExpr()) {
                return ParseFieldAccessExpr.parse(this, expression.asFieldAccessExpr(), forwardReturnTypeInfo);
            }
            if (expression.isLambdaExpr()) {
                if (forwardReturnTypeInfo.erasure()) {
                    return ParseLambdaExpr.erasure(this, expression.asLambdaExpr());
                }
                return ParseLambdaExpr.parse(this, expression.asLambdaExpr(), forwardReturnTypeInfo);
            }
            if (expression.isSwitchExpr()) {
                return ParseSwitchExpr.parse(this, expression.asSwitchExpr(), forwardReturnTypeInfo);
            }
            if (expression.isArrayCreationExpr()) {
                return ParseArrayCreationExpr.parse(this, expression.asArrayCreationExpr());
            }
            if (expression.isArrayInitializerExpr()) {
                return new ArrayInitializer(identifier, typeContext, expression.asArrayInitializerExpr().getValues().stream()
                        .map(this::parseExpressionStartVoid).collect(Collectors.toList()), forwardReturnTypeInfo.type());
            }
            if (expression.isEnclosedExpr()) {
                Expression inner = parseExpression(((EnclosedExpr) expression).getInner(), forwardReturnTypeInfo);
                return new EnclosedExpression(identifier, inner);
            }
            if (expression.isLongLiteralExpr()) {
                String value = expression.asLongLiteralExpr().getValue();
                return LongConstant.parse(typeContext.getPrimitives(), value);
            }
            if (expression.isDoubleLiteralExpr()) {
                String valueWithD = expression.asDoubleLiteralExpr().getValue();
                if (valueWithD.endsWith("f") || valueWithD.endsWith("F")) {
                    String value = valueWithD.substring(0, valueWithD.length() - 1);
                    return new FloatConstant(typeContext.getPrimitives(), Float.parseFloat(value));
                }
                String value = valueWithD.endsWith("D") || valueWithD.endsWith("d") ? valueWithD.substring(0, valueWithD.length() - 1) : valueWithD;
                return new DoubleConstant(typeContext.getPrimitives(), Double.parseDouble(value));
            }
            if (expression.isCharLiteralExpr()) {
                return new CharConstant(typeContext.getPrimitives(), expression.asCharLiteralExpr().asChar());
            }
            if (expression.isArrayAccessExpr()) {
                ArrayAccessExpr arrayAccessExpr = expression.asArrayAccessExpr();
                Expression scope = parseExpressionStartVoid(arrayAccessExpr.getName());
                Expression index = parseExpression(arrayAccessExpr.getIndex(),
                        new ForwardReturnTypeInfo(typeContext.getPrimitives().intParameterizedType()));
                Identifier indexIdentifier = Identifier.from(arrayAccessExpr.getIndex());
                return new ArrayAccess(identifier, scope, index, indexIdentifier, enclosingType);
            }
            if (expression.isInstanceOfExpr()) {
                InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
                Expression e = parseExpressionStartVoid(instanceOfExpr.getExpression());
                ParameterizedType type = ParameterizedTypeFactory.from(typeContext, instanceOfExpr.getType());
                LocalVariableReference patternVariable = instanceOfExpr.getPattern().map(pattern ->
                        new LocalVariableReference(new LocalVariable.Builder()
                                .setName(pattern.getNameAsString())
                                .setOwningType(enclosingType)
                                .setParameterizedType(type)
                                .build())).orElse(null);
                if (patternVariable != null) {
                    variableContext.add(patternVariable);
                }
                return new InstanceOf(identifier, typeContext.getPrimitives(), type, e, patternVariable);
            }
            if (expression.isSingleMemberAnnotationExpr()) {
                SingleMemberAnnotationExpr sma = expression.asSingleMemberAnnotationExpr();
                return parseExpressionStartVoid(sma.getMemberValue());
                // expect a field access or variable expression to show up
                // TODO write tests
            }
            // new switch expression isn't there yet in JavaParser...

            if (expression.isTextBlockLiteralExpr()) {
                TextBlockLiteralExpr textBlock = expression.asTextBlockLiteralExpr();
                return new StringConstant(typeContext.getPrimitives(), textBlock.stripIndent());
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

    @Override
    public TypeInfo owningType() {
        return uninspectedEnclosingType != null ? uninspectedEnclosingType : enclosingType;
    }

    @Override
    public Location getLocation() {
        if (enclosingMethod != null) return enclosingMethod.newLocation();
        if (enclosingField != null) return enclosingField.newLocation();
        return enclosingType.newLocation();
    }
}
