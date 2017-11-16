/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.test.jpa.compliance;

import javax.persistence.LockModeType;

import org.hibernate.test.jpa.AbstractJPATest;
import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil2.inTransaction;

/**
 * @author Steve Ebersole
 */
public class NonSelectQueryLockMode extends AbstractJPATest {

	@Test( expected = IllegalStateException.class )
	public void testNonSelectQueryGetLockMode() {
		inTransaction(
				sessionFactory(),
				session -> session.createQuery( "delete Item" ).getLockMode()
		);
	}

	@Test( expected = IllegalStateException.class )
	public void testNonSelectQuerySetLockMode() {
		inTransaction(
				sessionFactory(),
				session -> session.createQuery( "delete Item" ).setLockMode( LockModeType.PESSIMISTIC_WRITE )
		);
	}
}
