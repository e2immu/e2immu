/*
 * Copyright (c) 2007-2018, MDCPartners cvba, Belgium.
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package org.e2immu.intellij.highlighter.store;

import java.util.function.BiConsumer;

public interface Cache<K, V> {
	V get(K k);

	V put(K k, V v);

	void applyInReadLock(BiConsumer<K, V> f);

	void clear();

	int size();

	V remove(K k);
}
