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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Group TCG",
	description = "Use OSRS TCG cards as solo or shared Bronzeman unlocks",
	tags = {"tcg", "group", "bronzeman", "restriction", "multiplayer", "solo"}
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
	private HostedSyncService hostedSync;
	@Inject
	private GroupPackRevealOverlay packRevealOverlay;
	@Inject
	private TopTrumpsService topTrumps;
	@Inject
	private TopTrumpsOverlay topTrumpsOverlay;
	@Inject
	private CollectionAlbumManager collectionAlbums;
	@Inject
	private CardVisualCatalog cardVisuals;

	private GroupmanTcgPanel panel;
	private NavigationButton navigation;
	private int panelTicks;

	@Override
	protected void startUp()
	{
		collection.start();
		hostedSync.start();
		packReveals.start();
		topTrumps.start();
		panel = new GroupmanTcgPanel(collection, hostedSync, topTrumps, collectionAlbums,
			monsters, items, cardVisuals);
		navigation = NavigationButton.builder()
			.tooltip("Group TCG")
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
		log.info("Group TCG started with {} NPCs and {} items", monsters.size(), items.size());
	}

	@Override
	protected void shutDown()
	{
		collectionAlbums.dispose();
		packReveals.stop();
		topTrumps.stop();
		hostedSync.stop();
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
		hostedSync.onTick();
		topTrumps.onTick();
		if (++panelTicks % 5 == 0)
		{
			collectionAlbums.refreshIfVisible();
			GroupmanTcgPanel currentPanel = panel;
			SwingUtilities.invokeLater(() ->
			{
				if (currentPanel != null && currentPanel.isShowing())
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
		hostedSync.contextChanged();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("osrstcg".equals(event.getGroup()) && "state".equals(event.getKey()))
		{
			collection.localCollectionChanged();
			packReveals.localStateChanged();
			hostedSync.localCollectionChanged();
		}
		else if (GroupmanTcgConfig.GROUP.equals(event.getGroup())
			&& "collectionMode".equals(event.getKey()))
		{
			collection.contextChanged();
			hostedSync.collectionModeChanged();
		}
		else if (GroupmanTcgConfig.GROUP.equals(event.getGroup())
			&& "hostedSyncEnabled".equals(event.getKey()))
		{
			hostedSync.contextChanged();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		collection.profileChanged();
		packReveals.profileChanged();
		hostedSync.profileChanged();
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
		String option = clean(entry.getOption());
		LockedStateService.Requirement lock = clickedItemLock(event, entry, option);
		if (lock == null)
		{
			return;
		}

		if ("examine".equals(option) || "drop".equals(option) || "destroy".equals(option)
			|| (config.allowBankDeposits() && option.startsWith("deposit")))
		{
			return;
		}
		event.consume();
		feedback.locked(lock.target(), new java.util.ArrayList<>(lock.cards()));
	}

	private LockedStateService.Requirement clickedItemLock(MenuOptionClicked event, MenuEntry entry,
		String option)
	{
		MenuAction action = event.getMenuAction();
		if (isGroundItemAcquisition(action, option))
		{
			// Ground-item menu entries expose the item through their identifier, not itemId.
			return lockedState.itemLock(event.getId());
		}

		int interfaceGroup = WidgetUtil.componentToInterface(entry.getParam1());
		if (isShopPurchase(action, interfaceGroup, option))
		{
			// Most shop entries expose itemId; name fallback covers alternate shop widgets.
			int itemId = entry.getItemId();
			return itemId > 0
				? lockedState.itemLock(itemId)
				: lockedState.itemLock(Text.removeTags(entry.getTarget()).trim());
		}
		if (isGrandExchangeSearchOperation(action, interfaceGroup, isGrandExchangeOpen()))
		{
			// GE search results are chatbox widgets and expose only the result's target text.
			return lockedState.itemLock(Text.removeTags(entry.getTarget()).trim());
		}

		return lockedState.itemLock(entry.getItemId());
	}

	static boolean isGroundItemAcquisition(MenuAction action, String option)
	{
		if (action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
		{
			return true;
		}
		if (!"take".equals(option))
		{
			return false;
		}
		return action == MenuAction.GROUND_ITEM_FIRST_OPTION
			|| action == MenuAction.GROUND_ITEM_SECOND_OPTION
			|| action == MenuAction.GROUND_ITEM_THIRD_OPTION
			|| action == MenuAction.GROUND_ITEM_FOURTH_OPTION
			|| action == MenuAction.GROUND_ITEM_FIFTH_OPTION;
	}

	static boolean isShopPurchase(MenuAction action, int interfaceGroup, String option)
	{
		return LockedWidgetItemOverlay.isShopInterface(interfaceGroup)
			&& option.startsWith("buy")
			&& (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY);
	}

	static boolean isGrandExchangeSearchOperation(MenuAction action, int interfaceGroup,
		boolean grandExchangeOpen)
	{
		return grandExchangeOpen && interfaceGroup == InterfaceID.CHATBOX
			&& (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY);
	}

	private boolean isGrandExchangeOpen()
	{
		return client.getWidget(InterfaceID.GE_OFFERS, 0) != null;
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
