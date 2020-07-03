package org.e2immu.reactive;

import reactor.core.publisher.Flux;

import java.util.Set;

public class Example {
    public final Set<String> set = Set.of("a", "b", "c");

    public static Flux<String> constantFlux() {
        return Flux.just("aa", "bb", "cc");
    }

    public Flux<String> fromSet() {
        return Flux.fromIterable(set);
    }

    public Flux<String> join() {
        return Flux.concat(fromSet(), constantFlux());
    }

    public static void main(String... args) {
        new Example().join()
                .map(s -> ":" + s)
                .subscribe(System.out::println);
    }
}
