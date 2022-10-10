package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record EvaluatedExpressionCache(Map<Identifier, List<Expression>> map) {
    public static final EvaluatedExpressionCache EMPTY = new EvaluatedExpressionCache(Map.of());

    public EvaluatedExpressionCache combine(EvaluatedExpressionCache other) {
        if (other.map.isEmpty()) return this;
        if (map.isEmpty()) return other;
        return new EvaluatedExpressionCache(Stream.concat(map.entrySet().stream(), other.map.entrySet().stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public void add(Identifier identifier, Expression expression) {
        map.merge(identifier, List.of(expression), (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).toList());
    }

    public void add(Identifier identifier, List<Expression> expressions) {
        map.merge(identifier, expressions, (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).toList());
    }

    public EvaluatedExpressionCache translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        if (map.isEmpty()) return this;
        return new EvaluatedExpressionCache(map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e ->
                        e.getValue().stream().map(ex -> ex.translate(inspectionProvider, translationMap)).toList())));
    }

    public CausesOfDelay delays() {
        return map.values().stream().flatMap(Collection::stream)
                .map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }
}
