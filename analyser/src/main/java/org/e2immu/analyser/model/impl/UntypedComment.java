package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.model.Comment;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.output.*;

import java.util.Arrays;

public record UntypedComment(String text) implements Comment {

    public UntypedComment {
        assert text != null && !text.isBlank();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        boolean multiLine = text.contains("\n");
        Guide.GuideGenerator gg = multiLine ? Guide.generatorForMultilineComment() : Guide.defaultGuideGenerator();
        OutputBuilder joinedText = Arrays.stream(text.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> new OutputBuilder().add(new Text(line)))
                .collect(OutputBuilder.joining(Space.ONE_IS_NICE_EASY_SPLIT,
                        Symbol.LEFT_BLOCK_COMMENT,
                        Symbol.RIGHT_BLOCK_COMMENT,
                        gg));
        OutputBuilder ob = new OutputBuilder().add(joinedText);
        //if (multiLine) ob.add(Space.NEWLINE);
        return ob;
    }

}
