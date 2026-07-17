package com.groupmantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
	private final JLabel syncStatus = muted("Loading collection...");
	private final JPanel results = body();

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

		add(search);
		add(Box.createVerticalStrut(6));
		add(header("Collection"));
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
		refreshSearch();
	}

	private void refreshSearch()
	{
		results.removeAll();
		String query = EntityCardCatalog.normalize(search.getText());
		if (query.isEmpty())
		{
			results.add(muted("Search for an NPC or item"));
		}
		else
		{
			Set<String> owned = collection.cards();
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
				results.add(resultRow(match.getKey(), unlocked, match.getValue()));
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

	private static JPanel resultRow(String name, boolean unlocked, Set<String> cards)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel, BorderLayout.CENTER);
		JLabel state = new JLabel(unlocked ? "Unlocked" : "Locked");
		state.setForeground(unlocked ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		state.setToolTipText(String.join(" / ", cards));
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
}

