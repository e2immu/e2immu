package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;

public class AssertStatement extends StatementWithStructure {

    public final Expression message; // can be null

    public AssertStatement(Expression check, Expression message) {
        // IMPORTANT NOTE: we're currently NOT adding message!
        // we regard it as external to the code
        super(new Structure.Builder()
                .setExpression(check)
                .setExpressionIsCondition(true)
                .build());
        this.message = message;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new AssertStatement(translationMap.translateExpression(structure.expression()), message);
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        return new OutputBuilder()
                .add(new Text("assert"))
                .add(Space.ONE)
                .add(structure.expression().output())
                .add(message != null ? new OutputBuilder().add(Symbol.COMMA).add(message.output()) : new OutputBuilder())
                .add(Symbol.SEMICOLON)
                .addIfNotNull(messageComment(statementAnalysis));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression());
    }
}
