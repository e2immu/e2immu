package org.e2immu.analyser.util;

import java.net.URI;

/**
 * Often, the path is a direct substring of the URI.
 * But when parsing subtypes, the same URI is kept while the path changes.
 * Instances of this type are kept in Identifier.JarIdentifier, so that the source of each type can be tracked.
 *
 * @param path
 * @param uri
 */
public record Source(String path, URI uri) {
    public String stripDotClass() {
        return StringUtil.stripDotClass(path);
    }
}
