package com.groupmantcg;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ItemCardCatalog extends EntityCardCatalog
{
	@Inject
	ItemCardCatalog(Gson gson)
	{
		super(gson, "/tracked_item_names.json");
	}
}

