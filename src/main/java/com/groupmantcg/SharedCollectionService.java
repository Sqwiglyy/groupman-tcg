package com.groupmantcg;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;

/** Owns the effective collection and the grow-only official-GIM union. */
@Slf4j
@Singleton
class SharedCollectionService
{
	private static final int PROTOCOL = 1;
	private static final String CACHE_KEY = "sharedCollectionV1";
	private static final int REFRESH_TICKS = 5;
	private static final int REANNOUNCE_TICKS = 100;

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final GroupmanTcgConfig config;
	private final LocalCollection localCollection;
	private final CardBitsetIndex index;
	private final GroupCacheCodec cacheCodec;
	private final PartyService partyService;
	private final WSClient wsClient;

	private volatile Set<String> sharedCards = Collections.emptySet();
	private volatile GroupSyncStatus status = soloStatus(0);
	private Set<String> roster = Collections.emptySet();
	private final Set<String> syncedMembers = new HashSet<>();
	private String groupKey;
	private String groupName;
	private String lastSentBits;
	private long lastPartyId = -1L;
	private int ticks;
	private boolean started;

	@Inject
	SharedCollectionService(Client client, ClientThread clientThread, ConfigManager configManager,
		GroupmanTcgConfig config, LocalCollection localCollection, CardBitsetIndex index,
		GroupCacheCodec cacheCodec, PartyService partyService, WSClient wsClient)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.config = config;
		this.localCollection = localCollection;
		this.index = index;
		this.cacheCodec = cacheCodec;
		this.partyService = partyService;
		this.wsClient = wsClient;
	}

	void start()
	{
		started = true;
		wsClient.registerMessage(GroupCollectionSnapshotMessage.class);
		requestRefresh(true);
	}

	void stop()
	{
		started = false;
		wsClient.unregisterMessage(GroupCollectionSnapshotMessage.class);
		reset();
	}

	Set<String> cards()
	{
		return status.isActive() ? sharedCards : localCollection.getCards();
	}

	GroupSyncStatus status()
	{
		return status;
	}

	String activeGroupKey()
	{
		return started && status.isActive() && partyService.isInParty() ? groupKey : null;
	}

	String verifiedPartySenderName(long memberId, String incomingGroupKey)
	{
		if (!started || !status.isActive() || !partyService.isInParty() || groupKey == null
			|| !groupKey.equals(incomingGroupKey))
		{
			return null;
		}
		PartyMember sender = partyService.getMemberById(memberId);
		if (sender == null || sender.getDisplayName() == null
			|| !roster.contains(EntityCardCatalog.normalize(sender.getDisplayName())))
		{
			return null;
		}
		return sender.getDisplayName().trim();
	}

	PartyMember verifiedPartyMember(String displayName)
	{
		if (!started || !status.isActive() || !partyService.isInParty() || displayName == null)
		{
			return null;
		}
		PartyMember member = partyService.getMemberByDisplayName(displayName);
		if (member == null || member.getDisplayName() == null
			|| !roster.contains(EntityCardCatalog.normalize(member.getDisplayName())))
		{
			return null;
		}
		return member;
	}

	void onTick()
	{
		if (started && ++ticks % REFRESH_TICKS == 0)
		{
			refresh(ticks % REANNOUNCE_TICKS == 0);
		}
	}

	void localCollectionChanged()
	{
		localCollection.invalidate();
		requestRefresh(true);
	}

	void contextChanged()
	{
		requestRefresh(true);
	}

	void profileChanged()
	{
		localCollection.invalidate();
		clientThread.invokeLater(() ->
		{
			reset();
			refresh(true);
		});
	}

	void partyChanged()
	{
		clientThread.invokeLater(() ->
		{
			syncedMembers.clear();
			lastPartyId = -1L;
			refresh(true);
		});
	}

	void snapshotReceived(GroupCollectionSnapshotMessage message)
	{
		clientThread.invokeLater(() -> merge(message));
	}

	private void requestRefresh(boolean forceSend)
	{
		clientThread.invokeLater(() -> refresh(forceSend));
	}

	private void refresh(boolean forceSend)
	{
		Set<String> local = localCollection.getCards();
		if (!started || config.collectionMode() != CollectionMode.GROUP_IRONMAN)
		{
			reset();
			status = soloStatus(local.size());
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getAccountType() == null
			|| !client.getAccountType().isGroupIronman())
		{
			reset();
			status = inactiveStatus(local.size(), "Log into a Group Ironman account");
			return;
		}

		ClanSettings settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
		if (settings == null || settings.getName() == null || settings.getMembers() == null)
		{
			reset();
			status = inactiveStatus(local.size(), "Waiting for the official GIM roster");
			return;
		}

		String discoveredKey = EntityCardCatalog.normalize(settings.getName());
		if (!discoveredKey.equals(groupKey))
		{
			groupKey = discoveredKey;
			groupName = settings.getName().trim();
			sharedCards = loadCache();
			syncedMembers.clear();
			lastSentBits = null;
			lastPartyId = -1L;
		}

		Set<String> discoveredRoster = new HashSet<>();
		for (ClanMember member : settings.getMembers())
		{
			if (member != null && member.getName() != null)
			{
				discoveredRoster.add(EntityCardCatalog.normalize(member.getName()));
			}
		}
		roster = Collections.unmodifiableSet(discoveredRoster);
		syncedMembers.retainAll(roster);

		PartyMember localPartyMember = partyService.getLocalMember();
		if (localPartyMember != null && localPartyMember.getDisplayName() != null)
		{
			String localName = EntityCardCatalog.normalize(localPartyMember.getDisplayName());
			if (roster.contains(localName))
			{
				syncedMembers.add(localName);
			}
		}

		Set<String> combined = new HashSet<>(sharedCards);
		boolean grew = combined.addAll(local);
		if (grew)
		{
			setSharedCards(combined);
			persist();
		}
		updateActiveStatus();
		send(forceSend || grew);
	}

	private void merge(GroupCollectionSnapshotMessage message)
	{
		if (!started || !status.isActive() || !partyService.isInParty() || message == null
			|| message.getProtocol() != PROTOCOL || !groupKey.equals(message.getGroupKey())
			|| !index.fingerprint().equals(message.getCatalogFingerprint()))
		{
			return;
		}

		PartyMember sender = partyService.getMemberById(message.getMemberId());
		if (sender == null || sender.getDisplayName() == null)
		{
			return;
		}
		String senderName = EntityCardCatalog.normalize(sender.getDisplayName());
		if (!roster.contains(senderName))
		{
			log.debug("Rejected group snapshot from non-roster member {}", senderName);
			return;
		}

		final Set<String> incoming;
		try
		{
			incoming = index.decode(message.getUnlockBits());
		}
		catch (IllegalArgumentException ex)
		{
			log.debug("Rejected malformed group snapshot", ex);
			return;
		}

		syncedMembers.add(senderName);
		Set<String> combined = new HashSet<>(sharedCards);
		int previous = combined.size();
		if (combined.addAll(incoming))
		{
			setSharedCards(combined);
			persist();
			updateActiveStatus();
			send(true);
			if (config.syncMessages())
			{
				int added = combined.size() - previous;
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Groupman TCG] The group gained " + added + " unlock" + (added == 1 ? "" : "s") + ".", null);
			}
		}
		else
		{
			updateActiveStatus();
		}
	}

	private Set<String> loadCache()
	{
		try
		{
			String json = configManager.getRSProfileConfiguration(GroupmanTcgConfig.GROUP, CACHE_KEY);
			GroupCacheCodec.Cache cache = cacheCodec.decode(json);
			if (cache == null || !groupKey.equals(cache.groupKey)
				|| !index.fingerprint().equals(cache.catalogFingerprint))
			{
				return Collections.emptySet();
			}
			return index.decode(cache.unlockBits);
		}
		catch (Exception ex)
		{
			log.debug("Unable to load shared collection cache", ex);
			return Collections.emptySet();
		}
	}

	private void persist()
	{
		String bits = index.encode(sharedCards);
		configManager.setRSProfileConfiguration(GroupmanTcgConfig.GROUP, CACHE_KEY,
			cacheCodec.encode(groupKey, index.fingerprint(), bits));
	}

	private void send(boolean force)
	{
		if (!status.isActive() || !partyService.isInParty())
		{
			return;
		}
		String bits = index.encode(sharedCards);
		long partyId = partyService.getPartyId();
		if (!force && partyId == lastPartyId && bits.equals(lastSentBits))
		{
			return;
		}
		GroupCollectionSnapshotMessage message = new GroupCollectionSnapshotMessage();
		message.setProtocol(PROTOCOL);
		message.setGroupKey(groupKey);
		message.setCatalogFingerprint(index.fingerprint());
		message.setUnlockBits(bits);
		partyService.send(message);
		lastSentBits = bits;
		lastPartyId = partyId;
	}

	private void updateActiveStatus()
	{
		boolean inParty = partyService.isInParty();
		status = new GroupSyncStatus(true, true, inParty, groupName, sharedCards.size(),
			syncedMembers.size(), roster.size(), inParty ? "Live Party sync" : "Using offline cache; join RuneLite Party to sync");
	}

	private void setSharedCards(Set<String> cards)
	{
		sharedCards = Collections.unmodifiableSet(new HashSet<>(cards));
	}

	private void reset()
	{
		sharedCards = Collections.emptySet();
		roster = Collections.emptySet();
		syncedMembers.clear();
		groupKey = null;
		groupName = null;
		lastSentBits = null;
		lastPartyId = -1L;
	}

	private GroupSyncStatus inactiveStatus(int cards, String detail)
	{
		return new GroupSyncStatus(true, false, partyService.isInParty(), "", cards, 0, 0, detail);
	}

	private static GroupSyncStatus soloStatus(int cards)
	{
		return new GroupSyncStatus(false, false, false, "", cards, 0, 0, "Solo OSRS TCG collection");
	}
}
