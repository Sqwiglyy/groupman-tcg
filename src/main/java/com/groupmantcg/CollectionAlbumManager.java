package com.groupmantcg;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;

/** Owns the single reusable full collection window. */
@Singleton
final class CollectionAlbumManager
{
	private final SharedCollectionService collections;
	private final CardVisualCatalog catalog;
	private final CardArtService art;

	private volatile CollectionAlbumWindow window;
	private String activeCollectionKey = "";
	private String activeDisplayName = "Shared collection";

	@Inject
	CollectionAlbumManager(SharedCollectionService collections, CardVisualCatalog catalog,
		CardArtService art)
	{
		this.collections = collections;
		this.catalog = catalog;
		this.art = art;
	}

	void show(String collectionKey, String displayName)
	{
		String key = collectionKey == null ? "" : collectionKey;
		String name = displayName == null ? "Shared collection" : displayName;
		SwingUtilities.invokeLater(() ->
		{
			activeCollectionKey = key;
			activeDisplayName = name;
			if (window == null || !window.isDisplayable())
			{
				window = new CollectionAlbumWindow(catalog, art);
			}
			window.showCollection(collections.collectionAlbum(key, name));
		});
	}

	void refreshIfVisible()
	{
		SwingUtilities.invokeLater(() ->
		{
			CollectionAlbumWindow current = window;
			if (current != null && current.isShowing())
			{
				current.refreshCollection(collections.collectionAlbum(
					activeCollectionKey, activeDisplayName));
			}
		});
	}

	void dispose()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window != null)
			{
				window.disposeInternal();
				window = null;
			}
		});
	}
}
