package com.groupmantcg;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

@Singleton
class FeedbackService
{
	private static final long THROTTLE_MILLIS = 900L;
	private static final int MAX_REQUIREMENTS = 5;
	private final GroupmanTcgConfig config;
	private final ChatMessageManager chat;
	private long lastMessageAt;

	@Inject
	FeedbackService(GroupmanTcgConfig config, ChatMessageManager chat)
	{
		this.config = config;
		this.chat = chat;
	}

	void locked(String target, List<String> requirements)
	{
		String prefix = target == null || target.isEmpty() ? "Action locked" : target + " is locked";
		send(prefix + ". Required: " + summarize(requirements));
	}

	void missing(List<String> requirements)
	{
		send("Action locked. Missing cards: " + summarize(requirements));
	}

	private void send(String message)
	{
		long now = System.currentTimeMillis();
		if (!config.chatFeedback() || now - lastMessageAt < THROTTLE_MILLIS)
		{
			return;
		}
		lastMessageAt = now;
		chat.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("[Group TCG] " + message)
			.build());
	}

	private static String summarize(List<String> requirements)
	{
		int shown = Math.min(requirements.size(), MAX_REQUIREMENTS);
		String result = String.join(", ", requirements.subList(0, shown));
		return requirements.size() > shown ? result + " and " + (requirements.size() - shown) + " more" : result;
	}
}
