package com.github.wicket.autowire;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.border.Border;

/**
 * Meta data for a Component
 */
class Node
{
	/**
	 * The component id
	 */
	String id;

	/**
	 * The parent node of this
	 */
	Node parent;

	Field field;

	/**
	 * All children of this node
	 */
	List<Node> children = new ArrayList<>();

	/**
	 * A flag indicating whether the component represented by this node is a Border
	 */
	boolean border = false;

	long lastUsed = System.currentTimeMillis();

	void addChild(Node child)
	{
		child.parent = this;
		children.add(child);
	}

	void initialize(Component component)
	{
		initialize(component, component);
	}

	private void initialize(Component root, Component parent)
	{
		for (Node child : children)
		{
			Component value = Utils.getChildComponent(root, child.field);
			if (child.border)
			{
				((Border)parent).addToBorder(value);
			}
			else
			{
				((MarkupContainer)parent).add(value);
			}
			if (!child.children.isEmpty())
			{
				child.initialize(root, value);
			}
		}
	}

	@Override
	public String toString()
	{
		return "Node{" + "field=" + ((field != null) ? field.getName() : null)
				+ ", children=" + children + ", border=" + border + ", id='" + id + '\''
				+ '}';
	}
}
