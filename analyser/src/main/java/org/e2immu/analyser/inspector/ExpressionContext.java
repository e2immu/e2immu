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

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.UnionType;
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
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.EXPRESSION_CONTEXT;
import static org.e2immu.analyser.util.Logger.log;

// cannot even be a @Container, since the VariableContext passed on to us gets modified along the way
public class ExpressionContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionContext.class);

    public final TypeContext typeContext;
    public final TypeInfo enclosingType;
    /*
    We have a chicken-and-egg problem when parsing lambda's: we'd need a full blown enclosing type to start
    the inspection, but we can only have the method's signature AFTER this inspection.
    But we already know the name of the type. We only need it to set the ownership of local variables.
     */
    public final TypeInfo uninspectedEnclosingType;
    public final TypeInfo primaryType;
    public final VariableContext variableContext; // gets modified! so this class cannot even be a container...
    public final AnonymousTypeCounters anonymousTypeCounters;
    public final MethodInfo enclosingMethod;
    public final ForwardReturnTypeInfo forwardReturnTypeInfo;
    public final FieldInfo enclosingField; // terminology ~ assigning field, field that we're assigning to
    public final ForwardReturnTypeInfo typeOfEnclosingSwitchExpression;

    private final List<TypeInfo> newlyCreatedTypes = new LinkedList<>();

    public void addNewlyCreatedType(TypeInfo anonymousType) {
        newlyCreatedTypes.add(anonymousType);
    }

    public Stream<TypeInfo> streamNewlyCreatedTypes() {
        return newlyCreatedTypes.stream();
    }

    public static ExpressionContext forInspectionOfPrimaryType(@NotNull @NotModified TypeInfo typeInfo,
                                                               @NotNull @NotModified TypeContext typeContext,
                                                               @NotNull @NotModified AnonymousTypeCounters anonymousTypeCounters) {
        log(EXPRESSION_CONTEXT, "Creating a new expression context for {}", typeInfo.fullyQualifiedName);
        return new ExpressionContext(Objects.requireNonNull(typeInfo), null, null,
                ForwardReturnTypeInfo.NO_INFO, null,
                null, typeInfo,
                Objects.requireNonNull(typeContext),
                VariableContext.initialVariableContext(null, new HashMap<>()),
                Objects.requireNonNull(anonymousTypeCounters));
    }

    public static ExpressionContext forTypeBodyParsing(@NotNull @NotModified TypeInfo enclosingType,
                                                       @NotNull @NotModified TypeInfo primaryType,
                                                       @NotNull @NotModified ExpressionContext expressionContextOfType) {
        Map<String, FieldReference> staticallyImportedFields = expressionContextOfType.typeContext.staticFieldImports();
        log(EXPRESSION_CONTEXT, "Creating a new expression context for {}", enclosingType.fullyQualifiedName);
        return new ExpressionContext(Objects.requireNonNull(enclosingType), null,
                null,
                ForwardReturnTypeInfo.NO_INFO,
                null, null,
                Objects.requireNonNull(primaryType),
                Objects.requireNonNull(expressionContextOfType.typeContext),
                VariableContext.initialVariableContext(expressionContextOfType.variableContext, staticallyImportedFields),
                Objects.requireNonNull(expressionContextOfType.anonymousTypeCounters));
    }

    private ExpressionContext(TypeInfo enclosingType,
                              TypeInfo uninspectedEnclosingType,
                              MethodInfo enclosingMethod,
                              ForwardReturnTypeInfo forwardReturnTypeInfo,
                              FieldInfo enclosingField,
                              ForwardReturnTypeInfo typeOfEnclosingSwitchExpression,
                              TypeInfo primaryType,
                              TypeContext typeContext,
                              VariableContext variableContext,
                              AnonymousTypeCounters anonymousTypeCounters) {
        this.typeContext = typeContext;
        this.primaryType = primaryType;
        this.enclosingType = enclosingType;
        this.uninspectedEnclosingType = uninspectedEnclosingType;
        this.enclosingMethod = enclosingMethod;
        this.enclosingField = enclosingField;
        this.anonymousTypeCounters = anonymousTypeCounters;
        this.variableContext = variableContext;
        this.typeOfEnclosingSwitchExpression = typeOfEnclosingSwitchExpression;
        this.forwardReturnTypeInfo = Objects.requireNonNull(forwardReturnTypeInfo);
    }

    public ExpressionContext newVariableContext(MethodInfo methodInfo, ForwardReturnTypeInfo forwardReturnTypeInfo) {
        log(EXPRESSION_CONTEXT, "Creating a new variable context for method {}", methodInfo.fullyQualifiedName);
        return new ExpressionContext(enclosingType, null,
                methodInfo, forwardReturnTypeInfo, null, null,
                primaryType, typeContext, VariableContext.dependentVariableContext(variableContext), anonymousTypeCounters);
    }

    public ExpressionContext newVariableContext(@NotNull String reason) {
        log(EXPRESSION_CONTEXT, "Creating a new variable context for {}", reason);
        return new ExpressionContext(enclosingType, uninspectedEnclosingType,
                enclosingMethod,
                forwardReturnTypeInfo,
                enclosingField, typeOfEnclosingSwitchExpression,
                primaryType, typeContext, VariableContext.dependentVariableContext(variableContext),
                anonymousTypeCounters);
    }

    public ExpressionContext newVariableContextForEachLoop(@NotNull VariableContext newVariableContext) {
        log(EXPRESSION_CONTEXT, "Creating a new variable context for for-each loop");
        return new ExpressionContext(enclosingType, uninspectedEnclosingType, enclosingMethod,
                forwardReturnTypeInfo,
                enclosingField,
                typeOfEnclosingSwitchExpression,
                primaryType, typeContext, newVariableContext, anonymousTypeCounters);
    }


    public ExpressionContext newLambdaContext(TypeInfo subType, VariableContext variableContext) {
        log(EXPRESSION_CONTEXT, "Creating a new type context for lambda, sub-type {}", subType.fullyQualifiedName);
        return new ExpressionContext(enclosingType, subType, enclosingMethod,
                forwardReturnTypeInfo,
                enclosingField, typeOfEnclosingSwitchExpression, primaryType,
                typeContext, variableContext, anonymousTypeCounters);
    }

    public ExpressionContext newSubType(@NotNull TypeInfo subType) {
        log(EXPRESSION_CONTEXT, "Creating a new type context for subtype {}", subType.simpleName);
        return new ExpressionContext(subType, null,
                null, ForwardReturnTypeInfo.NO_INFO, null, null, primaryType,
                new TypeContext(typeContext), variableContext, anonymousTypeCounters);
    }

    public ExpressionContext newTypeContext(String reason) {
        log(EXPRESSION_CONTEXT, "Creating a new type context for {}", reason);
        return new ExpressionContext(enclosingType, uninspectedEnclosingType, enclosingMethod,
                ForwardReturnTypeInfo.NO_INFO,
                enclosingField, typeOfEnclosingSwitchExpression, primaryType,
                new TypeContext(typeContext), variableContext, anonymousTypeCounters);
    }

    public ExpressionContext newTypeContext(FieldInfo fieldInfo, ForwardReturnTypeInfo forwardReturnTypeInfo) {
        log(EXPRESSION_CONTEXT, "Creating a new type context for initialiser of field {}", fieldInfo.fullyQualifiedName());
        return new ExpressionContext(enclosingType, null, null,
                forwardReturnTypeInfo,
                fieldInfo, null, primaryType,
                new TypeContext(typeContext), variableContext, anonymousTypeCounters);
    }

    /*
    used for compact constructors: we have already added some synthetic statements
     */
    public Block continueParsingBlock(BlockStmt blockStmt, Block.BlockBuilder blockBuilder) {
        for (Statement statement : blockStmt.getStatements()) {
            parseStatement(blockBuilder, statement, null);
        }
        return blockBuilder.build();
    }

    public Block parseBlockOrStatement(Statement stmt) {
        assert enclosingMethod != null || enclosingField != null;
        return parseBlockOrStatement(stmt, null);
    }

    // method makes changes to variableContext

    private Block parseBlockOrStatement(Statement stmt, String labelOfBlock) {
        Block.BlockBuilder blockBuilder = new Block.BlockBuilder().setLabel(labelOfBlock);
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
            if (statement.isLabeledStmt()) {
                if (labelOfStatement != null) throw new UnsupportedOperationException();
                String label = statement.asLabeledStmt().getLabel().asString();
                parseStatement(blockBuilder, statement.asLabeledStmt().getStatement(), label);
                return;
            }

            org.e2immu.analyser.model.Statement newStatement;
            if (statement.isReturnStmt()) {
                newStatement = new ReturnStatement(((ReturnStmt) statement).getExpression()
                        .map(this::parseExpression).orElse(EmptyExpression.EMPTY_EXPRESSION));
            } else if (statement.isYieldStmt()) {
                Expression expr = parseExpression(((YieldStmt) statement).getExpression(),
                        Objects.requireNonNull(typeOfEnclosingSwitchExpression));
                newStatement = new YieldStatement(expr);
            } else if (statement.isExpressionStmt()) {
                Expression expression = parseExpression(((ExpressionStmt) statement).getExpression());
                newStatement = new ExpressionAsStatement(expression);
                variableContext.addAll(expression.newLocalVariables());
            } else if (statement.isForEachStmt()) {
                newStatement = forEachStatement(labelOfStatement, (ForEachStmt) statement);
            } else if (statement.isWhileStmt()) {
                newStatement = whileStatement(labelOfStatement, (WhileStmt) statement);
            } else if (statement.isBlockStmt()) {
                ExpressionContext context = newVariableContext("block");
                newStatement = context.parseBlockOrStatement(statement, labelOfStatement);
                inherit(context);
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
                String label = statement.asContinueStmt().getLabel().map(SimpleName::asString).orElse(null);
                newStatement = new ContinueStatement(label);
            } else if (statement.isBreakStmt()) {
                String label = statement.asBreakStmt().getLabel().map(SimpleName::asString).orElse(null);
                newStatement = new BreakStatement(label);
            } else if (statement.isDoStmt()) {
                newStatement = doStatement(labelOfStatement, statement.asDoStmt());
            } else if (statement.isForStmt()) {
                newStatement = forStatement(labelOfStatement, statement.asForStmt());
            } else if (statement.isAssertStmt()) {
                newStatement = assertStatement(statement.asAssertStmt());
            } else if (statement.isEmptyStmt()) {
                newStatement = EmptyStatement.EMPTY_STATEMENT;
            } else if (statement.isSwitchStmt()) {
                newStatement = switchStatement(statement.asSwitchStmt());
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

    private org.e2immu.analyser.model.Statement switchStatement(@NotNull SwitchStmt switchStmt) {
        Expression selector = parseExpression(switchStmt.getSelector());
        ExpressionContext newExpressionContext;
        TypeInfo enumType = selectorIsEnumType(selector);
        TypeInspection enumInspection = typeContext.getTypeInspection(enumType);
        if (enumType != null) {
            newExpressionContext = newVariableContext("switch-statement");
            Variable scope = new This(typeContext, enumType);
            enumInspection.fields().forEach(fieldInfo -> newExpressionContext.variableContext
                    .add(new FieldReference(typeContext, fieldInfo,
                            fieldInfo.isStatic(typeContext) ? null : scope)));
        } else {
            newExpressionContext = this;
        }
        if (switchStmt.getEntries().isEmpty()) {
            return new SwitchStatementNewStyle(selector, List.of());
        }
        if (switchStmt.getEntries().stream().anyMatch(e ->
                e.getType() == com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP)) {
            var res = switchStatementOldStyle(newExpressionContext, selector, switchStmt);
            inherit(newExpressionContext);
            return res;
        }
        List<SwitchEntry> entries = switchStmt.getEntries()
                .stream()
                .map(entry -> newExpressionContext.switchEntry(selector, entry))
                .collect(Collectors.toList());
        inherit(newExpressionContext);
        return new SwitchStatementNewStyle(selector, entries);
    }

    /*
    we group all statements, and make a list of switch labels
     */
    private org.e2immu.analyser.model.Statement switchStatementOldStyle(ExpressionContext expressionContextWithEnums,
                                                                        Expression selector,
                                                                        SwitchStmt switchStmt) {
        List<SwitchStatementOldStyle.SwitchLabel> labels = new ArrayList<>();
        Block.BlockBuilder blockBuilder = new Block.BlockBuilder();
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
                            ForwardReturnTypeInfo info = ForwardReturnTypeInfo.computeSAM(selector.returnType(), typeContext);
                            Expression parsedLabel = expressionContextWithEnums.parseExpression(labelExpr, info);
                            var switchLabel = new SwitchStatementOldStyle.SwitchLabel(parsedLabel, from);
                            labels.add(switchLabel);
                        }
                    }
                }
            }
        }
        return new SwitchStatementOldStyle(selector, blockBuilder.build(), labels);
    }

    public TypeInfo selectorIsEnumType(@NotNull Expression selector) {
        TypeInfo typeInfo = selector.returnType().typeInfo;
        if (typeInfo != null && typeContext.getTypeInspection(typeInfo).typeNature() == TypeNature.ENUM) {
            return typeInfo;
        }
        return null;
    }

    public SwitchEntry switchEntry(Expression switchVariableAsExpression, @NotNull com.github.javaparser.ast.stmt.SwitchEntry switchEntry) {
        List<Expression> labels = switchEntry.getLabels().stream().map(this::parseExpression).collect(Collectors.toList());
        switch (switchEntry.getType()) {
            case STATEMENT_GROUP -> throw new UnsupportedOperationException("In other method");

            case EXPRESSION, THROWS_STATEMENT -> {
                Block.BlockBuilder blockBuilder = new Block.BlockBuilder();
                for (Statement statement : switchEntry.getStatements()) {
                    parseStatement(blockBuilder, statement, null);
                }
                return new SwitchEntry.StatementsEntry(typeContext.getPrimitives(),
                        switchVariableAsExpression, labels, blockBuilder.build().structure.statements());
            }
            case BLOCK -> {
                Block block = parseBlockOrStatement(switchEntry.getStatements().get(0));
                return new SwitchEntry.BlockEntry(typeContext.getPrimitives(), switchVariableAsExpression, labels, block);
            }
            default -> throw new UnsupportedOperationException("Unknown type " + switchEntry.getType());
        }
    }

    private org.e2immu.analyser.model.Statement forStatement(String label, ForStmt forStmt) {
        List<Expression> initializers = forStmt.getInitialization().stream().map(this::parseExpression).collect(Collectors.toList());
        ExpressionContext newExpressionContext = newVariableContext("for-loop");
        for (Expression initialiser : initializers) {
            List<LocalVariableReference> newLocalVariables = initialiser.newLocalVariables();
            if (newLocalVariables == null)
                throw new NullPointerException("Statement of " + initialiser.getClass() + " produces null local vars");
            newExpressionContext.variableContext.addAll(newLocalVariables);
        }
        Expression condition = forStmt.getCompare().map(newExpressionContext::parseExpression).orElse(EmptyExpression.EMPTY_EXPRESSION);
        List<Expression> updaters = forStmt.getUpdate().stream().map(newExpressionContext::parseExpression).collect(Collectors.toList());
        Block block = newExpressionContext.parseBlockOrStatement(forStmt.getBody());
        inherit(newExpressionContext);
        return new ForStatement(label, initializers, condition, updaters, block);
    }

    private org.e2immu.analyser.model.Statement assertStatement(AssertStmt assertStmt) {
        Expression check = parseExpression(assertStmt.getCheck());
        Expression message = assertStmt.getMessage().map(this::parseExpression).orElse(null);
        return new AssertStatement(check, message);
    }

    private org.e2immu.analyser.model.Statement tryStatement(TryStmt tryStmt) {
        List<Expression> resources = new ArrayList<>();
        ExpressionContext tryExpressionContext = newVariableContext("try-resources");
        for (com.github.javaparser.ast.expr.Expression resource : tryStmt.getResources()) {
            LocalVariableCreation localVariableCreation = (LocalVariableCreation) tryExpressionContext.parseExpression(resource);
            tryExpressionContext.variableContext.add(localVariableCreation.localVariable, localVariableCreation.expression);
            resources.add(localVariableCreation);
        }
        Block tryBlock = tryExpressionContext.parseBlockOrStatement(tryStmt.getTryBlock());
        inherit(tryExpressionContext);
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
                typeOfVariable = typeContext.typeMapBuilder.get("java.lang.Exception").asParameterizedType(typeContext);
            } else {
                typeOfVariable = ParameterizedTypeFactory.from(typeContext, parameter.getType());
                unionOfTypes = List.of(typeOfVariable);
            }
            String name = parameter.getName().asString();
            LocalVariable localVariable = new LocalVariable.Builder()
                    .setOwningType(owningType())
                    .setName(name).setSimpleName(name).setParameterizedType(typeOfVariable).build();
            LocalVariableCreation lvc = new LocalVariableCreation(typeContext, localVariable);
            TryStatement.CatchParameter catchParameter = new TryStatement.CatchParameter(lvc, unionOfTypes);
            ExpressionContext catchExpressionContext = newVariableContext("catch-clause");
            catchExpressionContext.variableContext.add(localVariable, EmptyExpression.EMPTY_EXPRESSION);
            Block block = catchExpressionContext.parseBlockOrStatement(catchClause.getBody());
            inherit(catchExpressionContext);
            catchClauses.add(new Pair<>(catchParameter, block));
        }
        Block finallyBlock = tryStmt.getFinallyBlock().map(this::parseBlockOrStatement).orElse(Block.EMPTY_BLOCK);
        return new TryStatement(resources, tryBlock, catchClauses, finallyBlock);
    }

    private org.e2immu.analyser.model.Statement whileStatement(String label, WhileStmt statement) {
        org.e2immu.analyser.model.Expression expression = parseExpression(statement.getCondition());
        ExpressionContext context = newVariableContext("while-block");
        Block block = context.parseBlockOrStatement(statement.getBody());
        inherit(context);
        return new WhileStatement(label, expression, block);
    }

    private org.e2immu.analyser.model.Statement doStatement(String label, DoStmt statement) {
        ExpressionContext context = newVariableContext("do-block");
        Block block = context.parseBlockOrStatement(statement.getBody());
        inherit(context);
        org.e2immu.analyser.model.Expression expression = parseExpression(statement.getCondition());
        return new DoStatement(label, expression, block);
    }

    private org.e2immu.analyser.model.Statement explicitConstructorInvocation(ExplicitConstructorInvocationStmt statement) {
        List<org.e2immu.analyser.model.Expression> parameterExpressions = statement.getArguments()
                .stream().map(this::parseExpression).collect(Collectors.toList());
        MethodInfo constructor = enclosingType.findConstructor(typeContext, parameterExpressions);
        return new ExplicitConstructorInvocation(!statement.isThis(), constructor, parameterExpressions);
    }

    private org.e2immu.analyser.model.Statement localClassDeclaration(LocalClassDeclarationStmt statement) {
        String localName = statement.getClassDeclaration().getNameAsString();
        String typeName = StringUtil.capitalise(enclosingMethod.name) + "$" + localName + "$" + anonymousTypeCounters.newIndex(primaryType);
        TypeInfo typeInfo = new TypeInfo(enclosingType, typeName);
        typeContext.typeMapBuilder.add(typeInfo, TypeInspectionImpl.InspectionState.STARTING_JAVA_PARSER);
        TypeInspector typeInspector = new TypeInspector(typeContext.typeMapBuilder, typeInfo, true);
        typeInspector.inspectLocalClassDeclaration(this, statement.getClassDeclaration());

        typeContext.addToContext(localName, typeInfo, true);
        addNewlyCreatedType(typeInfo);
        return new LocalClassDeclaration(typeInfo);
    }

    private org.e2immu.analyser.model.Statement synchronizedStatement(SynchronizedStmt statement) {
        org.e2immu.analyser.model.Expression expression = parseExpression(statement.getExpression());
        ExpressionContext context = newVariableContext("synchronized-block");
        Block block = context.parseBlockOrStatement(statement.getBody());
        inherit(context);
        return new SynchronizedStatement(expression, block);
    }

    private void inherit(ExpressionContext context) {
        newlyCreatedTypes.addAll(context.newlyCreatedTypes);
    }

    private org.e2immu.analyser.model.Statement forEachStatement(String label, ForEachStmt forEachStmt) {
        return ParseForEachStmt.parse(this, label, forEachStmt);
    }

    private org.e2immu.analyser.model.Statement ifThenElseStatement(IfStmt statement) {
        org.e2immu.analyser.model.Expression conditional = parseExpression(statement.getCondition());
        ExpressionContext ifContext = newVariableContext("if-block");
        Block ifBlock = ifContext.parseBlockOrStatement(statement.getThenStmt());
        inherit(ifContext);
        Block elseBlock;
        if (statement.getElseStmt().isPresent()) {
            ExpressionContext elseContext = newVariableContext("else-block");
            elseBlock = elseContext.parseBlockOrStatement(statement.getElseStmt().get());
            inherit(elseContext);
        } else {
            elseBlock = Block.EMPTY_BLOCK;
        }
        return new IfElseStatement(conditional, ifBlock, elseBlock);
    }

    @NotNull
    public org.e2immu.analyser.model.Expression parseExpression(@NotNull com.github.javaparser.ast.expr.Expression expression) {
        return parseExpression(expression, forwardReturnTypeInfo);
    }

    public org.e2immu.analyser.model.Expression parseExpression(@NotNull com.github.javaparser.ast.expr.Expression expression,
                                                                ForwardReturnTypeInfo forwardReturnTypeInfo) {
        assert forwardReturnTypeInfo != null;
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
                return new Cast(parseExpression(castExpr.getExpression()), parameterizedType);
            }
            if (expression.isBinaryExpr()) {
                BinaryExpr binaryExpr = (BinaryExpr) expression;
                org.e2immu.analyser.model.Expression lhs = parseExpression(binaryExpr.getLeft());
                org.e2immu.analyser.model.Expression rhs = parseExpression(binaryExpr.getRight());
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
                return new BinaryOperator(typeContext.getPrimitives(), lhs, operatorMethod, rhs,
                        BinaryOperator.precedence(typeContext.getPrimitives(), operatorMethod));
            }
            if (expression.isUnaryExpr()) {
                UnaryExpr unaryExpr = (UnaryExpr) expression;
                org.e2immu.analyser.model.Expression exp = parseExpression(unaryExpr.getExpression());
                ParameterizedType pt = exp.returnType();
                if (pt.typeInfo == null) throw new UnsupportedOperationException("??");
                MethodInfo operator = UnaryOperator.getOperator(typeContext.getPrimitives(), unaryExpr.getOperator(), pt.typeInfo);
                Primitives primitives = typeContext.getPrimitives();
                if (primitives.isPreOrPostFixOperator(operator)) {
                    boolean isPrefix = primitives.isPrefixOperator(operator);
                    MethodInfo associatedAssignment = primitives.prePostFixToAssignment(operator);
                    return new Assignment(typeContext.getPrimitives(),
                            exp, new IntConstant(typeContext.getPrimitives(), 1), associatedAssignment, isPrefix,
                            true);
                }
                return new UnaryOperator(
                        operator,
                        exp,
                        UnaryOperator.precedence(unaryExpr.getOperator())
                );
            }
            if (expression.isThisExpr()) {
                ThisExpr thisExpr = expression.asThisExpr();
                Variable variable = thisExpr.getTypeName().map(typeName -> {
                    NamedType superType = typeContext.get(typeName.asString(), true);
                    if (!(superType instanceof TypeInfo)) throw new UnsupportedOperationException();
                    return new This(typeContext, (TypeInfo) superType, true, false);
                }).orElse(new This(typeContext, enclosingType));
                return new VariableExpression(variable);
            }
            if (expression.isSuperExpr()) {
                SuperExpr superExpr = expression.asSuperExpr();
                Variable variable = superExpr.getTypeName().map(typeName -> {
                    NamedType superType = typeContext.get(typeName.asString(), true);
                    if (!(superType instanceof TypeInfo)) throw new UnsupportedOperationException();
                    return new This(typeContext, (TypeInfo) superType, true, true);
                }).orElse(new This(typeContext, enclosingType, false, true));
                return new VariableExpression(variable);
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
                        return ParseFieldAccessExpr.createFieldAccess(this, scope, cit.getNameAsString(), expression.getBegin().orElseThrow());
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
                return ParseObjectCreationExpr.parse(this, objectCreationExpr, forwardReturnTypeInfo);
            }
            if (expression.isVariableDeclarationExpr()) {
                VariableDeclarationExpr vde = (VariableDeclarationExpr) expression;
                VariableDeclarator var = vde.getVariable(0);
                ParameterizedType parameterizedType;
                org.e2immu.analyser.model.Expression initializer;
                boolean isVar = "var".equals(var.getType().asString());
                if (isVar) {
                    // run initializer without info
                    initializer = var.getInitializer()
                            .map(this::parseExpression).orElse(EmptyExpression.EMPTY_EXPRESSION);
                    parameterizedType = initializer.returnType();
                } else {
                    parameterizedType = ParameterizedTypeFactory.from(typeContext, var.getType());
                    initializer = var.getInitializer()
                            .map(i -> parseExpression(i, ForwardReturnTypeInfo.computeSAM(parameterizedType, typeContext)))
                            .orElse(EmptyExpression.EMPTY_EXPRESSION);
                }
                LocalVariable.Builder localVariable = new LocalVariable.Builder()
                        .setName(var.getNameAsString()).setSimpleName(var.getNameAsString())
                        .setParameterizedType(parameterizedType);
                vde.getAnnotations().forEach(ae -> localVariable.addAnnotation(AnnotationInspector.inspect(this, ae)));
                vde.getModifiers().forEach(m -> localVariable.addModifier(LocalVariableModifier.from(m)));
                LocalVariable lv = localVariable.setOwningType(owningType()).build();
                return new LocalVariableCreation(typeContext, lv, initializer, isVar);
            }
            if (expression.isAssignExpr()) {
                AssignExpr assignExpr = (AssignExpr) expression;
                org.e2immu.analyser.model.Expression target = parseExpression(assignExpr.getTarget());
                org.e2immu.analyser.model.Expression value = parseExpression(assignExpr.getValue(),
                        ForwardReturnTypeInfo.computeSAM(target.returnType(), typeContext));
                if (value.returnType().isType() && Primitives.isPrimitiveExcludingVoid(value.returnType()) &&
                        target.returnType().isType() && Primitives.isPrimitiveExcludingVoid(target.returnType())) {
                    ParameterizedType widestType = typeContext.getPrimitives().widestType(value.returnType(), target.returnType());
                    MethodInfo primitiveOperator = Assignment.operator(typeContext.getPrimitives(), assignExpr.getOperator(), widestType.typeInfo);
                    return new Assignment(typeContext.getPrimitives(), target, value, primitiveOperator, null, true);
                }
                return new Assignment(typeContext.getPrimitives(), target, value);
            }
            if (expression.isMethodCallExpr()) {
                return new ParseMethodCallExpr(typeContext).parse(this, expression.asMethodCallExpr(), forwardReturnTypeInfo);
            }
            if (expression.isMethodReferenceExpr()) {
                return ParseMethodReferenceExpr.parse(this, expression.asMethodReferenceExpr(), forwardReturnTypeInfo);
            }
            if (expression.isConditionalExpr()) {
                ConditionalExpr conditionalExpr = (ConditionalExpr) expression;
                org.e2immu.analyser.model.Expression condition = parseExpression(conditionalExpr.getCondition(),
                        new ForwardReturnTypeInfo(typeContext.typeMapBuilder.getPrimitives().booleanParameterizedType, null));
                org.e2immu.analyser.model.Expression ifTrue = parseExpression(conditionalExpr.getThenExpr(),
                        forwardReturnTypeInfo);
                org.e2immu.analyser.model.Expression ifFalse = parseExpression(conditionalExpr.getElseExpr(),
                        forwardReturnTypeInfo);
                return new InlineConditional(typeContext, condition, ifTrue, ifFalse);
            }
            if (expression.isFieldAccessExpr()) {
                return ParseFieldAccessExpr.parse(this, expression.asFieldAccessExpr());
            }
            if (expression.isLambdaExpr()) {
                return ParseLambdaExpr.parse(this, expression.asLambdaExpr(), forwardReturnTypeInfo);
            }
            if (expression.isSwitchExpr()) {
                return ParseSwitchExpr.parse(this, expression.asSwitchExpr());
            }
            if (expression.isArrayCreationExpr()) {
                return ParseArrayCreationExpr.parse(this, expression.asArrayCreationExpr());
            }
            if (expression.isArrayInitializerExpr()) {
                return new ArrayInitializer(typeContext, expression.asArrayInitializerExpr().getValues().stream()
                        .map(this::parseExpression).collect(Collectors.toList()), forwardReturnTypeInfo.type());
            }
            if (expression.isEnclosedExpr()) {
                return new EnclosedExpression(parseExpression(((EnclosedExpr) expression).getInner()));
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
                Expression scope = parseExpression(arrayAccessExpr.getName());
                Expression index = parseExpression(arrayAccessExpr.getIndex());
                return new ArrayAccess(scope, index);
            }
            if (expression.isInstanceOfExpr()) {
                InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
                Expression e = parseExpression(instanceOfExpr.getExpression());
                ParameterizedType type = ParameterizedTypeFactory.from(typeContext, instanceOfExpr.getType());
                LocalVariableReference patternVariable = instanceOfExpr.getPattern().map(pattern ->
                        new LocalVariableReference(new LocalVariable.Builder()
                                .setSimpleName(pattern.getNameAsString())
                                .setName(pattern.getNameAsString())
                                .setOwningType(enclosingType)
                                .setParameterizedType(type)
                                .build())).orElse(null);
                if (patternVariable != null) {
                    variableContext.add(patternVariable);
                }
                return new InstanceOf(typeContext.getPrimitives(), type, e, patternVariable);
            }
            if (expression.isSingleMemberAnnotationExpr()) {
                SingleMemberAnnotationExpr sma = expression.asSingleMemberAnnotationExpr();
                return parseExpression(sma.getMemberValue());
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

    public TypeInfo owningType() {
        return uninspectedEnclosingType != null ? uninspectedEnclosingType : enclosingType;
    }

    public Location getLocation() {
        if (enclosingMethod != null) return new Location(enclosingMethod);
        if (enclosingField != null) return new Location(enclosingField);
        return new Location(enclosingType);
    }
}
