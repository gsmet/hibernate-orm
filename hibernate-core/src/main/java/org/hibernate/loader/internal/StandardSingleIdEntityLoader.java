/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.loader.internal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.LoadQueryInfluencers.InternalFetchProfileType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.loader.spi.SingleIdEntityLoader;
import org.hibernate.metamodel.model.domain.spi.EntityDescriptor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.JoinType;
import org.hibernate.sql.ast.consume.spi.SqlAstSelectToJdbcSelectConverter;
import org.hibernate.sql.ast.produce.internal.SqlAstSelectDescriptorImpl;
import org.hibernate.sql.ast.produce.internal.StandardSqlExpressionResolver;
import org.hibernate.sql.ast.produce.metamodel.internal.LoadIdParameter;
import org.hibernate.sql.ast.produce.metamodel.internal.SelectByEntityIdentifierBuilder;
import org.hibernate.sql.ast.produce.metamodel.spi.SqlAliasBaseGenerator;
import org.hibernate.sql.ast.produce.metamodel.spi.TableGroupInfo;
import org.hibernate.sql.ast.produce.spi.RootTableGroupContext;
import org.hibernate.sql.ast.produce.spi.SqlAliasBaseManager;
import org.hibernate.sql.ast.produce.spi.SqlAstSelectDescriptor;
import org.hibernate.sql.ast.produce.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.produce.sqm.spi.Callback;
import org.hibernate.sql.ast.tree.spi.QuerySpec;
import org.hibernate.sql.ast.tree.spi.SelectStatement;
import org.hibernate.sql.ast.tree.spi.expression.Expression;
import org.hibernate.sql.ast.tree.spi.expression.SqlTuple;
import org.hibernate.sql.ast.tree.spi.from.EntityTableGroup;
import org.hibernate.sql.ast.tree.spi.from.TableSpace;
import org.hibernate.sql.ast.tree.spi.predicate.Predicate;
import org.hibernate.sql.ast.tree.spi.predicate.RelationalPredicate;
import org.hibernate.sql.exec.internal.JdbcSelectExecutorStandardImpl;
import org.hibernate.sql.exec.internal.LoadParameterBindingContext;
import org.hibernate.sql.exec.internal.RowTransformerSingularReturnImpl;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameter;
import org.hibernate.sql.exec.spi.JdbcParameterBinding;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.hibernate.sql.exec.spi.ParameterBindingContext;
import org.hibernate.sql.results.internal.ScalarQueryResultImpl;
import org.hibernate.sql.results.spi.QueryResult;
import org.hibernate.sql.results.spi.SqlAstCreationContext;

/**
 * @author Steve Ebersole
 */
public class StandardSingleIdEntityLoader<T> implements SingleIdEntityLoader<T> {
	private final EntityDescriptor<T> entityDescriptor;

	private final SqlAstSelectDescriptor databaseSnapshotSelectAst;

	private EnumMap<LockMode,JdbcSelect> selectByLockMode = new EnumMap<>( LockMode.class );
	private EnumMap<InternalFetchProfileType,JdbcSelect> selectByInternalCascadeProfile;

	public StandardSingleIdEntityLoader(EntityDescriptor<T> entityDescriptor) {
		this.entityDescriptor = entityDescriptor;

		this.databaseSnapshotSelectAst = generateDatabaseSnapshotSelect( entityDescriptor );

// todo (6.0) : re-enable this pre-caching after model processing is more fully complete
//		ParameterBindingContext context = new TemplateParameterBindingContext( entityDescriptor.getFactory(), 1 );
//		final JdbcSelect base = createJdbcSelect( LockOptions.READ, LoadQueryInfluencers.NONE, context );
//
//		selectByLockMode.put( LockMode.NONE, base );
//		selectByLockMode.put( LockMode.READ, base );
	}


	private static class JdbcParameterBindingsImpl implements JdbcParameterBindings {
		@Override
		public JdbcParameterBinding getBinding(JdbcParameter parameter) {
			return null;
		}
	}

	@Override
	public T load(Object id, LoadOptions loadOptions, SharedSessionContractImplementor session) {
		final ParameterBindingContext parameterBindingContext = new LoadParameterBindingContext(
				session.getFactory(),
				id
		);

		final List<JdbcParameter> jdbcParameters = new ArrayList<>();
		final JdbcParameterBindingsImpl jdbcParameterBindings = new JdbcParameterBindingsImpl();

		final JdbcSelect jdbcSelect = resolveJdbcSelect(
				id,
				loadOptions.getLockOptions(),
				parameterBindingContext,
				jdbcParameters,
				jdbcParameterBindings,
				session
		);

		final List<T> list = JdbcSelectExecutorStandardImpl.INSTANCE.list(
				jdbcSelect,
				new ExecutionContext() {
					@Override
					public SharedSessionContractImplementor getSession() {
						return session;
					}

					@Override
					public QueryOptions getQueryOptions() {
						return QueryOptions.NONE;
					}

					@Override
					public ParameterBindingContext getParameterBindingContext() {
						return parameterBindingContext;
					}

					@Override
					public JdbcParameterBindings getJdbcParameterBindings() {
						return jdbcParameterBindings;
					}

					@Override
					public Callback getCallback() {
						return null;
					}
				},
				RowTransformerSingularReturnImpl.instance()
		);

		if ( list.isEmpty() ) {
			return null;
		}

		return list.get( 0 );
	}

	private JdbcSelect resolveJdbcSelect(
			Object id,
			LockOptions lockOptions,
			ParameterBindingContext parameterBindingContext,
			List<JdbcParameter> jdbcParameters,
			JdbcParameterBindingsImpl jdbcParameterBindings,
			SharedSessionContractImplementor session) {
		final LoadQueryInfluencers loadQueryInfluencers = session.getLoadQueryInfluencers();
		if ( entityDescriptor.isAffectedByEnabledFilters( session ) ) {
			// special case of not-cacheable based on enabled filters effecting this load.
			//
			// This case is special because the filters need to be applied in order to
			// 		properly restrict the SQL/JDBC results.  For this reason it has higher
			// 		precedence than even "internal" fetch profiles.
			return createJdbcSelect( lockOptions, loadQueryInfluencers, parameterBindingContext );
		}

		if ( loadQueryInfluencers.getEnabledInternalFetchProfileType() != null ) {
			if ( LockMode.UPGRADE.greaterThan( lockOptions.getLockMode() ) ) {
				if ( selectByInternalCascadeProfile == null ) {
					selectByInternalCascadeProfile = new EnumMap<>( InternalFetchProfileType.class );
				}
				return selectByInternalCascadeProfile.computeIfAbsent(
						loadQueryInfluencers.getEnabledInternalFetchProfileType(),
						internalFetchProfileType -> createJdbcSelect( lockOptions, loadQueryInfluencers, parameterBindingContext )
				);
			}
		}

		// otherwise see if the loader for the requested load can be cached - which
		// 		also means we should look in the cache for an existing one

		final boolean cacheable = determineIfCacheable( lockOptions, loadQueryInfluencers );

		if ( cacheable ) {
			return selectByLockMode.computeIfAbsent(
					lockOptions.getLockMode(),
					lockMode -> createJdbcSelect( lockOptions, loadQueryInfluencers, parameterBindingContext )
			);
		}

		return createJdbcSelect(
				lockOptions,
				loadQueryInfluencers,
				parameterBindingContext
		);

	}

	private JdbcSelect createJdbcSelect(
			LockOptions lockOptions,
			LoadQueryInfluencers queryInfluencers,
			ParameterBindingContext parameterBindingContext ) {
		final SelectByEntityIdentifierBuilder selectBuilder = new SelectByEntityIdentifierBuilder(
				entityDescriptor.getFactory(),
				entityDescriptor
		);
		final SqlAstSelectDescriptor selectDescriptor = selectBuilder
				.generateSelectStatement( 1, queryInfluencers, lockOptions );


		return SqlAstSelectToJdbcSelectConverter.interpret(
				selectDescriptor,
				parameterBindingContext
		);
	}
	@SuppressWarnings("RedundantIfStatement")
	private boolean determineIfCacheable(LockOptions lockOptions, LoadQueryInfluencers loadQueryInfluencers) {
		if ( entityDescriptor.isAffectedByEntityGraph( loadQueryInfluencers ) ) {
			return false;
		}

		if ( lockOptions.getTimeOut() == LockOptions.WAIT_FOREVER ) {
			return false;
		}

		return true;
	}

	@Override
	public Object[] loadDatabaseSnapshot(Object id, SharedSessionContractImplementor session) {
		final ParameterBindingContext parameterBindingContext = new LoadParameterBindingContext(
				session.getFactory(),
				id
		);

		final JdbcSelect jdbcSelect = SqlAstSelectToJdbcSelectConverter.interpret(
				databaseSnapshotSelectAst,
				parameterBindingContext
		);

		final List<T> list = JdbcSelectExecutorStandardImpl.INSTANCE.list(
				jdbcSelect,
				new ExecutionContext() {
					@Override
					public SharedSessionContractImplementor getSession() {
						return session;
					}

					@Override
					public QueryOptions getQueryOptions() {
						return QueryOptions.NONE;
					}

					@Override
					public ParameterBindingContext getParameterBindingContext() {
						return parameterBindingContext;
					}

					@Override
					public Callback getCallback() {
						return afterLoadAction -> {};
					}
				},
				null
		);

		if ( list.isEmpty() ) {
			return null;
		}

		return (Object[]) list.get( 0 );
	}

	private static SqlAstSelectDescriptor generateDatabaseSnapshotSelect(EntityDescriptor<?> entityDescriptor) {
		final QuerySpec rootQuerySpec = new QuerySpec( true );
		final SelectStatement selectStatement = new SelectStatement( rootQuerySpec );

		final TableSpace rootTableSpace = rootQuerySpec.getFromClause().makeTableSpace();

		final SqlAliasBaseGenerator aliasBaseGenerator = new SqlAliasBaseManager();

		final EntityTableGroup rootTableGroup = entityDescriptor.createRootTableGroup(
				new TableGroupInfo() {
					@Override
					public String getUniqueIdentifier() {
						return "root";
					}

					@Override
					public String getIdentificationVariable() {
						return null;
					}

					@Override
					public EntityDescriptor getIntrinsicSubclassEntityMetadata() {
						return entityDescriptor;
					}
				},
				new RootTableGroupContext() {
					@Override
					public void addRestriction(Predicate predicate) {
						rootQuerySpec.addRestriction( predicate );
					}

					@Override
					public QuerySpec getQuerySpec() {
						return rootQuerySpec;
					}

					@Override
					public TableSpace getTableSpace() {
						return rootTableSpace;
					}

					@Override
					public SqlAliasBaseGenerator getSqlAliasBaseGenerator() {
						return aliasBaseGenerator;
					}

					@Override
					public JoinType getTableReferenceJoinType() {
						return null;
					}

					@Override
					public LockOptions getLockOptions() {
						return LockOptions.NONE;
					}
				}
		);

		final List<QueryResult> queryResults = new ArrayList<>();

		final SqlExpressionResolver sqlExpressionResolver = new StandardSqlExpressionResolver(
				() -> rootQuerySpec,
				expression -> expression,
				(expression, sqlSelection) -> {
					queryResults.add(
							new ScalarQueryResultImpl(
									null,
									sqlSelection,
									expression.getType()
							)
					);
				}
		);

		final SqlAstCreationContext creationContext = new SqlAstCreationContext() {
			@Override
			public SessionFactoryImplementor getSessionFactory() {
				return entityDescriptor.getFactory();
			}

			@Override
			public SqlExpressionResolver getSqlSelectionResolver() {
				return sqlExpressionResolver;
			}
		};

		final List columnReferences = entityDescriptor.getHierarchy()
				.getIdentifierDescriptor()
				.resolveColumnReferences( rootTableGroup, creationContext );
		final Expression idExpression;
		if ( columnReferences.size() == 1 ) {
			idExpression = (Expression) columnReferences.get( 0 );
		}
		else {
			idExpression = new SqlTuple( columnReferences );
		}

		rootQuerySpec.addRestriction(
				new RelationalPredicate(
						RelationalPredicate.Operator.EQUAL,
						idExpression,
						new LoadIdParameter(
								entityDescriptor.getHierarchy().getIdentifierDescriptor(),
								creationContext.getSessionFactory().getTypeConfiguration()
						)
				)
		);

		return new SqlAstSelectDescriptorImpl(
				selectStatement,
				queryResults,
				entityDescriptor.getAffectedTableNames()
		);
	}
}
