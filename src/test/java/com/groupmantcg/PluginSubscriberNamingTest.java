package com.groupmantcg;

import java.lang.reflect.Method;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;
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

	@Test
	public void exposesTheGroupTcgNameAndServerCollectionModes()
	{
		PluginDescriptor descriptor = GroupmanTcgPlugin.class.getAnnotation(PluginDescriptor.class);
		assertEquals("Group TCG", descriptor.name());
		assertEquals("Solo collection", CollectionMode.SOLO.toString());
		assertEquals("Shared server collection", CollectionMode.GROUP_IRONMAN.toString());
	}

	@Test
	public void linksToTheSelfHostingGuide()
	{
		assertEquals("https://github.com/Sqwiglyy/groupman-tcg-server",
			GroupmanTcgPanel.SERVER_SETUP_GUIDE_URL);
		assertEquals("https://discord.gg/yHzttZnQkt", GroupmanTcgPanel.DISCORD_INVITE_URL);
	}
}
