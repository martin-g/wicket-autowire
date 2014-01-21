package com.github.wicket.autowire;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.apache.wicket.Component;

/**
*
*/
class AssignInstanceAction implements Action
{

	private final Field field;
	private final String id;

	public AssignInstanceAction(Field field, String id)
	{
		this.field = field;
		this.id = id;
	}

	@Override
	public String toString()
	{
		return "Assign instance with id " + id + " to field " + field.getName();
	}

	@Override
	public void perform(Component component)
	{
		try
		{
			Component instance = getInstance(field.getType(), component, id);
			Utils.setValue(instance, component, field);
		}
		catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
		{
			e.printStackTrace();
		}
	}

	private Component getInstance(final Class<?> componentClass, final Component enclosing,
	                              final String id) throws NoSuchMethodException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException
	{
		if (componentClass.getEnclosingClass() == null
				|| Modifier.isStatic(componentClass.getModifiers()))
		{
			// -- Static inner class or normal class
			final Constructor<?> constructor = componentClass.getDeclaredConstructor(String.class);
			constructor.setAccessible(true);
			return (Component)constructor.newInstance(id);
		}
		else
		{
			if (enclosing != null
					&& componentClass.getEnclosingClass().isAssignableFrom(enclosing.getClass()))
			{
				final Constructor<?> constructor = componentClass.getDeclaredConstructor(
						componentClass.getEnclosingClass(), String.class);
				constructor.setAccessible(true);
				return (Component)constructor.newInstance(enclosing, id);
			}
			throw new RuntimeException("Unable to initialize inner class "
					+ componentClass.getClass().getSimpleName() + " with id " + id
					+ ". Enclosing class is not in the component hierarchy.");
		}
	}
}
