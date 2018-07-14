/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.criteria.internal.predicate;

import java.io.Serializable;
import javax.persistence.criteria.Expression;

import org.hibernate.query.criteria.internal.CriteriaBuilderImpl;
import org.hibernate.query.criteria.internal.ParameterRegistry;
import org.hibernate.query.criteria.internal.Renderable;
import org.hibernate.query.criteria.internal.compile.RenderingContext;

/**
 * ANSI-SQL defines {@code TRUE}, {@code FALSE} and {@code UNKNOWN} as <i>truth values</i>.  These
 * <i>truth values</i> are used to explicitly check the result of a boolean expression (the syntax is like
 * {@code a > b IS TRUE}.  {@code IS TRUE} is the assumed default.
 * <p>
 * JPA defines support for only {@code IS TRUE} and {@code IS FALSE}, not {@code IS UNKNOWN} ({@code a > NULL}
 * is an example where the result would be UNKNOWN.
 *
 * @author Steve Ebersole
 */
public class ExplicitTruthValueCheck
		extends AbstractSimplePredicate
		implements Serializable {
	// TODO : given that JPA supports only TRUE and FALSE, can this be handled just with negation?
	private final Expression<Boolean> booleanExpression;
	private final TruthValue truthValue;

	public ExplicitTruthValueCheck(CriteriaBuilderImpl criteriaBuilder, Expression<Boolean> booleanExpression, TruthValue truthValue) {
		super( criteriaBuilder );
		this.booleanExpression = booleanExpression;
		this.truthValue = truthValue;
	}

	public Expression<Boolean> getBooleanExpression() {
		return booleanExpression;
	}

	public TruthValue getTruthValue() {
		return truthValue;
	}

	@Override
	public void registerParameters(ParameterRegistry registry) {
		Helper.possibleParameter( getBooleanExpression(), registry );
	}

	@Override
	public String render(boolean isNegated, RenderingContext renderingContext) {
		return ( (Renderable) getBooleanExpression() ).render( renderingContext )
				+ ( isNegated ? " <> " : " = " )
				+ ( getTruthValue() == TruthValue.TRUE ? "true" : "false" );
	}
}
