/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.github.wicket.autowire;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.application.IComponentInitializationListener;
import org.apache.wicket.application.IComponentInstantiationListener;
import org.apache.wicket.markup.MarkupNotFoundException;
import org.apache.wicket.markup.html.TransparentWebMarkupContainer;
import org.apache.wicket.util.string.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoWire
		implements
			IComponentInitializationListener,
			IComponentInstantiationListener
{

	private static final Logger log = LoggerFactory.getLogger(Component.class);

	private static final ComponentCache CACHE = new ComponentCache();

	private AutoWire()
	{

	}

	public static void install(final Application application)
	{
		final AutoWire instance = new AutoWire();

		application.getComponentInstantiationListeners().add(instance);
		application.getComponentInitializationListeners().add(instance);
	}

	@Override
	public void onInstantiation(final Component component)
	{
		Class<? extends Component> componentClass = component.getClass();
		Value value = CACHE.get(componentClass);
		if (value == null)
		{
			log.trace("MISS: {}", componentClass);

			synchronized (AutoWire.class)
			{
				value = CACHE.get(componentClass);
				if (value == null)
				{
					List<Action> actions = getInstantiationActions(component);

					value = new Value(actions);
					Value old = CACHE.putIfAbsent(componentClass, value);
					if (old != null)
					{
						value = old;
					}
				}
			}
		}
		value.performInstantiationActions(component);
	}

	@Override
	public void onInitialize(final Component component)
	{
		if (isAutoWiringPossible(component))
		{
			try
			{
				Value value = CACHE.get(component.getClass());
				value.performInitializeActions((MarkupContainer) component);
			}
			catch (final MarkupNotFoundException e)
			{
				log.error("================ Markup not found for '{}'", component);
			}
		}
	}

	private List<Action> getInstantiationActions(Component component)
	{
		List<Action> actions = new ArrayList<>();

		if (isAutoWiringPossible(component))
		{
			Set<String> done = new HashSet<>();
			Class<?> clazz = component.getClass();
			// iterate over class hierarchy
			while (clazz != null && clazz.getName().startsWith("org.apache.wicket") == false)
			{
				log.trace("looking for fields in class '{}'", clazz);

				// iterate over declared field
				for (final Field field : clazz.getDeclaredFields())
				{
					if (field.isAnnotationPresent(AutoComponent.class))
					{
						AutoComponent ann = field.getAnnotation(AutoComponent.class);
						if (ann.inject())
						{
							String annotationId = ann.id();
							String fieldName = field.getName();
							final String id = Strings.isEmpty(annotationId) ? fieldName : annotationId;
							// fields in super classes are ignored, if they are
							// in subclasses too
							if (!done.contains(id))
							{
								done.add(id);
								Component value = Utils.getChildComponent(component, field);
								if (value == null)
								{
									actions.add(new AssignInstanceAction(field, id));
								}
								else
								{
									log.trace("Field '{}' is already initialized. Skipping.", fieldName);
								}
							}
						}
					}
				}
				clazz = clazz.getSuperclass();
			}
		}

		if (log.isTraceEnabled())
		{
			log.trace("Actions: " + actions);
		}

		return actions;
	}

	private boolean isAutoWiringPossible(final Component component)
	{
		return component instanceof MarkupContainer
				&& !(component instanceof TransparentWebMarkupContainer);
	}

}
