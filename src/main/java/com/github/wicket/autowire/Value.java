package com.github.wicket.autowire;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.IMarkupFragment;
import org.apache.wicket.markup.MarkupElement;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.WicketTag;
import org.apache.wicket.markup.html.border.Border;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.resolver.WicketContainerResolver;
import org.apache.wicket.util.lang.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
*
*/
class Value
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Value.class);

	private final List<Action> instantiationActions;

	private final ConcurrentMap<String, Node> cache = new ConcurrentHashMap<>();

	Value(List<Action> instantiationActions)
	{
		this.instantiationActions = Args.notNull(instantiationActions, "instantiationActions");
	}

	void performInstantiationActions(MarkupContainer container)
	{
		for (Action action : instantiationActions)
		{
			action.perform(container);
		}
	}

	void performInitializeActions(MarkupContainer component)
	{
		final IMarkupFragment markup = component.getMarkup(null);

		if (markup == null)
		{
			return;
		}

		String key = markup.toString(false);
		Node node = cache.get(key);
		if (node == null)
		{
			if (LOGGER.isTraceEnabled())
			{
				LOGGER.trace("MARKUP MISS");
			}
			synchronized (AutoWire.class)
			{
				node = cache.get(key);
				if (node == null)
				{
					node = getNode(component, markup);
					cache.put(key, node);
				}
			}
		}

		node.lastUsed = System.currentTimeMillis();
		node.initialize(component);

		cleanup();
	}

	// avoid memory leaks if markup is changing often.
	private void cleanup()
	{
		long threshold = System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000;
		if (cache.size() > 30)
		{
			Iterator<Map.Entry<String, Node>> iterator = cache.entrySet().iterator();
			while (iterator.hasNext())
			{
				Map.Entry<String, Node> next = iterator.next();
				if (next.getValue().lastUsed < threshold)
				{
					iterator.remove();
				}
			}
		}
	}

	private Node getNode(Component component, IMarkupFragment markup)
	{
		final MarkupStream stream = new MarkupStream(markup);

		final Deque<AtomicReference<Component>> stack = new ArrayDeque<>();
		stack.push(new AtomicReference<>(component));

		Node node = new Node();

		// detect borders.
		boolean addToBorder = false;

		LOGGER.trace("Performing auto wiring for component '{}'", component);

		// no associated markup: component tag is part of the markup
		MarkupElement containerTag = null;
		// current criteria is fragile! find better way to check if
		// component tag of component is part its markup.
		if (skipFirstComponentTag(component, stream))
		{
			LOGGER.trace("Skipped component tag '{}'", stream.get());

			containerTag = stream.get();
			stream.next();
		}

		while (stream.skipUntil(ComponentTag.class))
		{
			final ComponentTag tag = stream.getTag();

			LOGGER.trace("Processing tag '{}'", tag);

			// track border tags
			if (tag instanceof WicketTag)
			{
				WicketTag wicketTag = (WicketTag) tag;
				if (wicketTag.isBorderTag() && tag.isOpen())
				{
					addToBorder = true;
				}
				else if (wicketTag.isBodyTag() && tag.isOpen())
				{
					addToBorder = false;
				}
				else if (wicketTag.isBodyTag() && tag.isClose())
				{
					addToBorder = true;
				}
				else if (wicketTag.isBorderTag() && tag.isClose())
				{
					addToBorder = false;
				}
			}

			LOGGER.trace("addToBorder? '{}'", addToBorder);

			// maintain bread crumbs and build components
			if (isComponentTag(tag))
			{
				if (tag.isOpen() || tag.isOpenClose())
				{
					final Component container = stack.peek().get();
					final Component cmp;
					final Node child = new Node();

					LOGGER.trace("Current parent component is '{}'", container);

					if (container == null)
					{
						cmp = null;
					}
					else
					{
						cmp = Utils.buildComponent(component, tag.getId(), child);
					}

					LOGGER.trace("Resolved component is '{}'. Adding to parent now.", cmp);

					if (cmp != null)
					{
						if (container instanceof MarkupContainer)
						{
							child.border = addToBorder && container instanceof Border;
							child.id = cmp.getId();
							node.addChild(child);
						}
						else if (container == null)
						{
							throw new RuntimeException("component " + tag.getId()
									+ " was auto wired, but its parent not!");
						}
						else
						{
							throw new RuntimeException(
									"only containers may contain child elements. type of "
											+ container + " is not a container!");
						}
					}
					// push even if cmp is null, to track if parent is
					// auto-wired
					if (tag.isOpen() && !tag.hasNoCloseTag())
					{
						LOGGER.trace("Tag has a body. Adding to stack now.");

						stack.push(new AtomicReference<>(cmp));
						if (cmp != null)
						{
							node = child;
						}

						LOGGER.trace("Current stack: '{}'", stack);
					}
				}
				else if (tag.isClose() && !tag.getOpenTag().isAutoComponentTag())
				{
					// the container tag is part of the inherited markup. do
					// not pop stack on container tag close.
					if (containerTag == null || !tag.closes(containerTag))
					{
						LOGGER.trace("Tag is closing. Pop the stack now.");

						if (stack.pop().get() != null)
						{
							node = node.parent;
						}

						LOGGER.trace("Current stack: '{}'", stack);
					}
				}
			}

			LOGGER.trace("--- Tag done. ---");

			stream.next();
		}

		if (stack.size() != 1)
		{
			throw new RuntimeException("Stack must only contain one element " + stack);
		}

		return node;
	}

	private boolean skipFirstComponentTag(Component component, MarkupStream stream)
	{
		MarkupElement currentElement = stream.get();
		boolean hasSameId = currentElement instanceof ComponentTag
				&& ((ComponentTag) currentElement).getId().equals(component.getId());

		return hasSameId || component instanceof ListItem;
	}

	private boolean isComponentTag(ComponentTag tag)
	{
		return !(tag instanceof WicketTag) && !tag.isAutoComponentTag()
				|| tag.getName().equals(WicketContainerResolver.CONTAINER);
	}

}
