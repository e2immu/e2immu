package org.e2immu.analyser.model.impl;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import org.e2immu.analyser.model.Comment;

import java.util.stream.Collectors;

public class CommentFactory {
    public static Comment from(Node node) {
        if (node.getComment().isPresent()) {
            String comment = node.getComment().get().getContent().trim();

            String adjacentOrphans = node.getParentNode()
                    .map(parent -> parent.getOrphanComments().stream()
                            .filter(c -> isAdjacent(parent, c.getBegin().orElseThrow(), node.getBegin().orElseThrow()))
                            .map(com.github.javaparser.ast.comments.Comment::getContent)
                            .map(String::trim)
                            .collect(Collectors.joining("\n")))
                    .orElse("");
            String combined = adjacentOrphans.isEmpty() ? comment : adjacentOrphans + "\n" + comment;
            String trim = combined.trim();
            if (trim.isBlank()) return null;
            return new UntypedComment(trim);
        }
        return null;
    }

    /*
    TestBasics_8
     */
    private static boolean isAdjacent(Node parent, Position orphanBegin, Position commentBegin) {
        int distance = commentBegin.line - orphanBegin.line;
        if (distance < 0) return false;
        return parent.getChildNodes().stream().noneMatch(child -> {
            // child nodes which are not comments
            if (child instanceof com.github.javaparser.ast.comments.Comment) return false;
            // distance between child node and begin of my comment
            int diff = commentBegin.line - child.getBegin().orElseThrow().line;
            // should be positive, but not smaller than the one for the orphan
            return diff > 0 && diff < distance;
        });
    }
}
