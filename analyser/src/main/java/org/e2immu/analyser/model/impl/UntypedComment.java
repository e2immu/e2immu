package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.model.Comment;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.Arrays;

public record UntypedComment(String text) implements Comment {

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder ob = new OutputBuilder().add(Symbol.LEFT_BLOCK_COMMENT);
        OutputBuilder joinedText = Arrays.stream(text.split("\n"))
                .map(line -> new OutputBuilder().add(new Text(line)))
                .collect(OutputBuilder.joining(Space.ONE_IS_NICE_EASY_SPLIT));
        return ob.add(joinedText).add(Symbol.RIGHT_BLOCK_COMMENT);
    }

}
