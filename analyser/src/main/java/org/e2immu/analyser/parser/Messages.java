package org.e2immu.analyser.parser;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull1;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

@NotNull1
@Container
public class Messages {

    @NotNull1
    private final List<Message> messages = new LinkedList<>();

    @Modified
    public void addAll(@NotNull1 @NotModified Messages messages) {
        this.messages.addAll(messages.messages);
    }

    @Modified
    public void add(Message message) {
        this.messages.add(message);
    }

    @NotNull1
    public Stream<Message> getMessageStream() {
        return messages.stream();
    }
}
