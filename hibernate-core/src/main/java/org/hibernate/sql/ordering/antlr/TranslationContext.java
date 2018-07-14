/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.ordering.antlr;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.function.SQLFunctionRegistry;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Contract for contextual information required to perform translation.
*
* @author Steve Ebersole
*/
public interface TranslationContext {
	/**
	 * Retrieves the {@code session factory} for this context.
	 *
	 * @return The {@code session factory}
	 */
	public SessionFactoryImplementor getSessionFactory();

	/**
	 * Retrieves the {@code dialect} for this context.
	 *
	 * @return The {@code dialect}
	 */
	public Dialect getDialect();

	/**
	 * Retrieves the SQL function registry for this context.
	 *
	 * @return The SQL function registry.
	 */
	public SQLFunctionRegistry getSqlFunctionRegistry();

	/**
	 * Retrieves the {@code column mapper} for this context.
	 *
	 * @return The {@code column mapper}
	 */
	public ColumnMapper getColumnMapper();
}
