/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
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
/*
 * generated by Xtext
 */
package org.openhab.model.script;

import org.eclipse.xtext.common.types.xtext.ClasspathBasedTypeScopeProvider;
import org.eclipse.xtext.xbase.featurecalls.IdentifiableSimpleNameProvider;
import org.eclipse.xtext.xbase.interpreter.IExpressionInterpreter;
import org.eclipse.xtext.xbase.scoping.featurecalls.StaticImplicitMethodsFeatureForTypeProvider.ExtensionClassNameProvider;
import org.openhab.core.scriptengine.Script;
import org.openhab.model.script.internal.engine.ScriptImpl;
import org.openhab.model.script.interpreter.ScriptInterpreter;
import org.openhab.model.script.jvmmodel.ScriptIdentifiableSimpleNameProvider;
import org.openhab.model.script.scoping.ActionClassLoader;
import org.openhab.model.script.scoping.ActionClasspathBasedTypeScopeProvider;
import org.openhab.model.script.scoping.ActionClasspathTypeProviderFactory;
import org.openhab.model.script.scoping.ScriptExtensionClassNameProvider;
import org.openhab.model.script.scoping.ScriptScopeProvider;
import org.openhab.model.script.scoping.StateAndCommandProvider;

/**
 * Use this class to register components to be used at runtime / without the Equinox extension registry.
 */
@SuppressWarnings("restriction")
public class ScriptRuntimeModule extends org.openhab.model.script.AbstractScriptRuntimeModule {

	public Class<? extends ExtensionClassNameProvider> bindExtensionClassNameProvider() {
		return ScriptExtensionClassNameProvider.class;
	}
	
	public Class<? extends IdentifiableSimpleNameProvider> bindIdentifiableSimpleNameProvider() {
		return ScriptIdentifiableSimpleNameProvider.class;
	}

	public Class<? extends Script> bindScript() {
		return ScriptImpl.class;
	}
	
	public Class<? extends IExpressionInterpreter> bindIExpressionInterpreter() {
		return ScriptInterpreter.class;
	}
	
	public Class<StateAndCommandProvider> bindStateAndCommandProvider() {
		return StateAndCommandProvider.class;
	}

	@Override
	public Class<? extends org.eclipse.xtext.scoping.IScopeProvider> bindIScopeProvider() {
		return ScriptScopeProvider.class;
	}

	/* we need this so that our pluggable actions can be resolved at design time */
	@Override
	public Class<? extends org.eclipse.xtext.common.types.access.IJvmTypeProvider.Factory> bindIJvmTypeProvider$Factory() {
		return ActionClasspathTypeProviderFactory.class;
	}
	
	/* we need this so that our pluggable actions can be resolved when being parsed at runtime */
	@Override
	public Class<? extends org.eclipse.xtext.common.types.xtext.AbstractTypeScopeProvider> bindAbstractTypeScopeProvider() {
		return ActionClasspathBasedTypeScopeProvider.class;
	}

	/* we need this so that our pluggable actions can be resolved when being executed at runtime */
	@Override
	public ClassLoader bindClassLoaderToInstance() {
		return new ActionClassLoader(getClass().getClassLoader());
	}

}
