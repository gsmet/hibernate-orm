/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.Oracle12cIdentityColumnSupport;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.SQL2008StandardLimitHandler;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.config.spi.StandardConverters;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.MaterializedBlobType;
import org.hibernate.type.WrappedMaterializedBlobType;

/**
 * An SQL dialect for Oracle 12c.
 *
 * @author zhouyanming (zhouyanming@gmail.com)
 */
public class Oracle12cDialect extends Oracle10gDialect {
	public static final String PREFER_LONG_RAW = "hibernate.dialect.oracle.prefer_long_raw";

	public Oracle12cDialect() {
		super();
		getDefaultProperties().setProperty( Environment.BATCH_VERSIONED_DATA, "true" );
	}

	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );

		// account for Oracle's deprecated support for LONGVARBINARY...
		// 		prefer BLOB, unless the user opts out of it
		boolean preferLong = serviceRegistry.getService( ConfigurationService.class ).getSetting(
				PREFER_LONG_RAW,
				StandardConverters.BOOLEAN,
				false
		);

		if ( !preferLong ) {
			typeContributions.contributeType( MaterializedBlobType.INSTANCE, "byte[]", byte[].class.getName() );
			typeContributions.contributeType( WrappedMaterializedBlobType.INSTANCE, "Byte[]", Byte[].class.getName() );
		}
	}

	@Override
	protected void registerDefaultProperties() {
		super.registerDefaultProperties();
		getDefaultProperties().setProperty( Environment.USE_GET_GENERATED_KEYS, "true" );
	}

	@Override
	public LimitHandler getLimitHandler() {
		return SQL2008StandardLimitHandler.INSTANCE;
	}

	@Override
	public boolean useFollowOnLocking(QueryParameters parameters) {
		// You can't use the SQL 2008 limit handling with for update
		return super.useFollowOnLocking( parameters ) ||
				( parameters.hasRowSelection() && parameters.getRowSelection().definesLimits() );
	}

	@Override
	public String getNativeIdentifierGeneratorStrategy() {
		return "sequence";
	}

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		return new Oracle12cIdentityColumnSupport();
	}

	@Override
	public String getForUpdateString(String aliases) {
		StringBuilder sb = new StringBuilder();
		sb.append( getForUpdateString() );
		if ( StringHelper.isNotEmpty( aliases ) ) {
			sb.append( " of " ).append( aliases );
		}
		return sb.toString();
	}

	@Override
	public String getForUpdateNowaitString(String aliases) {
		return getForUpdateString( aliases ) + " nowait";
	}

	/*
	 * Overwrite because the parent's implementation does not support the `for update of ...` syntax.
	 *
	 * Since Oracle 8i (or even prior versions) the syntax of "for update of [table.column]" is already supported.
	 * Refer to https://docs.oracle.com/cd/A87860_01/doc/server.817/a85397/state21b.htm#2065648
	 */
	@Override
	public String getForUpdateString(String aliases, LockOptions lockOptions) {
		LockMode lockMode = lockOptions.getLockMode();
		final Iterator<Map.Entry<String, LockMode>> itr = lockOptions.getAliasLockIterator();
		Set<String> tableAliasSet = new HashSet<>();
		while ( itr.hasNext() ) {
			// seek the highest lock mode
			final Map.Entry<String, LockMode> entry = itr.next();
			tableAliasSet.add( entry.getKey() );
			final LockMode lm = entry.getValue();
			if ( lm.greaterThan( lockMode ) ) {
				lockMode = lm;
			}
		}
		lockOptions.setLockMode( lockMode );
		if ( needToSpecifyAliasesInForUpdate( tableAliasSet, aliases ) ) {
			return getForUpdateString( lockMode, lockOptions.getTimeOut(), aliases );
		}
		else {
			return getForUpdateString( lockOptions );
		}
	}

	@Override
	public String getReadLockString(String aliases, int timeout) {
		return forUpdateFragment( aliases, timeout );
	}

	@Override
	public String getWriteLockString(String aliases, int timeout) {
		if ( timeout == LockOptions.SKIP_LOCKED ) {
			return getForUpdateSkipLockedString( aliases );
		}
		else {
			return forUpdateFragment( aliases, timeout );
		}
	}

	/*
	 * Avoid using 'update of [table.column]' syntax if the given aliasesToLock are actually all tables.
	 *
	 * The generated for-update clause varies in below scenarios:
	 *
	 * 1. createQuery("from A a").setLockMode( "a", LockMode.PESSIMISTIC_WRITE )
	 *     Result in `for update` only, because there is only one table in the query.
	 *
	 * 2. createQuery("from A a").setLockMode( LockMode.PESSIMISTIC_WRITE )
	 *     Result in `for update` only, because the user did not intent to lock on specific alias at all.
	 *
	 * 3. createQuery("from A a join fetch a.b").setLockMode( "b", LockMode.PESSIMISTIC_WRITE )
	 *     Result in `for update of b0_.id`, to only lock on the alias requested by the user.
	 */
	private boolean needToSpecifyAliasesInForUpdate(Set<String> tableAliasSet, String aliasesToLock) {
		if ( StringHelper.isNotEmpty( aliasesToLock ) ) {
			String[] tableAliasWithIdColumns = StringHelper.split( ",", aliasesToLock );
			HashSet<String> tableAliasToLock = new HashSet<>();
			for ( String tableAliasWithIdColumn : tableAliasWithIdColumns ) {
				int indexOfDot = tableAliasWithIdColumn.indexOf( "." );
				String tableAlias = indexOfDot == -1
						? tableAliasWithIdColumn
						: tableAliasWithIdColumn.substring( 0, indexOfDot );
				tableAliasToLock.add( tableAlias );
			}

			return !tableAliasSet.equals( tableAliasToLock );
		}

		return false;
	}

	private String getForUpdateString(LockMode lockMode, int timeout, String aliases) {
		switch ( lockMode ) {
			case UPGRADE:
				return getForUpdateString( aliases );
			case PESSIMISTIC_READ:
				return getReadLockString( aliases, timeout );
			case PESSIMISTIC_WRITE:
				return getWriteLockString( aliases, timeout );
			case UPGRADE_NOWAIT:
			case FORCE:
			case PESSIMISTIC_FORCE_INCREMENT:
				return getForUpdateNowaitString( aliases );
			case UPGRADE_SKIPLOCKED:
				return getForUpdateSkipLockedString( aliases );
			default:
				return "";
		}
	}

	private String forUpdateFragment(String aliases, int timeout) {
		StringBuilder forUpdateFragment = new StringBuilder( getForUpdateString() );

		// refer to https://docs.oracle.com/database/121/SQLRF/statements_10002.htm#i2126016
		if ( StringHelper.isNotEmpty( aliases ) ) {
			forUpdateFragment.append( " of " ).append( aliases );
		}

		if ( timeout == LockOptions.NO_WAIT ) {
			forUpdateFragment.append( " nowait" );
		}
		else if ( timeout == LockOptions.SKIP_LOCKED ) {
			forUpdateFragment.append( " skip locked" );
		}
		else if ( timeout > 0 ) {
			// convert from milliseconds to seconds
			final float seconds = timeout / 1000.0f;
			forUpdateFragment.append( " wait " ).append( Math.round( seconds ) );
		}

		return forUpdateFragment.toString();
	}
}
