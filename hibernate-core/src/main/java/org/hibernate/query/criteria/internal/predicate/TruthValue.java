/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.criteria.internal.predicate;


/**
 * Models what ANSI-SQL terms a <i>truth value</i>.  Specifically, ANSI-SQL defines {@code TRUE}, {@code FALSE} and
 * {@code UNKNOWN} as <i>truth values</i>.  These <i>truth values</i> are used to explicitly check the result of a
 * boolean expression (the syntax is like {@code a > b IS TRUE}.  {@code IS TRUE} is the assumed default.
 * <p>
 * JPA defines support for only {@code IS TRUE} and {@code IS FALSE}, not {@code IS UNKNOWN} ({@code a > NULL}
 * is an example where the result would be UNKNOWN).  All 3 are provided here for completness.
 *
 * @author Steve Ebersole
 */
public enum TruthValue {
	TRUE,
	FALSE,
	UNKNOWN
}
