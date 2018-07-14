/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.type.CollectionType;

/**
 * An {@code IdentifierBag} has a primary key consisting of
 * just the identifier column
 */
public class IdentifierBag extends IdentifierCollection {
	/**
	 * @deprecated User {@link IdentifierBag#IdentifierBag(MetadataBuildingContext, PersistentClass)} instead.
	 */
	@Deprecated
	public IdentifierBag(MetadataImplementor metadata, PersistentClass owner) {
		super( metadata, owner );
	}

	public IdentifierBag(MetadataBuildingContext buildingContext, PersistentClass owner) {
		super( buildingContext, owner );
	}

	public CollectionType getDefaultCollectionType() {
		return getMetadata().getTypeResolver()
				.getTypeFactory()
				.idbag( getRole(), getReferencedPropertyName() );
	}

	public Object accept(ValueVisitor visitor) {
		return visitor.accept(this);
	}	
}
