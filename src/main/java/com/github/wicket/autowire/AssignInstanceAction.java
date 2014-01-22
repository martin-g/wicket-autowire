package com.github.wicket.autowire;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.util.lang.Args;

/**
 *
 */
class AssignInstanceAction implements Action
{

	private final Field field;
	
	private final String id;

	public AssignInstanceAction(Field field, String id)
	{
		this.field = Args.notNull(field, "field");
		this.id = Args.notEmpty(id, "id");
	}

	@Override
	public void perform(MarkupContainer parent)
	{
		try
		{
			Component instance = getInstance(field.getType(), parent, id);
			Utils.setChildComponent(instance, parent, field);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private Component getInstance(final Class<?> componentClass, final Component enclosing, final String id) 
			throws Exception
	{
		Class<?> enclosingClass = componentClass.getEnclosingClass();
		if (enclosingClass == null || Modifier.isStatic(componentClass.getModifiers()))
		{
			// -- Static inner class or normal class
			final Constructor<?> constructor = componentClass.getDeclaredConstructor(String.class);
			boolean accessible = constructor.isAccessible();
			try
			{
				constructor.setAccessible(true);
				return (Component)constructor.newInstance(id);
			}
			finally
			{
				constructor.setAccessible(accessible);
			}
		}
		else
		{
			if (enclosing != null && enclosingClass.isAssignableFrom(enclosing.getClass()))
			{
				final Constructor<?> constructor = componentClass.getDeclaredConstructor(enclosingClass, String.class);
				boolean accessible = constructor.isAccessible();
				try
				{
					constructor.setAccessible(true);
					return (Component)constructor.newInstance(enclosing, id);
				}
				finally
				{
					constructor.setAccessible(accessible);
				}
			}
			throw new RuntimeException("Unable to initialize inner class "
					+ componentClass.getName() + " with id " + id
					+ ". Enclosing class is not in the component hierarchy.");
		}
	}

	@Override
	public String toString()
	{
		return "Assign instance with id " + id + " to field " + field.getName();
	}

}
