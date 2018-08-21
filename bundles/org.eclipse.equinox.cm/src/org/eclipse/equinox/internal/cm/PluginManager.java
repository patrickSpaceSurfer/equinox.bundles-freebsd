/*******************************************************************************
 * Copyright (c) 2005, 2018 Cognos Incorporated, IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Cognos Incorporated - initial API and implementation
 *     IBM Corporation - bug fixes and enhancements
 *******************************************************************************/
package org.eclipse.equinox.internal.cm;

import java.util.*;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationPlugin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * PluginManager tracks and allows customization via ConfigurationPlugin  
 */
public class PluginManager {
	private final PluginTracker pluginTracker;

	public PluginManager(BundleContext context) {
		pluginTracker = new PluginTracker(context);
	}

	public void start() {
		pluginTracker.open();
	}

	public void stop() {
		pluginTracker.close();
	}

	public void modifyConfiguration(ServiceReference<?> managedReference, Dictionary<String, Object> properties) {
		if (properties == null)
			return;

		ServiceReference<ConfigurationPlugin>[] references = pluginTracker.getServiceReferences();
		for (int i = 0; i < references.length; ++i) {
			String[] pids = (String[]) references[i].getProperty(ConfigurationPlugin.CM_TARGET);
			if (pids != null) {
				String pid = (String) properties.get(Constants.SERVICE_PID);
				if (!Arrays.asList(pids).contains(pid))
					continue;
			}
			ConfigurationPlugin plugin = pluginTracker.getService(references[i]);
			if (plugin != null)
				plugin.modifyConfiguration(managedReference, properties);
		}
	}

	private static class PluginTracker extends ServiceTracker<ConfigurationPlugin, ConfigurationPlugin> {
		final Integer ZERO = Integer.valueOf(0);
		private TreeSet<ServiceReference<ConfigurationPlugin>> serviceReferences = new TreeSet<>(new Comparator<ServiceReference<ConfigurationPlugin>>() {
			@Override
			public int compare(ServiceReference<ConfigurationPlugin> s1, ServiceReference<ConfigurationPlugin> s2) {

				int rankCompare = getRank(s1).compareTo(getRank(s2));
				if (rankCompare != 0) {
					return rankCompare;
				}
				// we reverse the order which means services with higher service.ranking properties are called first
				return -(s1.compareTo(s2));
			}

			private Integer getRank(ServiceReference<ConfigurationPlugin> ref) {
				Object ranking = ref.getProperty(ConfigurationPlugin.CM_RANKING);
				if (ranking == null || !(ranking instanceof Integer))
					return ZERO;
				return ((Integer) ranking);
			}
		});

		public PluginTracker(BundleContext context) {
			super(context, ConfigurationPlugin.class.getName(), null);
		}

		/* NOTE: this method alters the contract of the overriden method.
		 * Rather than returning null if no references are present, it
		 * returns an empty array.
		 */
		@Override
		public ServiceReference<ConfigurationPlugin>[] getServiceReferences() {
			synchronized (serviceReferences) {
				return serviceReferences.toArray(new ServiceReference[serviceReferences.size()]);
			}
		}

		@Override
		public ConfigurationPlugin addingService(ServiceReference<ConfigurationPlugin> reference) {
			synchronized (serviceReferences) {
				serviceReferences.add(reference);
			}
			return context.getService(reference);
		}

		@Override
		public void modifiedService(ServiceReference<ConfigurationPlugin> reference, ConfigurationPlugin service) {
			// nothing to do
		}

		@Override
		public void removedService(ServiceReference<ConfigurationPlugin> reference, ConfigurationPlugin service) {
			synchronized (serviceReferences) {
				serviceReferences.remove(reference);
			}
			context.ungetService(reference);
		}
	}
}
