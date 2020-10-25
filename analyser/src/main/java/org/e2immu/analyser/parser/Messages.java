package org.e2immu.analyser.parser;

import org.e2immu.annotation.*;

import java.util.*;
import java.util.stream.Stream;

@Container
public class Messages {

    @NotNull1
    private final Set<Message> messages = new HashSet<>();

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
