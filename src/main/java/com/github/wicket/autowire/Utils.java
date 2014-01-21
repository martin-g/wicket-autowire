package com.github.wicket.autowire;

import java.lang.reflect.Field;

import org.apache.wicket.Component;

/**
 *
 */
class Utils
{
	static Component getValue(Component component, Field field)
	{
		boolean accessible = field.isAccessible();
		try
		{
			field.setAccessible(true);
			return (Component)field.get(component);
		}
		catch (IllegalAccessException e)
		{
			return null;
		}
		finally
		{
			field.setAccessible(accessible);
		}
	}

	static Component buildComponent(Component component, final String id, Node child)
	{
		Class<?> clazz = component.getClass();
		while (Component.class.isAssignableFrom(clazz))
		{
			try
			{
				Component value = null;
				// look for annotated field
				for (Field field : clazz.getDeclaredFields())
				{
					if (field.isAnnotationPresent(AutoComponent.class))
					{
						value = getValue(component, field);
						if (value != null && value.getId().equals(id))
						{
							child.field = field;
							break;
						}
						else
						{
							value = null;
						}
					}
				}
				if (value != null)
				{
					return value;
				}
			}
			catch (final SecurityException | IllegalArgumentException e)
			{
				throw new RuntimeException(e);
			}
			clazz = clazz.getSuperclass();
		}

		return null;
	}

	// set value on duplicated field of parent classes too!
	static void setValue(Component instance, final Component component, Field field)
			throws IllegalAccessException
	{
		Class<?> clazz = field.getDeclaringClass();
		while (Component.class.isAssignableFrom(clazz))
		{
			for (Field f : clazz.getDeclaredFields())
			{
				if (f.getName().equals(field.getName()))
				{
					boolean accessible = f.isAccessible();
					try
					{
						f.setAccessible(true);
						f.set(component, instance);
					}
					finally
					{
						f.setAccessible(accessible);
					}
				}
			}
			clazz = clazz.getSuperclass();
		}
	}
}
