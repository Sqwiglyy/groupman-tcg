package com.groupmantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

class GroupmanTcgPanel extends PluginPanel
{
	private static final int MAX_RESULTS = 20;
	private static final DateTimeFormatter RECENT_DATE = DateTimeFormatter.ofPattern("d MMM HH:mm")
		.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter RECENT_TOOLTIP_DATE = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm")
		.withZone(ZoneId.systemDefault());
	private final SharedCollectionService collection;
	private final HostedSyncService hosted;
	private final TopTrumpsService topTrumps;
	private final CollectionAlbumManager albums;
	private final MonsterCardCatalog monsters;
	private final ItemCardCatalog items;
	private final CardVisualCatalog visuals;
	private final IconTextField search = new IconTextField();
	private final JComboBox<CollectionChoice> collectionSelector = new JComboBox<>();
	private final JLabel syncStatus = muted("Loading collection...");
	private final JLabel hostedStatus = muted("Loading private server...");
	private final JPanel hostedActions = body();
	private final JPanel leaderboard = body();
	private final JPanel results = body();
	private boolean updatingSelector;

	GroupmanTcgPanel(SharedCollectionService collection, HostedSyncService hosted,
		TopTrumpsService topTrumps, CollectionAlbumManager albums,
		MonsterCardCatalog monsters, ItemCardCatalog items, CardVisualCatalog visuals)
	{
		this.collection = collection;
		this.hosted = hosted;
		this.topTrumps = topTrumps;
		this.albums = albums;
		this.monsters = monsters;
		this.items = items;
		this.visuals = visuals;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 28));
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				refreshSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				refreshSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				refreshSearch();
			}
		});
		collectionSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		collectionSelector.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 28));
		collectionSelector.addActionListener(event ->
		{
			if (!updatingSelector)
			{
				refreshSearch();
			}
		});

		add(search);
		add(Box.createVerticalStrut(6));
		add(header("Browse collection"));
		add(collectionSelector);
		add(Box.createVerticalStrut(4));
		add(actionButton("Open full collection", this::openFullCollection));
		add(header("Group status"));
		add(syncStatus);
		add(header("Private server"));
		add(hostedStatus);
		add(hostedActions);
		add(header("Server leaderboard"));
		add(leaderboard);
		add(header("Card lookup"));
		add(results);
		refresh();
	}

	private void openFullCollection()
	{
		CollectionChoice selected = (CollectionChoice) collectionSelector.getSelectedItem();
		if (selected == null)
		{
			albums.show("", "Shared collection");
			return;
		}
		albums.show(selected.key, selected.displayName);
	}

	void refresh()
	{
		GroupSyncStatus status = collection.status();
		if (!status.isGroupMode())
		{
			syncStatus.setText("Solo · " + status.getCards() + " cards");
			syncStatus.setForeground(Color.WHITE);
		}
		else if (!status.isActive())
		{
			syncStatus.setText(status.getDetail() + " · " + status.getCards() + " local cards");
			syncStatus.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		else
		{
			String members = status.getSyncedMembers() + " approved member"
				+ (status.getSyncedMembers() == 1 ? "" : "s");
			syncStatus.setText(status.getGroupName() + " · " + status.getCards() + " cards · " + members);
			syncStatus.setForeground(status.isConnected()
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
		}
		syncStatus.setToolTipText(status.getDetail());
		refreshHosted();
		refreshLeaderboard();
		refreshCollectionChoices();
		refreshSearch();
	}

	private void refreshLeaderboard()
	{
		leaderboard.removeAll();
		HostedSyncStatus current = hosted.status();
		if (!current.linked())
		{
			leaderboard.add(muted("Join a private server to compare collections."));
		}
		else if (current.state() == HostedSyncStatus.State.WAITING_APPROVAL)
		{
			leaderboard.add(muted("Leaderboard available after server approval."));
		}
		else
		{
			List<CollectionLeaderboard.Entry> entries = CollectionLeaderboard.rank(
				collection.memberCollections(), visuals);
			if (entries.isEmpty())
			{
				leaderboard.add(muted("Waiting for server collections to sync."));
			}
			else
			{
				for (int index = 0; index < entries.size(); index++)
				{
					leaderboard.add(leaderboardRow(index + 1, entries.get(index)));
					if (index + 1 < entries.size())
					{
						leaderboard.add(Box.createVerticalStrut(3));
					}
				}
			}
		}
		leaderboard.revalidate();
		leaderboard.repaint();
	}

	private JPanel leaderboardRow(int rank, CollectionLeaderboard.Entry entry)
	{
		JPanel row = body();
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel player = new JLabel("#" + rank + "  " + entry.playerName());
		player.setForeground(Color.WHITE);
		heading.add(player, BorderLayout.CENTER);
		JLabel points = new JLabel(compactPoints(entry.points()) + " pts");
		points.setForeground(ColorScheme.BRAND_ORANGE);
		heading.add(points, BorderLayout.EAST);
		row.add(heading);
		JLabel cards = muted(entry.uniqueCards() + " unique card" + (entry.uniqueCards() == 1 ? "" : "s"));
		cards.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		row.add(cards);
		String tooltip = String.format(Locale.UK, "%,d points from %,d unique OSRS TCG cards. "
			+ "Duplicate copies do not add points.", entry.points(), entry.uniqueCards());
		row.setToolTipText(tooltip);
		heading.setToolTipText(tooltip);
		player.setToolTipText(tooltip);
		points.setToolTipText(tooltip);
		cards.setToolTipText(tooltip);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static String compactPoints(long points)
	{
		if (points < 1_000L)
		{
			return Long.toString(points);
		}
		if (points < 1_000_000L)
		{
			return compactNumber(points / 1_000.0d) + "k";
		}
		if (points < 1_000_000_000L)
		{
			return compactNumber(points / 1_000_000.0d) + "m";
		}
		return compactNumber(points / 1_000_000_000.0d) + "b";
	}

	private static String compactNumber(double value)
	{
		String formatted = String.format(Locale.ROOT, value >= 100.0d ? "%.0f" : "%.1f", value);
		return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
	}

	private void refreshHosted()
	{
		HostedSyncStatus current = hosted.status();
		hostedStatus.setText(current.detail());
		hostedStatus.setToolTipText(current.linked()
			? current.groupName() + " · " + current.groupId() : current.detail());
		switch (current.state())
		{
			case ONLINE:
				hostedStatus.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
				break;
			case SYNCING:
			case WAITING_APPROVAL:
				hostedStatus.setForeground(ColorScheme.BRAND_ORANGE);
				break;
			case ERROR:
			case WRONG_PROFILE:
				hostedStatus.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
				break;
			default:
				hostedStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		hostedActions.removeAll();
		if (current.state() == HostedSyncStatus.State.NOT_LINKED)
		{
			hostedActions.add(actionButton("Create private group", this::createHostedGroup));
			hostedActions.add(Box.createVerticalStrut(4));
			hostedActions.add(actionButton("Join private group", this::joinHostedGroup));
		}
		else if (current.linked())
		{
			JLabel id = muted("Group ID: " + abbreviated(current.groupId()));
			id.setToolTipText(current.groupId());
			hostedActions.add(id);
			if (!current.memberLabel().isEmpty())
			{
				hostedActions.add(muted("Your server label: " + current.memberLabel()));
			}
			if (current.owner())
			{
				if (!current.inviteCode().isEmpty())
				{
					JLabel invite = muted("Invite: " + current.inviteCode());
					invite.setToolTipText("Share this only with people you want on this private server.");
					hostedActions.add(invite);
					hostedActions.add(actionButton("Copy join details", this::copyJoinDetails));
				}
				hostedActions.add(Box.createVerticalStrut(4));
				hostedActions.add(actionButton(current.inviteCode().isEmpty()
					? "Create invite" : "Rotate invite", this::rotateInvite));
				for (HostedSyncStatus.Member member : current.members())
				{
					if (member.pending())
					{
						hostedActions.add(Box.createVerticalStrut(4));
						String pendingName = member.playerName().isEmpty() ? member.label() : member.playerName();
						JLabel warning = muted("Confirm " + pendingName + " with your teammate");
						warning.setToolTipText("Confirm the displayed RuneScape name with your friend before approving.");
						hostedActions.add(warning);
						hostedActions.add(actionButton("Approve " + pendingName, () -> approve(member)));
						hostedActions.add(actionButton("Reject " + pendingName, () -> revoke(member)));
					}
					else if (!member.revoked() && "member".equals(member.role()))
					{
						hostedActions.add(Box.createVerticalStrut(4));
						String memberName = member.playerName().isEmpty() ? member.label() : member.playerName();
						hostedActions.add(actionButton("Revoke " + memberName, () -> revoke(member)));
					}
				}
			}
			for (HostedSyncStatus.Member member : current.members())
			{
				if (member.approved() && !member.id().equals(current.memberId()))
				{
					hostedActions.add(Box.createVerticalStrut(4));
					String player = member.playerName().isEmpty() ? member.label() : member.playerName();
					String mode = "solo".equals(member.collectionMode())
						? "Solo collection" : "Shared collection";
					hostedActions.add(muted(player + " - " + mode));
					hostedActions.add(actionButton("Challenge " + player,
						() -> topTrumps.challengeMember(member)));
				}
			}
			if (current.state() == HostedSyncStatus.State.WAITING_APPROVAL
				|| current.state() == HostedSyncStatus.State.ERROR)
			{
				hostedActions.add(Box.createVerticalStrut(4));
				hostedActions.add(actionButton("Sync now", hosted::syncNow));
			}
			hostedActions.add(Box.createVerticalStrut(4));
			hostedActions.add(actionButton("Disconnect this profile", this::disconnectHosted));
		}
		hostedActions.revalidate();
		hostedActions.repaint();
	}

	private void createHostedGroup()
	{
		JPasswordField setupKey = new JPasswordField();
		JPanel form = body();
		form.add(new JLabel("Worker setup key"));
		form.add(setupKey);
		form.add(Box.createVerticalStrut(6));
		form.add(muted("Used once to claim this private Worker; never saved by the plugin."));
		form.add(muted("Your RuneScape display name will be stored on this private server."));
		int choice = JOptionPane.showConfirmDialog(this, form,
			"Create private Group TCG group", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice == JOptionPane.OK_OPTION)
		{
			char[] password = setupKey.getPassword();
			String value = new String(password);
			Arrays.fill(password, '\0');
			setupKey.setText("");
			hosted.createGroup(value, this::actionFinished);
		}
	}

	private void joinHostedGroup()
	{
		JTextField groupId = new JTextField();
		JTextField invite = new JTextField();
		JPanel form = body();
		form.add(new JLabel("Group ID"));
		form.add(groupId);
		form.add(Box.createVerticalStrut(6));
		form.add(new JLabel("Invite code"));
		form.add(invite);
		int choice = JOptionPane.showConfirmDialog(this, form,
			"Join private Group TCG server",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice == JOptionPane.OK_OPTION)
		{
			hosted.joinGroup(groupId.getText(), invite.getText(), this::actionFinished);
		}
	}

	private void approve(HostedSyncStatus.Member member)
	{
		hosted.approveMember(member.id(), this::actionFinished);
	}

	private void revoke(HostedSyncStatus.Member member)
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Remove " + member.label() + " from this server? Their permanent unlocks remain.",
			"Revoke server member", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			hosted.revokeMember(member.id(), this::actionFinished);
		}
	}

	private void rotateInvite()
	{
		hosted.rotateInvite(this::actionFinished);
	}

	private void disconnectHosted()
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Remove this profile's server token? Shared unlocks remain cached.",
			"Disconnect private server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			hosted.disconnect();
			refresh();
		}
	}

	private void copyJoinDetails()
	{
		HostedSyncStatus current = hosted.status();
		String text = "Group TCG group ID: " + current.groupId()
			+ System.lineSeparator() + "Invite code: " + current.inviteCode();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		hostedStatus.setText("Join details copied");
	}

	private void actionFinished(String error)
	{
		if (error != null)
		{
			JOptionPane.showMessageDialog(this, error, "Private server", JOptionPane.ERROR_MESSAGE);
		}
		refresh();
	}

	private static JButton actionButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		button.addActionListener(event -> action.run());
		return button;
	}

	private static String abbreviated(String value)
	{
		return value == null || value.length() <= 12 ? value : value.substring(0, 8) + "...";
	}

	private void refreshCollectionChoices()
	{
		CollectionChoice selected = (CollectionChoice) collectionSelector.getSelectedItem();
		String selectedKey = selected == null ? "" : selected.key;
		DefaultComboBoxModel<CollectionChoice> model = new DefaultComboBoxModel<>();
		model.addElement(new CollectionChoice("", "Shared collection", collection.cards()));
		for (Map.Entry<String, Set<String>> member : collection.memberCollections().entrySet())
		{
			model.addElement(new CollectionChoice(EntityCardCatalog.normalize(member.getKey()),
				member.getKey(), member.getValue()));
		}

		updatingSelector = true;
		try
		{
			collectionSelector.setModel(model);
			for (int i = 0; i < model.getSize(); i++)
			{
				if (model.getElementAt(i).key.equals(selectedKey))
				{
					collectionSelector.setSelectedIndex(i);
					return;
				}
			}
			collectionSelector.setSelectedIndex(0);
		}
		finally
		{
			updatingSelector = false;
		}
	}

	private void refreshSearch()
	{
		results.removeAll();
		String query = EntityCardCatalog.normalize(search.getText());
		if (query.isEmpty())
		{
			showRecentCards();
		}
		else
		{
			CollectionChoice choice = (CollectionChoice) collectionSelector.getSelectedItem();
			boolean shared = choice == null || choice.key.isEmpty();
			Set<String> owned = choice == null ? collection.cards() : choice.cards;
			Map<String, Set<String>> matches = new TreeMap<>();
			collectMatches(matches, "NPC: ", monsters.entries(), query);
			collectMatches(matches, "Item: ", items.entries(), query);
			int shown = 0;
			for (Map.Entry<String, Set<String>> match : matches.entrySet())
			{
				if (shown++ >= MAX_RESULTS)
				{
					results.add(muted("More results omitted"));
					break;
				}
				boolean unlocked = ownsAny(owned, match.getValue());
				results.add(resultRow(match.getKey(), unlocked, match.getValue(), shared));
			}
			if (matches.isEmpty())
			{
				results.add(muted("No matching tracked card"));
			}
		}
		results.revalidate();
		results.repaint();
	}

	private void showRecentCards()
	{
		CollectionChoice choice = (CollectionChoice) collectionSelector.getSelectedItem();
		boolean shared = choice == null || choice.key.isEmpty();
		String collectionKey = choice == null ? "" : choice.key;
		List<HostedCollectionSnapshot.RecentCard> recent = collection.recentCards(collectionKey, MAX_RESULTS);
		if (recent.isEmpty())
		{
			results.add(muted("No dated card pulls available yet."));
			return;
		}
		results.add(muted("Most recent card pulls"));
		for (HostedCollectionSnapshot.RecentCard card : recent)
		{
			results.add(recentCardRow(card, shared));
		}
	}

	private JPanel recentCardRow(HostedCollectionSnapshot.RecentCard card, boolean shared)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
		JLabel name = new JLabel(card.cardName() + (card.foil() ? " (foil)" : ""));
		name.setForeground(card.foil() ? ColorScheme.BRAND_ORANGE : Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		JLabel detail = new JLabel(shared ? card.owner() : RECENT_DATE.format(Instant.ofEpochMilli(card.pulledAt())));
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		String tooltip = card.owner() + " - pulled "
			+ RECENT_TOOLTIP_DATE.format(Instant.ofEpochMilli(card.pulledAt()));
		if (card.foil())
		{
			tooltip += " - foil";
		}
		name.setToolTipText(tooltip);
		detail.setToolTipText(tooltip);
		row.add(detail, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static void collectMatches(Map<String, Set<String>> destination, String prefix,
		Map<String, Set<String>> source, String query)
	{
		for (Map.Entry<String, Set<String>> entry : source.entrySet())
		{
			if (entry.getKey().contains(query))
			{
				destination.put(prefix + display(entry.getKey()), entry.getValue());
			}
		}
	}

	private JPanel resultRow(String name, boolean unlocked, Set<String> cards, boolean shared)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);
		List<String> owners = shared && unlocked ? collection.ownersOf(cards) : java.util.Collections.emptyList();
		String stateText;
		if (!unlocked)
		{
			stateText = shared ? "Locked" : "Missing";
		}
		else if (!shared)
		{
			stateText = "Owned";
		}
		else if (owners.size() == 1)
		{
			stateText = owners.get(0);
		}
		else if (owners.size() > 1)
		{
			stateText = owners.size() + " members";
		}
		else
		{
			stateText = "Unlocked";
		}
		JLabel state = new JLabel(stateText);
		state.setForeground(unlocked ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		String tooltip = String.join(" / ", cards);
		if (!owners.isEmpty())
		{
			tooltip += " · " + String.join(" · ", collection.ownershipDetails(cards));
		}
		state.setToolTipText(tooltip);
		row.add(state, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static boolean ownsAny(Set<String> owned, Set<String> cards)
	{
		for (String card : cards)
		{
			if (owned.contains(card))
			{
				return true;
			}
		}
		return false;
	}

	private static JPanel body()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	private static JLabel header(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(8, 0, 3, 0));
		return label;
	}

	private static JLabel muted(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
		return label;
	}

	private static String display(String value)
	{
		return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static final class CollectionChoice
	{
		private final String key;
		private final String displayName;
		private final Set<String> cards;

		private CollectionChoice(String key, String displayName, Set<String> cards)
		{
			this.key = key;
			this.displayName = displayName;
			this.cards = cards;
		}

		@Override
		public String toString()
		{
			return displayName + " (" + cards.size() + ")";
		}
	}
}
