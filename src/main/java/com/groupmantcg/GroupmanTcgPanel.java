package com.groupmantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

class GroupmanTcgPanel extends PluginPanel
{
	private static final int MAX_RESULTS = 20;
	private final SharedCollectionService collection;
	private final MonsterCardCatalog monsters;
	private final ItemCardCatalog items;
	private final IconTextField search = new IconTextField();
	private final JComboBox<CollectionChoice> collectionSelector = new JComboBox<>();
	private final JLabel syncStatus = muted("Loading collection...");
	private final JPanel results = body();
	private boolean updatingSelector;

	GroupmanTcgPanel(SharedCollectionService collection, MonsterCardCatalog monsters, ItemCardCatalog items)
	{
		this.collection = collection;
		this.monsters = monsters;
		this.items = items;
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
		add(header("Group status"));
		add(syncStatus);
		add(header("Card lookup"));
		add(results);
		refresh();
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
			String members = status.isInParty()
				? status.getSyncedMembers() + "/" + status.getRosterMembers() + " members seen"
				: "offline cache";
			syncStatus.setText(status.getGroupName() + " · " + status.getCards() + " cards · " + members);
			syncStatus.setForeground(status.isInParty()
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
		}
		syncStatus.setToolTipText(status.getDetail());
		refreshCollectionChoices();
		refreshSearch();
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
			results.add(muted("Search cards; hover owners for pull details."));
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
