/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;

/**
 * A simple class to centralize logic needed to locate config files on the system.
 *
 * @author Steve Ebersole
 *
 * @deprecated Use {@link org.hibernate.boot.registry.classloading.spi.ClassLoaderService} instead
 */
@Deprecated
public final class ConfigHelper {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( ConfigHelper.class );

	private static final String URL_SCHEME_SUFFIX = "://";
	private static final String CLASS_PATH_SCHEME = "classpath" + URL_SCHEME_SUFFIX;

	/**
	 * Try to locate a local URL representing the incoming path.  The first attempt
	 * assumes that the incoming path is an actual URL string (file://, etc).  If this
	 * does not work, then the next attempts try to locate this UURL as a java system
	 * resource.
	 *
	 * @param path The path representing the config location.
	 *
	 * @return An appropriate URL or null.
	 */
	public static URL locateConfig(final String path) {
		try {
			return new URL( path );
		}
		catch (MalformedURLException e) {
			return findAsResource( path );
		}
	}

	/**
	 * Try to locate a local URL representing the incoming path.
	 * This method <b>only</b> attempts to locate this URL as a
	 * java system resource.
	 *
	 * @param path The path representing the config location.
	 *
	 * @return An appropriate URL or null.
	 */
	public static URL findAsResource(final String path) {
		URL url = null;

		// First, try to locate this resource through the current
		// context classloader.
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		if ( contextClassLoader != null ) {
			url = contextClassLoader.getResource( path );
		}
		if ( url != null ) {
			return url;
		}

		// Next, try to locate this resource through this class's classloader
		url = ConfigHelper.class.getClassLoader().getResource( path );
		if ( url != null ) {
			return url;
		}

		// Next, try to locate this resource through the system classloader
		url = ClassLoader.getSystemClassLoader().getResource( path );

		// Anywhere else we should look?
		return url;
	}

	/**
	 * Open an InputStream to the URL represented by the incoming path.  First makes a call
	 * to {@link #locateConfig(java.lang.String)} in order to find an appropriate URL.
	 * {@link java.net.URL#openStream()} is then called to obtain the stream.
	 *
	 * @param path The path representing the config location.
	 *
	 * @return An input stream to the requested config resource.
	 *
	 * @throws HibernateException Unable to open stream to that resource.
	 */
	public static InputStream getConfigStream(final String path) throws HibernateException {
		final URL url = ConfigHelper.locateConfig( path );

		if ( url == null ) {
			String msg = LOG.unableToLocateConfigFile( path );
			LOG.error( msg );
			throw new HibernateException( msg );
		}

		try {
			return url.openStream();
		}
		catch (IOException e) {
			throw new HibernateException( "Unable to open config file: " + path, e );
		}
	}

	private ConfigHelper() {
	}

	public static InputStream getResourceAsStream(String resource) {
		String stripped = resource.startsWith( "/" )
				? resource.substring( 1 )
				: resource;

		InputStream stream = null;
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if ( classLoader != null ) {
			stream = classLoader.getResourceAsStream( stripped );
		}
		if ( stream == null ) {
			stream = Environment.class.getResourceAsStream( resource );
		}
		if ( stream == null ) {
			stream = Environment.class.getClassLoader().getResourceAsStream( stripped );
		}
		if ( stream == null ) {
			throw new HibernateException( resource + " not found" );
		}
		return stream;
	}


	public static InputStream getUserResourceAsStream(String resource) {
		boolean hasLeadingSlash = resource.startsWith( "/" );
		String stripped = hasLeadingSlash ? resource.substring( 1 ) : resource;

		InputStream stream = null;

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if ( classLoader != null ) {
			stream = classLoader.getResourceAsStream( resource );
			if ( stream == null && hasLeadingSlash ) {
				stream = classLoader.getResourceAsStream( stripped );
			}
		}

		if ( stream == null ) {
			stream = Environment.class.getClassLoader().getResourceAsStream( resource );
		}
		if ( stream == null && hasLeadingSlash ) {
			stream = Environment.class.getClassLoader().getResourceAsStream( stripped );
		}

		if ( stream == null ) {
			throw new HibernateException( resource + " not found" );
		}

		return stream;
	}

	public static URL getUserResourceAsURL(String resource) {
		boolean hasLeadingSlash = resource.startsWith( "/" );
		String stripped = hasLeadingSlash ? resource.substring( 1 ) : resource;

		URL url = null;

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if ( classLoader != null ) {
			url = classLoader.getResource( resource );
			if ( url == null && hasLeadingSlash ) {
				url = classLoader.getResource( stripped );
			}
		}

		if ( url == null ) {
			url = Environment.class.getClassLoader().getResource( resource );
		}
		if ( url == null && hasLeadingSlash ) {
			url = Environment.class.getClassLoader().getResource( stripped );
		}

		if ( url == null ) {
			throw new HibernateException( resource + " not found" );
		}

		return url;
	}

	/**
	 * Resolves resources based on the provided URL locator.
	 *
	 * @param resourcePath resource path encoded as {@link String}
	 * @return resource {@link URL}
	 * @throws MalformedURLException the URL {@link String} cannot be parsed
	 */
	public static URL resolveUrl(String resourcePath) throws MalformedURLException {
		if ( resourcePath == null ) {
			return null;
		}

		if ( resourcePath.startsWith( CLASS_PATH_SCHEME ) ) {
			String path = resourcePath.substring( CLASS_PATH_SCHEME.length() );
			return Thread.currentThread().getContextClassLoader().getResource( path );
		}
		if (resourcePath.contains( URL_SCHEME_SUFFIX )) {
			return new URL( resourcePath );
		}

		return getUserResourceAsURL(resourcePath);
	}
}
