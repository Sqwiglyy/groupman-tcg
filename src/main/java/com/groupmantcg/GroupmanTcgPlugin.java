package com.groupmantcg;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Groupman TCG",
	description = "Multiplayer Bronzeman restrictions powered by a permanent shared OSRS TCG collection",
	tags = {"tcg", "group", "ironman", "hardcore", "bronzeman", "restriction", "multiplayer"}
)
public class GroupmanTcgPlugin extends Plugin
{
	@Inject
	private GroupmanTcgConfig config;
	@Inject
	private Client client;
	@Inject
	private SharedCollectionService collection;
	@Inject
	private MonsterCardCatalog monsters;
	@Inject
	private ItemCardCatalog items;
	@Inject
	private FeedbackService feedback;
	@Inject
	private SkillRestrictionEngine skillRestrictions;
	@Inject
	private LockedStateService lockedState;
	@Inject
	private ClientToolbar toolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private LockedNpcOverlay lockedNpcOverlay;
	@Inject
	private LockedGroundItemOverlay lockedGroundItemOverlay;
	@Inject
	private LockedWidgetItemOverlay lockedWidgetItemOverlay;
	@Inject
	private GroupPackRevealService packReveals;
	@Inject
	private GroupPackRevealOverlay packRevealOverlay;
	@Inject
	private TopTrumpsService topTrumps;
	@Inject
	private TopTrumpsOverlay topTrumpsOverlay;

	private GroupmanTcgPanel panel;
	private NavigationButton navigation;
	private int panelTicks;

	@Override
	protected void startUp()
	{
		collection.start();
		packReveals.start();
		topTrumps.start();
		panel = new GroupmanTcgPanel(collection, monsters, items);
		navigation = NavigationButton.builder()
			.tooltip("Groupman TCG")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		toolbar.addNavigation(navigation);
		overlayManager.add(lockedNpcOverlay);
		overlayManager.add(lockedGroundItemOverlay);
		overlayManager.add(lockedWidgetItemOverlay);
		overlayManager.add(packRevealOverlay);
		overlayManager.add(topTrumpsOverlay);
		log.info("Groupman TCG started with {} NPCs and {} items", monsters.size(), items.size());
	}

	@Override
	protected void shutDown()
	{
		packReveals.stop();
		topTrumps.stop();
		collection.stop();
		overlayManager.remove(lockedNpcOverlay);
		overlayManager.remove(lockedGroundItemOverlay);
		overlayManager.remove(lockedWidgetItemOverlay);
		overlayManager.remove(packRevealOverlay);
		overlayManager.remove(topTrumpsOverlay);
		if (navigation != null)
		{
			toolbar.removeNavigation(navigation);
		}
		navigation = null;
		panel = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		collection.onTick();
		packReveals.onTick();
		topTrumps.onTick();
		if (panel != null && ++panelTicks % 5 == 0)
		{
			GroupmanTcgPanel currentPanel = panel;
			SwingUtilities.invokeLater(() ->
			{
				if (currentPanel.isShowing())
				{
					currentPanel.refresh();
				}
			});
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		collection.contextChanged();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("osrstcg".equals(event.getGroup()) && "state".equals(event.getKey()))
		{
			collection.localCollectionChanged();
			packReveals.localStateChanged();
		}
		else if (GroupmanTcgConfig.GROUP.equals(event.getGroup())
			&& "collectionMode".equals(event.getKey()))
		{
			collection.contextChanged();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		collection.profileChanged();
		packReveals.profileChanged();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		collection.partyChanged();
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		collection.contextChanged();
	}

	@Subscribe
	public void onGroupCollectionSnapshot(GroupCollectionSnapshotMessage message)
	{
		collection.snapshotReceived(message);
	}

	@Subscribe
	public void onGroupPackReveal(GroupPackRevealMessage message)
	{
		packReveals.messageReceived(message);
	}

	@Subscribe
	public void onTopTrumpsChallenge(TopTrumpsChallengeMessage message)
	{
		topTrumps.challengeReceived(message);
	}

	@Subscribe
	public void onTopTrumpsResponse(TopTrumpsResponseMessage message)
	{
		topTrumps.responseReceived(message);
	}

	@Subscribe
	public void onTopTrumpsResult(TopTrumpsResultMessage message)
	{
		topTrumps.resultReceived(message);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuEntry source = event.getMenuEntry();
		Player player = source.getPlayer();
		if (!isPlayerMenuAction(source.getType()) || player == null || player.getName() == null
			|| !topTrumps.canChallenge(player.getName()))
		{
			return;
		}
		for (MenuEntry existing : client.getMenuEntries())
		{
			if (TopTrumpsService.MENU_OPTION.equals(existing.getOption()))
			{
				return;
			}
		}
		String playerName = player.getName();
		client.createMenuEntry(-1)
			.setOption(TopTrumpsService.MENU_OPTION)
			.setTarget(source.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(ignored -> topTrumps.challenge(playerName));
	}

	private static boolean isPlayerMenuAction(MenuAction type)
	{
		return type == MenuAction.PLAYER_FIRST_OPTION || type == MenuAction.PLAYER_SECOND_OPTION
			|| type == MenuAction.PLAYER_THIRD_OPTION || type == MenuAction.PLAYER_FOURTH_OPTION
			|| type == MenuAction.PLAYER_FIFTH_OPTION || type == MenuAction.PLAYER_SIXTH_OPTION
			|| type == MenuAction.PLAYER_SEVENTH_OPTION || type == MenuAction.PLAYER_EIGHTH_OPTION
			|| type == MenuAction.RUNELITE_PLAYER;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (lockedState.restrictionsBypassed())
		{
			return;
		}
		SkillRestrictionEngine.Decision skillDecision = skillRestrictions.handle(event);
		if (skillDecision == SkillRestrictionEngine.Decision.BLOCKED)
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc != null && blockNpc(event, entry, npc))
		{
			return;
		}
		if (skillDecision == SkillRestrictionEngine.Decision.ALLOWED)
		{
			return;
		}
		blockItem(event, entry);
	}

	private boolean blockNpc(MenuOptionClicked event, MenuEntry entry, NPC npc)
	{
		LockedStateService.Requirement lock = lockedState.npcLock(npc);
		if (lock == null)
		{
			return false;
		}

		String option = clean(entry.getOption());
		if ("examine".equals(option))
		{
			return false;
		}
		MenuAction type = entry.getType();
		boolean targeted = type == MenuAction.ITEM_USE_ON_NPC || type == MenuAction.WIDGET_TARGET_ON_NPC;
		boolean combatAction = "attack".equals(option) || targeted;
		if (!(config.restrictAllNpcInteractions() || (config.restrictCombat() && combatAction)))
		{
			return false;
		}

		event.consume();
		feedback.locked(lock.target(), new java.util.ArrayList<>(lock.cards()));
		return true;
	}

	private void blockItem(MenuOptionClicked event, MenuEntry entry)
	{
		if (!config.restrictItems())
		{
			return;
		}
		int itemId = entry.getItemId();
		if (itemId <= 0)
		{
			return;
		}
		LockedStateService.Requirement lock = lockedState.itemLock(itemId);
		if (lock == null)
		{
			return;
		}

		String option = clean(entry.getOption());
		if ("examine".equals(option) || "drop".equals(option) || "destroy".equals(option)
			|| (config.allowBankDeposits() && option.startsWith("deposit")))
		{
			return;
		}
		event.consume();
		feedback.locked(lock.target(), new java.util.ArrayList<>(lock.cards()));
	}

	private static String clean(String text)
	{
		return Text.removeTags(text == null ? "" : text).trim().toLowerCase(Locale.ROOT);
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(184, 115, 51));
		graphics.fillOval(1, 1, 22, 22);
		graphics.setColor(new Color(49, 39, 32));
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		graphics.drawString("G", 7, 18);
		graphics.dispose();
		return image;
	}

	@Provides
	GroupmanTcgConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupmanTcgConfig.class);
	}
}
