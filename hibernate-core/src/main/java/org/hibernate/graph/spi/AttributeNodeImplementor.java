/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.graph.spi;

import javax.persistence.AttributeNode;
import javax.persistence.metamodel.Attribute;

/**
 * @author Strong Liu &lt;stliu@hibernate.org&gt;
 */
public interface AttributeNodeImplementor<T> extends AttributeNode<T> {
	public Attribute<?,T> getAttribute();
	public AttributeNodeImplementor<T> makeImmutableCopy();
}
