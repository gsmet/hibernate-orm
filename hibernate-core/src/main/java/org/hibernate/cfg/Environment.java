/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hibernate.HibernateException;
import org.hibernate.Version;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.log.UnsupportedLogger;
import org.hibernate.internal.util.ConfigHelper;
import org.hibernate.internal.util.config.ConfigurationHelper;

import org.jboss.logging.Logger;


/**
 * Provides access to configuration info passed in {@code Properties} objects.
 * <br><br>
 * Hibernate has two property scopes:
 * <ul>
 * <li><b>Factory-level</b> properties may be passed to the {@code SessionFactory} when it
 * instantiated. Each instance might have different property values. If no
 * properties are specified, the factory calls {@code Environment.getProperties()}.
 * <li><b>System-level</b> properties are shared by all factory instances and are always
 * determined by the {@code Environment} properties.
 * </ul>
 * The only system-level properties are
 * <ul>
 * <li>{@code hibernate.jdbc.use_streams_for_binary}
 * <li>{@code hibernate.cglib.use_reflection_optimizer}
 * </ul>
 * {@code Environment} properties are populated by calling {@code System.getProperties()}
 * and then from a resource named {@code /hibernate.properties} if it exists. System
 * properties override properties specified in {@code hibernate.properties}.<br>
 * <br>
 * The {@code SessionFactory} is controlled by the following properties.
 * Properties may be either be {@code System} properties, properties
 * defined in a resource named {@code /hibernate.properties} or an instance of
 * {@code java.util.Properties} passed to
 * {@code Configuration.build()}<br>
 * <br>
 * <table>
 * <tr><td><b>property</b></td><td><b>meaning</b></td></tr>
 * <tr>
 *   <td>{@code hibernate.dialect}</td>
 *   <td>classname of {@code org.hibernate.dialect.Dialect} subclass</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.connection.provider_class}</td>
 *   <td>classname of {@code ConnectionProvider}
 *   subclass (if not specified hueristics are used)</td>
 * </tr>
 * <tr><td>{@code hibernate.connection.username}</td><td>database username</td></tr>
 * <tr><td>{@code hibernate.connection.password}</td><td>database password</td></tr>
 * <tr>
 *   <td>{@code hibernate.connection.url}</td>
 *   <td>JDBC URL (when using {@code java.sql.DriverManager})</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.connection.driver_class}</td>
 *   <td>classname of JDBC driver</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.connection.isolation}</td>
 *   <td>JDBC transaction isolation level (only when using
 *     {@code java.sql.DriverManager})
 *   </td>
 * </tr>
 *   <td>{@code hibernate.connection.pool_size}</td>
 *   <td>the maximum size of the connection pool (only when using
 *     {@code java.sql.DriverManager})
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.connection.datasource}</td>
 *   <td>databasource JNDI name (when using {@code javax.sql.Datasource})</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.jndi.url}</td><td>JNDI {@code InitialContext} URL</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.jndi.class}</td><td>JNDI {@code InitialContext} classname</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.max_fetch_depth}</td>
 *   <td>maximum depth of outer join fetching</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.jdbc.batch_size}</td>
 *   <td>enable use of JDBC2 batch API for drivers which support it</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.jdbc.fetch_size}</td>
 *   <td>set the JDBC fetch size</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.jdbc.use_scrollable_resultset}</td>
 *   <td>enable use of JDBC2 scrollable resultsets (you only need this specify
 *   this property when using user supplied connections)</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.jdbc.use_getGeneratedKeys}</td>
 *   <td>enable use of JDBC3 PreparedStatement.getGeneratedKeys() to retrieve
 *   natively generated keys after insert. Requires JDBC3+ driver and JRE1.4+</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.hbm2ddl.auto}</td>
 *   <td>enable auto DDL export</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.default_schema}</td>
 *   <td>use given schema name for unqualified tables (always optional)</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.default_catalog}</td>
 *   <td>use given catalog name for unqualified tables (always optional)</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.session_factory_name}</td>
 *   <td>If set, the factory attempts to bind this name to itself in the
 *   JNDI context. This name is also used to support cross JVM {@code 
 *   Session} (de)serialization.</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.transaction.jta.platform}</td>
 *   <td>classname of {@code org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform}
 *   implementor</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.transaction.factory_class}</td>
 *   <td>the factory to use for instantiating {@code Transaction}s.
 *   (Defaults to {@code JdbcTransactionFactory}.)</td>
 * </tr>
 * <tr>
 *   <td>{@code hibernate.query.substitutions}</td><td>query language token substitutions</td>
 * </tr>
 * </table>
 *
 * @see org.hibernate.SessionFactory
 * @author Gavin King
 */
public final class Environment implements AvailableSettings {
	private static final CoreMessageLogger LOG = Logger.getMessageLogger( CoreMessageLogger.class, Environment.class.getName());

	private static final BytecodeProvider BYTECODE_PROVIDER_INSTANCE;
	private static final boolean ENABLE_BINARY_STREAMS;
	private static final boolean ENABLE_REFLECTION_OPTIMIZER;
	private static final boolean ENABLE_LEGACY_PROXY_CLASSNAMES;

	private static final Properties GLOBAL_PROPERTIES;

	private static final Map OBSOLETE_PROPERTIES = new HashMap();
	private static final Map RENAMED_PROPERTIES = new HashMap();

	/**
	 * Issues warnings to the user when any obsolete or renamed property names are used.
	 *
	 * @param configurationValues The specified properties.
	 */
	public static void verifyProperties(Map<?,?> configurationValues) {
		final Map propertiesToAdd = new HashMap();
		for ( Map.Entry entry : configurationValues.entrySet() ) {
			final Object replacementKey = OBSOLETE_PROPERTIES.get( entry.getKey() );
			if ( replacementKey != null ) {
				LOG.unsupportedProperty( entry.getKey(), replacementKey );
			}
			final Object renamedKey = RENAMED_PROPERTIES.get( entry.getKey() );
			if ( renamedKey != null ) {
				LOG.renamedProperty( entry.getKey(), renamedKey );
				propertiesToAdd.put( renamedKey, entry.getValue() );
			}
		}
		configurationValues.putAll( propertiesToAdd );
	}

	static {
		Version.logVersion();

		GLOBAL_PROPERTIES = new Properties();
		//Set USE_REFLECTION_OPTIMIZER to false to fix HHH-227
		GLOBAL_PROPERTIES.setProperty( USE_REFLECTION_OPTIMIZER, Boolean.FALSE.toString() );

		try {
			InputStream stream = ConfigHelper.getResourceAsStream( "/hibernate.properties" );
			try {
				GLOBAL_PROPERTIES.load(stream);
				LOG.propertiesLoaded( ConfigurationHelper.maskOut( GLOBAL_PROPERTIES, PASS ) );
			}
			catch (Exception e) {
				LOG.unableToLoadProperties();
			}
			finally {
				try{
					stream.close();
				}
				catch (IOException ioe){
					LOG.unableToCloseStreamError( ioe );
				}
			}
		}
		catch (HibernateException he) {
			LOG.propertiesNotFound();
		}

		try {
			Properties systemProperties = System.getProperties();
		    // Must be thread-safe in case an application changes System properties during Hibernate initialization.
		    // See HHH-8383.
			synchronized (systemProperties) {
				GLOBAL_PROPERTIES.putAll(systemProperties);
			}
		}
		catch (SecurityException se) {
			LOG.unableToCopySystemProperties();
		}

		verifyProperties(GLOBAL_PROPERTIES);

		ENABLE_BINARY_STREAMS = ConfigurationHelper.getBoolean(USE_STREAMS_FOR_BINARY, GLOBAL_PROPERTIES);
		if ( ENABLE_BINARY_STREAMS ) {
			LOG.usingStreams();
		}

		ENABLE_REFLECTION_OPTIMIZER = ConfigurationHelper.getBoolean(USE_REFLECTION_OPTIMIZER, GLOBAL_PROPERTIES);
		if ( ENABLE_REFLECTION_OPTIMIZER ) {
			LOG.usingReflectionOptimizer();
		}

		ENABLE_LEGACY_PROXY_CLASSNAMES = ConfigurationHelper.getBoolean( ENFORCE_LEGACY_PROXY_CLASSNAMES, GLOBAL_PROPERTIES );
		if ( ENABLE_LEGACY_PROXY_CLASSNAMES ) {
			final UnsupportedLogger unsupportedLogger = Logger.getMessageLogger( UnsupportedLogger.class, Environment.class.getName() );
			unsupportedLogger.usingLegacyClassnamesForProxies();
		}

		BYTECODE_PROVIDER_INSTANCE = buildBytecodeProvider( GLOBAL_PROPERTIES );
	}

	/**
	 * This will be removed soon; currently just returns false as no known JVM exibits this bug
	 * and is also able to run this version of Hibernate ORM.
	 * @deprecated removed as unneccessary
	 * @return false
	 */
	@Deprecated
	public static boolean jvmHasTimestampBug() {
		return false;
	}

	/**
	 * Should we use streams to bind binary types to JDBC IN parameters?
	 *
	 * @return True if streams should be used for binary data handling; false otherwise.
	 *
	 * @see #USE_STREAMS_FOR_BINARY
	 *
	 * @deprecated Deprecated to indicate that the method will be moved to
	 * {@link org.hibernate.boot.spi.SessionFactoryOptions} /
	 * {@link org.hibernate.boot.SessionFactoryBuilder} - probably in 6.0.
	 * See <a href="https://hibernate.atlassian.net/browse/HHH-12194">HHH-12194</a> and
	 * <a href="https://hibernate.atlassian.net/browse/HHH-12193">HHH-12193</a> for details
	 */
	@Deprecated
	public static boolean useStreamsForBinary() {
		return ENABLE_BINARY_STREAMS;
	}

	/**
	 * Should we use reflection optimization?
	 *
	 * @return True if reflection optimization should be used; false otherwise.
	 *
	 * @see #USE_REFLECTION_OPTIMIZER
	 * @see #getBytecodeProvider()
	 * @see BytecodeProvider#getReflectionOptimizer
	 *
	 * @deprecated Deprecated to indicate that the method will be moved to
	 * {@link org.hibernate.boot.spi.SessionFactoryOptions} /
	 * {@link org.hibernate.boot.SessionFactoryBuilder} - probably in 6.0.
	 * See <a href="https://hibernate.atlassian.net/browse/HHH-12194">HHH-12194</a> and
	 * <a href="https://hibernate.atlassian.net/browse/HHH-12193">HHH-12193</a> for details
	 */
	@Deprecated
	public static boolean useReflectionOptimizer() {
		return ENABLE_REFLECTION_OPTIMIZER;
	}

	/**
	 * @deprecated Deprecated to indicate that the method will be moved to
	 * {@link org.hibernate.boot.spi.SessionFactoryOptions} /
	 * {@link org.hibernate.boot.SessionFactoryBuilder} - probably in 6.0.
	 * See <a href="https://hibernate.atlassian.net/browse/HHH-12194">HHH-12194</a> and
	 * <a href="https://hibernate.atlassian.net/browse/HHH-12193">HHH-12193</a> for details
	 */
	@Deprecated
	public static BytecodeProvider getBytecodeProvider() {
		return BYTECODE_PROVIDER_INSTANCE;
	}

	/**
	 * @return True if global option org.hibernate.cfg.AvailableSettings#ENFORCE_LEGACY_PROXY_CLASSNAMES was enabled
	 * @deprecated This option will be removed soon and should not be relied on.
	 */
	@Deprecated
	public static boolean useLegacyProxyClassnames() {
		return ENABLE_LEGACY_PROXY_CLASSNAMES;
	}

	/**
	 * Disallow instantiation
	 */
	private Environment() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Return {@code System} properties, extended by any properties specified
	 * in {@code hibernate.properties}.
	 * @return Properties
	 */
	public static Properties getProperties() {
		Properties copy = new Properties();
		copy.putAll(GLOBAL_PROPERTIES);
		return copy;
	}

	/**
	 * @deprecated Use {@link ConnectionProviderInitiator#toIsolationNiceName} instead
	 */
	@Deprecated
	public static String isolationLevelToString(int isolation) {
		return ConnectionProviderInitiator.toIsolationNiceName( isolation );
	}


	public static final String BYTECODE_PROVIDER_NAME_JAVASSIST = "javassist";
	public static final String BYTECODE_PROVIDER_NAME_BYTEBUDDY = "bytebuddy";
	public static final String BYTECODE_PROVIDER_NAME_DEFAULT = BYTECODE_PROVIDER_NAME_BYTEBUDDY;

	public static BytecodeProvider buildBytecodeProvider(Properties properties) {
		String provider = ConfigurationHelper.getString( BYTECODE_PROVIDER, properties, BYTECODE_PROVIDER_NAME_DEFAULT );
		return buildBytecodeProvider( provider );
	}

	private static BytecodeProvider buildBytecodeProvider(String providerName) {
		if ( BYTECODE_PROVIDER_NAME_BYTEBUDDY.equals( providerName ) ) {
			return new org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl();
		}

		if ( BYTECODE_PROVIDER_NAME_JAVASSIST.equals( providerName ) ) {
			return new org.hibernate.bytecode.internal.javassist.BytecodeProviderImpl();
		}

		LOG.bytecodeProvider( providerName );

		// todo : allow a custom class name - just check if the config is a FQN
		//		currently we assume it is only ever the Strings "javassist" or "bytebuddy"...

		LOG.unknownBytecodeProvider( providerName, BYTECODE_PROVIDER_NAME_DEFAULT );
		return new org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl();
	}
}
