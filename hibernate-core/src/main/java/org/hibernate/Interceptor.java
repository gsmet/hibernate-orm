/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate;

import java.io.Serializable;
import java.util.Iterator;

import org.hibernate.type.Type;

/**
 * Allows user code to inspect and/or change property values.
 *
 * Inspection occurs before property values are written and after they are read
 * from the database.
 *
 * There might be a single instance of {@code Interceptor} for a {@code SessionFactory}, or a new instance
 * might be specified for each {@code Session}. Whichever approach is used, the interceptor must be
 * serializable if the {@code Session} is to be serializable. This means that {@code SessionFactory}-scoped
 * interceptors should implement {@code readResolve()}.
 *
 * The {@code Session} may not be invoked from a callback (nor may a callback cause a collection or proxy to
 * be lazily initialized).
 *
 * Instead of implementing this interface directly, it is usually better to extend {@code EmptyInterceptor}
 * and override only the callback methods of interest.
 *
 * @see SessionBuilder#interceptor(Interceptor)
 * @see SharedSessionBuilder#interceptor()
 * @see org.hibernate.cfg.Configuration#setInterceptor(Interceptor)
 * @see EmptyInterceptor
 *
 * @author Gavin King
 */
public interface Interceptor {
	/**
	 * Called just before an object is initialized. The interceptor may change the {@code state}, which will
	 * be propagated to the persistent object. Note that when this method is called, {@code entity} will be
	 * an empty uninitialized instance of the class.
	 * <p>
	 * NOTE: The indexes across the {@code state}, {@code propertyNames} and {@code types} arrays match.
	 *
	 * @param entity The entity instance being loaded
	 * @param id The identifier value being loaded
	 * @param state The entity state (which will be pushed into the entity instance)
	 * @param propertyNames The names of the entity properties, corresponding to the {@code state}.
	 * @param types The types of the entity properties, corresponding to the {@code state}.
	 *
	 * @return {@code true} if the user modified the {@code state} in any way.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	boolean onLoad(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws CallbackException;

	/**
	 * Called when an object is detected to be dirty, during a flush. The interceptor may modify the detected
	 * {@code currentState}, which will be propagated to both the database and the persistent object.
	 * Note that not all flushes end in actual synchronization with the database, in which case the
	 * new {@code currentState} will be propagated to the object, but not necessarily (immediately) to
	 * the database. It is strongly recommended that the interceptor <b>not</b> modify the {@code previousState}.
	 * <p>
	 * NOTE: The indexes across the {@code currentState}, {@code previousState}, {@code propertyNames} and
	 * {@code types} arrays match.
	 *
	 * @param entity The entity instance detected as being dirty and being flushed
	 * @param id The identifier of the entity
	 * @param currentState The entity's current state
	 * @param previousState The entity's previous (load time) state.
	 * @param propertyNames The names of the entity properties
	 * @param types The types of the entity properties
	 *
	 * @return {@code true} if the user modified the {@code currentState} in any way.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	boolean onFlushDirty(
			Object entity,
			Serializable id,
			Object[] currentState,
			Object[] previousState,
			String[] propertyNames,
			Type[] types) throws CallbackException;

	/**
	 * Called before an object is saved. The interceptor may modify the {@code state}, which will be used for
	 * the SQL {@code INSERT} and propagated to the persistent object.
	 *
	 * @param entity The entity instance whose state is being inserted
	 * @param id The identifier of the entity
	 * @param state The state of the entity which will be inserted
	 * @param propertyNames The names of the entity properties.
	 * @param types The types of the entity properties
	 *
	 * @return {@code true} if the user modified the {@code state} in any way.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws CallbackException;

	/**
	 *  Called before an object is deleted. It is not recommended that the interceptor modify the {@code state}.
	 *
	 * @param entity The entity instance being deleted
	 * @param id The identifier of the entity
	 * @param state The state of the entity
	 * @param propertyNames The names of the entity properties.
	 * @param types The types of the entity properties
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws CallbackException;

	/**
	 * Called before a collection is (re)created.
	 *
	 * @param collection The collection instance.
	 * @param key The collection key value.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	void onCollectionRecreate(Object collection, Serializable key) throws CallbackException;

	/**
	 * Called before a collection is deleted.
	 *
	 * @param collection The collection instance.
	 * @param key The collection key value.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	void onCollectionRemove(Object collection, Serializable key) throws CallbackException;

	/**
	 * Called before a collection is updated.
	 *
	 * @param collection The collection instance.
	 * @param key The collection key value.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	void onCollectionUpdate(Object collection, Serializable key) throws CallbackException;

	/**
	 * Called before a flush.
	 *
	 * @param entities The entities to be flushed.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	void preFlush(Iterator entities) throws CallbackException;

	/**
	 * Called after a flush that actually ends in execution of the SQL statements required to synchronize
	 * in-memory state with the database.
	 *
	 * @param entities The entities that were flushed.
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	void postFlush(Iterator entities) throws CallbackException;

	/**
	 * Called to distinguish between transient and detached entities. The return value determines the
	 * state of the entity with respect to the current session.
	 * <ul>
	 * <li>{@code Boolean.TRUE} - the entity is transient
	 * <li>{@code Boolean.FALSE} - the entity is detached
	 * <li>{@code null} - Hibernate uses the {@code unsaved-value} mapping and other heuristics to 
	 * determine if the object is unsaved
	 * </ul>
	 * @param entity a transient or detached entity
	 * @return Boolean or {@code null} to choose default behaviour
	 */
	Boolean isTransient(Object entity);

	/**
	 * Called from {@code flush()}. The return value determines whether the entity is updated
	 * <ul>
	 * <li>an array of property indices - the entity is dirty
	 * <li>an empty array - the entity is not dirty
	 * <li>{@code null} - use Hibernate's default dirty-checking algorithm
	 * </ul>
	 *
	 * @param entity The entity for which to find dirty properties.
	 * @param id The identifier of the entity
	 * @param currentState The current entity state as taken from the entity instance
	 * @param previousState The state of the entity when it was last synchronized (generally when it was loaded)
	 * @param propertyNames The names of the entity properties.
	 * @param types The types of the entity properties
	 *
	 * @return array of dirty property indices or {@code null} to indicate Hibernate should perform default behaviour
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	int[] findDirty(
			Object entity,
			Serializable id,
			Object[] currentState,
			Object[] previousState,
			String[] propertyNames,
			Type[] types);
	/**
	 * Instantiate the entity class. Return {@code null} to indicate that Hibernate should use
	 * the default constructor of the class. The identifier property of the returned instance
	 * should be initialized with the given identifier.
	 *
	 * @param entityName the name of the entity
	 * @param entityMode The type of entity instance to be returned.
	 * @param id the identifier of the new instance
	 *
	 * @return an instance of the class, or {@code null} to choose default behaviour
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	Object instantiate(String entityName, EntityMode entityMode, Serializable id) throws CallbackException;

	/**
	 * Get the entity name for a persistent or transient instance.
	 *
	 * @param object an entity instance
	 *
	 * @return the name of the entity
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	String getEntityName(Object object) throws CallbackException;

	/**
	 * Get a fully loaded entity instance that is cached externally.
	 *
	 * @param entityName the name of the entity
	 * @param id the instance identifier
	 *
	 * @return a fully initialized entity
	 *
	 * @throws CallbackException Thrown if the interceptor encounters any problems handling the callback.
	 */
	Object getEntity(String entityName, Serializable id) throws CallbackException;
	
	/**
	 * Called when a Hibernate transaction is begun via the Hibernate {@code Transaction} 
	 * API. Will not be called if transactions are being controlled via some other 
	 * mechanism (CMT, for example).
	 *
	 * @param tx The Hibernate transaction facade object
	 */
	void afterTransactionBegin(Transaction tx);

	/**
	 * Called before a transaction is committed (but not before rollback).
	 *
	 * @param tx The Hibernate transaction facade object
	 */
	void beforeTransactionCompletion(Transaction tx);

	/**
	 * Called after a transaction is committed or rolled back.
	 *
	 * @param tx The Hibernate transaction facade object
	 */
	void afterTransactionCompletion(Transaction tx);

	/**
	 * Called when sql string is being prepared. 
	 * @param sql sql to be prepared
	 * @return original or modified sql
	 *
	 * @deprecated Supply a {@link org.hibernate.resource.jdbc.spi.StatementInspector} instead, if you wish
	 * to inspect and alter SQL statements.
	 */
	@Deprecated
	String onPrepareStatement(String sql);
}
