/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.model.script.scoping;

import java.util.Collection;

import org.eclipse.xtext.xbase.scoping.featurecalls.StaticImplicitMethodsFeatureForTypeProvider.ExtensionClassNameProvider;
import org.openhab.model.script.lib.NumberExtensions;

import com.google.common.collect.Multimap;
import com.google.inject.Singleton;

/**
 * This class registers all statically available functions as well as the
 * extensions for specific jvm types.
 * 
 * @author Kai Kreuzer
 * @since 0.9.0
 *
 */
@SuppressWarnings("restriction")
@Singleton
public class ScriptExtensionClassNameProvider extends ExtensionClassNameProvider {

	@Override
	protected Collection<String> computeLiteralClassNames() {
		Collection<String> extensions = super.computeLiteralClassNames();
		extensions.add("org.openhab.io.net.actions.Mail");
		extensions.add("org.openhab.io.net.actions.HTTP");
		extensions.add("org.openhab.io.net.actions.XMPP");
		extensions.add("org.openhab.io.net.actions.Prowl");
		extensions.add("org.openhab.model.script.actions.BusEvent");
		extensions.add("org.openhab.model.script.actions.ScriptExecution");
		extensions.add("org.openhab.io.multimedia.actions.Audio");
		extensions.add("org.openhab.core.transform.actions.Transformation");
		return extensions;
	}
	
	@Override
	protected Multimap<Class<?>, Class<?>> simpleComputeExtensionClasses() {
		Multimap<Class<?>, Class<?>> result = super.simpleComputeExtensionClasses();
		result.removeAll(Comparable.class);
		result.removeAll(Double.class);
		result.removeAll(double.class);
		result.put(Number.class, NumberExtensions.class);
		result.put(Comparable.class, NumberExtensions.class);
		return result;
	}
}