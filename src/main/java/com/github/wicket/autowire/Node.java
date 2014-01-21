package com.github.wicket.autowire;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.border.Border;

/**
*
*/
class Node
{

	public String id = null;
	Node parent = null;
	Field field = null;
	List<Node> childNodes = new ArrayList<>();
	boolean border = false;
	long lastUsed = System.currentTimeMillis();

	public void add(Node child)
	{
		child.parent = this;
		childNodes.add(child);
	}

	@Override
	public String toString()
	{
		return "Node{" + "field=" + ((field != null) ? field.getName() : null)
				+ ", childNodes=" + childNodes + ", border=" + border + ", id='" + id + '\''
				+ '}';
	}

	public void initialize(Component component)
	{
		initialize(component, component);
	}

	private void initialize(Component root, Component parent)
	{
		for (Node child : childNodes)
		{
			Component value = Utils.getValue(root, child.field);
			if (child.border)
			{
				((Border)parent).addToBorder(value);
			}
			else
			{
				((MarkupContainer)parent).add(value);
			}
			if (!child.childNodes.isEmpty())
			{
				child.initialize(root, value);
			}
		}
	}

}
