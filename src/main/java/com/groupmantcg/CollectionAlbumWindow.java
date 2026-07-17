package com.groupmantcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Read-only full-size album for a shared or individual Group TCG collection. */
final class CollectionAlbumWindow extends JFrame
{
	private static final String RARITY_ALL = "All";
	private static final String[] RARITIES =
		{"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic", "Godly"};

	private final CardArtService art;
	private final List<CardVisualCatalog.CardVisual> allCards;
	private final CollectionAlbumGridPanel grid;
	private final JTextField searchField = new JTextField(18);
	private final JComboBox<SortMode> sortCombo = new JComboBox<>(SortMode.values());
	private final JComboBox<String> rarityCombo = new JComboBox<>();
	private final JRadioButton allRadio = new JRadioButton("All cards", true);
	private final JRadioButton obtainedRadio = new JRadioButton("Obtained only");
	private final JRadioButton duplicatesRadio = new JRadioButton("Duplicates only");
	private final JRadioButton missingRadio = new JRadioButton("Missing only");
	private final JCheckBox foilOnlyCheck = new JCheckBox("Foil only");
	private final JButton previousButton = new JButton("< Previous");
	private final JButton nextButton = new JButton("Next >");
	private final JLabel pageLabel = new JLabel(" ");
	private final JLabel summaryLabel = new JLabel(" ", SwingConstants.CENTER);
	private final Timer searchDebounceTimer;
	private final Timer imagePollTimer;
	private final Timer foilAnimationTimer;

	private CollectionAlbumSnapshot snapshot = new CollectionAlbumSnapshot(
		"Shared collection", java.util.Collections.emptySet(), java.util.Collections.emptyMap());
	private List<CardVisualCatalog.CardVisual> filteredCards = java.util.Collections.emptyList();
	private int pageIndex;
	private int pageCount = 1;

	CollectionAlbumWindow(CardVisualCatalog catalog, CardArtService art)
	{
		super("Shared Collection");
		this.art = art;
		this.allCards = new ArrayList<>(catalog.all());
		this.grid = new CollectionAlbumGridPanel(art);

		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setMinimumSize(new Dimension(800, 540));
		setLayout(new BorderLayout(8, 8));
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (String rarity : RARITIES)
		{
			rarityCombo.addItem(rarity);
		}
		rarityCombo.insertItemAt(RARITY_ALL, 0);
		rarityCombo.setSelectedIndex(0);
		styleCombo(rarityCombo);
		styleCombo(sortCombo);

		searchDebounceTimer = new Timer(180, event ->
		{
			pageIndex = 0;
			rebuildModel();
		});
		searchDebounceTimer.setRepeats(false);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			private void changed()
			{
				searchDebounceTimer.restart();
			}

			@Override
			public void insertUpdate(DocumentEvent event)
			{
				changed();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				changed();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				changed();
			}
		});

		ButtonGroup ownershipGroup = new ButtonGroup();
		ownershipGroup.add(allRadio);
		ownershipGroup.add(obtainedRadio);
		ownershipGroup.add(duplicatesRadio);
		ownershipGroup.add(missingRadio);
		styleFilter(allRadio);
		styleFilter(obtainedRadio);
		styleFilter(duplicatesRadio);
		styleFilter(missingRadio);
		styleFilter(foilOnlyCheck);

		sortCombo.addActionListener(event -> resetPageAndRebuild());
		rarityCombo.addActionListener(event -> resetPageAndRebuild());
		allRadio.addActionListener(event -> resetPageAndRebuild());
		obtainedRadio.addActionListener(event -> resetPageAndRebuild());
		duplicatesRadio.addActionListener(event -> resetPageAndRebuild());
		missingRadio.addActionListener(event -> resetPageAndRebuild());
		foilOnlyCheck.addActionListener(event -> resetPageAndRebuild());

		previousButton.addActionListener(event -> changePage(-1));
		nextButton.addActionListener(event -> changePage(1));
		styleButton(previousButton);
		styleButton(nextButton);
		pageLabel.setForeground(Color.WHITE);
		summaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summaryLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel top = buildControls();
		add(top, BorderLayout.NORTH);
		grid.setBorder(BorderFactory.createEmptyBorder(4, 6, 12, 6));
		add(grid, BorderLayout.CENTER);

		java.awt.event.MouseWheelListener pageWheel = this::onMouseWheel;
		top.addMouseWheelListener(pageWheel);
		grid.addMouseWheelListener(pageWheel);

		imagePollTimer = new Timer(250, event ->
		{
			if (isShowing())
			{
				grid.repaint();
			}
		});
		foilAnimationTimer = new Timer(OsrsTcgCardRenderer.FOIL_SPARKLE_FRAME_MS, event ->
		{
			if (isShowing() && grid.hasVisibleFoilCards())
			{
				grid.repaint();
			}
		});
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent event)
			{
				stopRepaintTimers();
			}
		});

		setSize(new Dimension(1120, 740));
		setLocationByPlatform(true);
	}

	private JPanel buildControls()
	{
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.setBorder(new EmptyBorder(6, 8, 2, 8));

		JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
		filterRow.setOpaque(false);
		addLabel(filterRow, "Search:");
		filterRow.add(searchField);
		addLabel(filterRow, "Sort:");
		filterRow.add(sortCombo);
		addLabel(filterRow, "Rarity:");
		filterRow.add(rarityCombo);
		filterRow.add(allRadio);
		filterRow.add(obtainedRadio);
		filterRow.add(duplicatesRadio);
		filterRow.add(missingRadio);
		filterRow.add(Box.createHorizontalStrut(4));
		filterRow.add(foilOnlyCheck);
		filterRow.setAlignmentX(Component.CENTER_ALIGNMENT);
		filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterRow.getPreferredSize().height));
		top.add(filterRow);

		JPanel pageRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 2));
		pageRow.setOpaque(false);
		pageRow.add(previousButton);
		pageRow.add(pageLabel);
		pageRow.add(nextButton);
		pageRow.setAlignmentX(Component.CENTER_ALIGNMENT);
		pageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, pageRow.getPreferredSize().height));
		top.add(pageRow);

		summaryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		top.add(summaryLabel);
		return top;
	}

	void showCollection(CollectionAlbumSnapshot next)
	{
		String previousName = snapshot == null ? "" : snapshot.displayName();
		snapshot = next;
		setTitle(titleFor(next.displayName()));
		if (!previousName.equals(next.displayName()))
		{
			pageIndex = 0;
		}
		rebuildModel();
		imagePollTimer.start();
		foilAnimationTimer.start();
		setVisible(true);
		toFront();
		requestFocus();
	}

	void refreshCollection(CollectionAlbumSnapshot next)
	{
		snapshot = next;
		setTitle(titleFor(next.displayName()));
		rebuildModel();
	}

	void disposeInternal()
	{
		searchDebounceTimer.stop();
		stopRepaintTimers();
		dispose();
	}

	private void stopRepaintTimers()
	{
		imagePollTimer.stop();
		foilAnimationTimer.stop();
	}

	private void resetPageAndRebuild()
	{
		pageIndex = 0;
		rebuildModel();
	}

	private void rebuildModel()
	{
		List<CardVisualCatalog.CardVisual> working = new ArrayList<>(allCards);
		String query = EntityCardCatalog.normalize(searchField.getText());
		if (!query.isEmpty())
		{
			working.removeIf(card -> !EntityCardCatalog.normalize(card.displayName()).contains(query));
		}

		String rarity = (String) rarityCombo.getSelectedItem();
		if (rarity != null && !RARITY_ALL.equals(rarity))
		{
			working.removeIf(card -> !rarity.equals(card.rarityLabel()));
		}

		if (obtainedRadio.isSelected())
		{
			working.removeIf(card -> snapshot.ownershipOf(card.displayName()).totalCopies() == 0);
		}
		else if (duplicatesRadio.isSelected())
		{
			working.removeIf(card -> snapshot.ownershipOf(card.displayName()).totalCopies() <= 1);
		}
		else if (missingRadio.isSelected())
		{
			working.removeIf(card -> snapshot.ownershipOf(card.displayName()).totalCopies() > 0);
		}
		if (foilOnlyCheck.isSelected())
		{
			working.removeIf(card -> snapshot.ownershipOf(card.displayName()).foilCopies() == 0);
		}

		Comparator<CardVisualCatalog.CardVisual> byName = Comparator.comparing(
			CardVisualCatalog.CardVisual::displayName, String.CASE_INSENSITIVE_ORDER);
		SortMode sort = (SortMode) sortCombo.getSelectedItem();
		if (sort == SortMode.NAME_ASC)
		{
			working.sort(byName);
		}
		else if (sort == SortMode.RARITY_DESC)
		{
			working.sort(Comparator.comparingInt(
				(CardVisualCatalog.CardVisual card) -> rarityRank(card.rarityLabel()))
				.reversed().thenComparing(byName));
		}
		else
		{
			working.sort(Comparator.comparingDouble(CardVisualCatalog.CardVisual::score)
				.reversed().thenComparing(byName));
		}

		filteredCards = working;
		pageCount = Math.max(1,
			(filteredCards.size() + CollectionAlbumGridPanel.PAGE_SIZE - 1)
				/ CollectionAlbumGridPanel.PAGE_SIZE);
		pageIndex = Math.max(0, Math.min(pageIndex, pageCount - 1));
		refreshCurrentPage();
		refreshSummary();
	}

	private void refreshCurrentPage()
	{
		int from = pageIndex * CollectionAlbumGridPanel.PAGE_SIZE;
		int to = Math.min(filteredCards.size(), from + CollectionAlbumGridPanel.PAGE_SIZE);
		List<CollectionAlbumGridPanel.Slot> slots = new ArrayList<>();
		List<String> preload = new ArrayList<>();
		for (int i = from; i < to; i++)
		{
			CardVisualCatalog.CardVisual card = filteredCards.get(i);
			slots.add(new CollectionAlbumGridPanel.Slot(card, snapshot.ownershipOf(card.displayName())));
			preload.add(card.displayName());
		}
		grid.setSlots(slots);
		art.preload(preload);

		int first = filteredCards.isEmpty() ? 0 : from + 1;
		NumberFormat numbers = NumberFormat.getIntegerInstance();
		pageLabel.setText("Page " + numbers.format(pageIndex + 1) + " / " + numbers.format(pageCount)
			+ "   (" + numbers.format(first) + "-" + numbers.format(to) + " of "
			+ numbers.format(filteredCards.size()) + ")");
		previousButton.setEnabled(pageIndex > 0);
		nextButton.setEnabled(pageIndex < pageCount - 1);
	}

	private void refreshSummary()
	{
		int unique = 0;
		int copies = 0;
		int foils = 0;
		for (CardVisualCatalog.CardVisual card : allCards)
		{
			CollectionAlbumSnapshot.Ownership owned = snapshot.ownershipOf(card.displayName());
			if (owned.totalCopies() > 0)
			{
				unique++;
				copies += owned.totalCopies();
				foils += owned.foilCopies();
			}
		}
		NumberFormat numbers = NumberFormat.getIntegerInstance();
		summaryLabel.setText(numbers.format(unique) + " / " + numbers.format(allCards.size())
			+ " obtained  -  " + numbers.format(copies) + " total copies  -  "
			+ numbers.format(foils) + " foils");
	}

	private void changePage(int delta)
	{
		int next = Math.max(0, Math.min(pageCount - 1, pageIndex + delta));
		if (next != pageIndex)
		{
			pageIndex = next;
			refreshCurrentPage();
		}
	}

	private void onMouseWheel(MouseWheelEvent event)
	{
		if (pageCount <= 1)
		{
			return;
		}
		int next = Math.max(0, Math.min(pageCount - 1, pageIndex + event.getWheelRotation()));
		if (next != pageIndex)
		{
			pageIndex = next;
			refreshCurrentPage();
		}
		event.consume();
	}

	static String titleFor(String displayName)
	{
		String clean = displayName == null ? "" : displayName.trim();
		if (clean.isEmpty() || "shared collection".equalsIgnoreCase(clean))
		{
			return "Shared Collection";
		}
		return clean + "'s Collection";
	}

	private static int rarityRank(String rarity)
	{
		for (int i = 0; i < RARITIES.length; i++)
		{
			if (RARITIES[i].equals(rarity))
			{
				return i;
			}
		}
		return -1;
	}

	private static void addLabel(JPanel panel, String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		panel.add(label);
	}

	private static void styleCombo(JComboBox<?> combo)
	{
		combo.setForeground(Color.WHITE);
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
	}

	private static void styleFilter(javax.swing.AbstractButton button)
	{
		button.setForeground(Color.WHITE);
		button.setOpaque(false);
	}

	private static void styleButton(JButton button)
	{
		button.setFocusable(false);
		button.setForeground(Color.WHITE);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
	}

	private enum SortMode
	{
		SCORE_DESC("Score (high first)"),
		RARITY_DESC("Rarity (high first)"),
		NAME_ASC("Name (A-Z)");

		private final String label;

		SortMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
