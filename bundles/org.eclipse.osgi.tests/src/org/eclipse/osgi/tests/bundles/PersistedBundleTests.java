/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.tests.bundles;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.*;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.osgi.launch.Equinox;
import org.eclipse.osgi.tests.OSGiTestsActivator;
import org.osgi.framework.*;

/*
 * The framework must persist data according to the value of the 
 * eclipse.stateSaveDelayInterval property. The value is of type long and
 * represents the number of milliseconds between persists. A positive value
 * represents the number of milliseconds between persists. A value of zero
 * indicates data should be immediately persisted with each update. A negative
 * value disables persistence on update altogether (but data will still be 
 * persisted on shutdown).
 * 
 */
public class PersistedBundleTests extends AbstractBundleTests {
	static class BundleBuilder {
		static class BundleManifestBuilder {
			private final Manifest manifest = new Manifest();

			public Manifest build() {
				manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
				return manifest;
			}

			public BundleManifestBuilder symbolicName(String value) {
				manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, value);
				return this;
			}
		}

		private final BundleManifestBuilder manifestBuilder = new BundleManifestBuilder();

		public InputStream build() throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JarOutputStream jos = new JarOutputStream(baos, manifestBuilder.build());
			jos.close();
			return new ByteArrayInputStream(baos.toByteArray());
		}

		public BundleBuilder symbolicName(String value) {
			manifestBuilder.symbolicName(value);
			return this;
		}
	}

	private static final String ECLIPSE_STATESAVEDELAYINTERVAL = "eclipse.stateSaveDelayInterval";

	private static final String IMMEDIATE_PERSISTENCE = "0";
	private static final String NO_PERSISTENCE = "-1";
	private static final String PERIODIC_PERSISTENCE = "5000";

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public static Test suite() {
		return new TestSuite(PersistedBundleTests.class);
	}

	/*
	 * Test that a value of zero for eclipse.stateSaveDelayInterval results in
	 * immediate persistence.
	 */
	public void testImmediatePersistence() throws Exception {
		Map<String, Object> configuration = createConfiguration();
		configuration.put(ECLIPSE_STATESAVEDELAYINTERVAL, IMMEDIATE_PERSISTENCE);
		Equinox equinox1 = new Equinox(configuration);
		initAndStart(equinox1);
		try {
			assertNull("Bundle exists", equinox1.getBundleContext().getBundle(getName()));
			equinox1.getBundleContext().installBundle(getName(), new BundleBuilder().symbolicName(getName()).build());
			Equinox equinox2 = new Equinox(configuration);
			initAndStart(equinox2);
			try {
				assertNotNull("Bundle does not exist", equinox2.getBundleContext().getBundle(getName()));
			} finally {
				stopQuietly(equinox2);
			}
		} finally {
			stopQuietly(equinox1);
		}
	}

	/*
	 * Test that a negative value for eclipse.stateSaveDelayInterval results in
	 * no persistence.
	 */
	public void testNoPersistence() throws Exception {
		Map<String, Object> configuration = createConfiguration();
		configuration.put(ECLIPSE_STATESAVEDELAYINTERVAL, NO_PERSISTENCE);
		Equinox equinox1 = new Equinox(configuration);
		initAndStart(equinox1);
		try {
			assertNull("Bundle exists", equinox1.getBundleContext().getBundle(getName()));
			equinox1.getBundleContext().installBundle(getName(), new BundleBuilder().symbolicName(getName()).build());
			Thread.sleep(Long.valueOf(PERIODIC_PERSISTENCE));
			Equinox equinox2 = new Equinox(configuration);
			initAndStart(equinox2);
			try {
				assertNull("Bundle exists", equinox2.getBundleContext().getBundle(getName()));
			} finally {
				stopQuietly(equinox2);
			}
		} finally {
			stopQuietly(equinox1);
		}
		// make sure it persisted after successful stop
		equinox1 = new Equinox(configuration);
		initAndStart(equinox1);
		try {
			assertNotNull("Bundle does not exists", equinox1.getBundleContext().getBundle(getName()));
		} finally {
			stopQuietly(equinox1);
		}
	}

	/*
	 * Test that a positive value for eclipse.stateSaveDelayInterval results in
	 * periodic persistence.
	 */
	public void testPeriodicPersistence() throws Exception {
		// Specify periodic persistence in the configuration.
		Map<String, Object> configuration = createConfiguration();
		configuration.put(ECLIPSE_STATESAVEDELAYINTERVAL, PERIODIC_PERSISTENCE);
		// Create an equinox instance that will be responsible for persisting
		// the bundle once the first period elapses.
		Equinox equinox1 = new Equinox(configuration);
		initAndStart(equinox1);
		try {
			// The bundle has not yet been installed.
			assertNull("Bundle exists", equinox1.getBundleContext().getBundle(getName()));
			// Install the bundle.
			equinox1.getBundleContext().installBundle(getName(), new BundleBuilder().symbolicName(getName()).build());
			// Create a second equinox instance to ensure the first instance
			// has not yet persisted the bundle.
			Equinox equinox2 = new Equinox(configuration);
			initAndStart(equinox2);
			try {
				// The bundle should not have been persisted and therefore be
				// unknown to the second equinox instance. This check must 
				// happen before the first period elapses.
				assertNull("Bundle exists", equinox2.getBundleContext().getBundle(getName()));
				stopQuietly(equinox2);
				// Ensure the first period elapses so the bundle has time to be
				// persisted.
				Thread.sleep(Long.valueOf(PERIODIC_PERSISTENCE));
				Bundle b = null;
				// Provide a buffer, if needed, after the first period elapses
				// to ensure the first instance has time to persist the bundle.
				for (int i = 0; i < 5; i++) {
					equinox2 = new Equinox(configuration);
					initAndStart(equinox2);
					b = equinox2.getBundleContext().getBundle(getName());
					if (b != null)
						break;
					Thread.sleep(1000);
				}
				// The persisted bundle should now be visible to the second
				// equinox instance.
				assertNotNull("Bundle does not exist", b);
			} finally {
				stopQuietly(equinox2);
			}
		} finally {
			stopQuietly(equinox1);
		}
	}

	private Map<String, Object> createConfiguration() {
		File file = OSGiTestsActivator.getContext().getDataFile(getName());
		Map<String, Object> result = new HashMap<String, Object>();
		result.put(Constants.FRAMEWORK_STORAGE, file.getAbsolutePath());
		return result;
	}

	private void initAndStart(Equinox equinox) throws BundleException {
		equinox.init();
		equinox.start();
	}

	private void stopQuietly(Equinox equinox) {
		if (equinox == null)
			return;
		try {
			equinox.stop();
			equinox.waitForStop(5000);
		} catch (Exception e) {
			// Ignore
		}
	}
}