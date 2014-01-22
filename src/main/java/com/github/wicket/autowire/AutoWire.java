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

	private static final Logger LOGGER = LoggerFactory.getLogger(Component.class);

	private static final ComponentCache CACHE = new ComponentCache();

	private AutoWire()
	{
	}

	public static void install(final Application application)
	{
		AutoWire instance = new AutoWire();

		application.getComponentInstantiationListeners().add(instance);
		application.getComponentInitializationListeners().add(instance);
	}

	@Override
	public void onInstantiation(final Component component)
	{
		if (isAutoWiringPossible(component))
		{
			MarkupContainer container = (MarkupContainer) component;

			Class<? extends Component> containerClass = container.getClass();
			Value value = CACHE.get(containerClass);
			if (value == null)
			{
				LOGGER.trace("MISS: {}", containerClass);

				synchronized (AutoWire.class)
				{
					value = CACHE.get(containerClass);
					if (value == null)
					{
						List<Action> actions = getInstantiationActions(container);

						value = new Value(actions);
						Value old = CACHE.putIfAbsent(containerClass, value);
						if (old != null)
						{
							value = old;
						}
					}
				}
			}
			value.performInstantiationActions(container);
		}
	}

	@Override
	public void onInitialize(final Component component)
	{
		if (isAutoWiringPossible(component))
		{
			MarkupContainer container = (MarkupContainer) component;
			try
			{
				Value value = CACHE.get(container.getClass());
				value.performInitializeActions(container);
			}
			catch (final MarkupNotFoundException e)
			{
				LOGGER.error("================ Markup not found for '{}'", component);
			}
		}
	}

	private List<Action> getInstantiationActions(MarkupContainer container)
	{
		List<Action> actions = new ArrayList<>();

		Set<String> processed = new HashSet<>();
		Class<?> clazz = container.getClass();

		while (clazz != null && !clazz.getName().startsWith("org.apache.wicket"))
		{
			LOGGER.trace("looking for fields in class '{}'", clazz);

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
						if (!processed.contains(id))
						{
							processed.add(id);
							Component childComponent = Utils.getChildComponent(container, field);
							if (childComponent == null)
							{
								actions.add(new AssignInstanceAction(field, id));
							}
							else
							{
								LOGGER.trace("Field '{}' is already initialized. Skipping.", fieldName);
							}
						}
					}
				}
			}
			clazz = clazz.getSuperclass();
		}

		LOGGER.trace("Actions: '{}'", actions);

		return actions;
	}

	private boolean isAutoWiringPossible(final Component component)
	{
		return component instanceof MarkupContainer
				&& !(component instanceof TransparentWebMarkupContainer);
	}

}
