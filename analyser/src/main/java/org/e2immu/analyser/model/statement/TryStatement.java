package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TryStatement implements Statement {
    public final List<Expression> resources;
    public final Block tryBlock;
    public final List<Pair<CatchParameter, Block>> catchClauses;
    public final Block finallyBlock;

    public TryStatement(List<Expression> resources,
                        Block tryBlock,
                        List<Pair<CatchParameter, Block>> catchClauses,
                        Block finallyBlock) {
        this.resources = ImmutableList.copyOf(resources);
        this.tryBlock = tryBlock;
        this.catchClauses = ImmutableList.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
    }

    public static class CatchParameter implements Expression {
        public final String name;
        public final List<ParameterizedType> unionOfTypes;

        public CatchParameter(String name, List<ParameterizedType> unionOfTypes) {
            this.name = name;
            this.unionOfTypes = ImmutableList.copyOf(unionOfTypes);
        }

        @Override
        public ParameterizedType returnType() {
            return null;
        }

        @Override
        public String expressionString(int indent) {
            return null;
        }

        @Override
        public int precedence() {
            return 0;
        }
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("try");
        if (!resources.isEmpty()) {
            sb.append("(");
            sb.append(resources.stream().map(r -> r.expressionString(0)).collect(Collectors.joining("; ")));
            sb.append(")");
        }
        sb.append(tryBlock.statementString(indent));
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            sb.append(" catch(");
            sb.append(pair.k.expressionString(0));
            sb.append(")");
            sb.append(pair.v.statementString(indent));
        }
        if (tryBlock != Block.EMPTY_BLOCK) {
            sb.append(" finally");
            sb.append(finallyBlock.statementString(indent));
        }
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        Set<String> importsOfResources = resources.stream().flatMap(r -> r.imports().stream()).collect(Collectors.toSet());
        Set<String> importsOfCatchParameters = catchClauses.stream().flatMap(c -> c.k.imports().stream()).collect(Collectors.toSet());
        Set<String> importsOfCatchBlocks = catchClauses.stream().flatMap(c -> c.v.imports().stream()).collect(Collectors.toSet());
        return SetUtil.immutableUnion(tryBlock.imports(), finallyBlock.imports(), importsOfResources, importsOfCatchBlocks, importsOfCatchParameters);
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization(null,
                ListUtil.immutableConcat(
                        List.of(new CodeOrganization.ExpressionsWithStatements(resources, tryBlock)),
                        catchClauses.stream().map(cc -> new CodeOrganization.ExpressionsWithStatements(List.of(cc.k), cc.v)).collect(Collectors.toList()),
                        finallyBlock != Block.EMPTY_BLOCK ? List.of(new CodeOrganization.ExpressionsWithStatements(List.of(), finallyBlock)) : List.of()
                ));
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return tryBlock.sideEffect(sideEffectContext);
    }

}
