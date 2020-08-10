package org.e2immu.analyser.parser;

import org.e2immu.annotation.*;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

@Container
public class Messages {

    @NotNull1
    private final List<Message> messages = new LinkedList<>();

    @Modified
    public void addAll(@NotNull1 @NotModified Messages messages) {
        this.messages.addAll(messages.messages);
    }

    @Modified
    public void addAll(@NotNull1 @NotModified Stream<Message> messageStream) {
        messageStream.forEach(messages::add);
    }

    @Modified
    public void add(@NotNull Message message) {
        this.messages.add(message);
    }

    @NotNull1
    public Stream<Message> getMessageStream() {
        return messages.stream();
    }
}
