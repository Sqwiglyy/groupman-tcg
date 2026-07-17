package com.groupmantcg;

import java.lang.reflect.Method;
import net.runelite.client.eventbus.Subscribe;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PluginSubscriberNamingTest
{
	@Test
	public void subscriberNamesMatchRuneLiteEventTypes()
	{
		for (Method method : GroupmanTcgPlugin.class.getDeclaredMethods())
		{
			if (method.getAnnotation(Subscribe.class) == null)
			{
				continue;
			}

			assertEquals("Subscriber must be named after its event type",
				"on" + method.getParameterTypes()[0].getSimpleName(), method.getName());
		}
	}
}
