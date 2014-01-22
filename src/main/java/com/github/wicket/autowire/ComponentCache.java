package com.github.wicket.autowire;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.wicket.Component;

/**
*
*/
class ComponentCache extends ConcurrentHashMap<Class<? extends Component>, Value>
{
}
