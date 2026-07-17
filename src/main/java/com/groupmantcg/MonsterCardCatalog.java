package com.groupmantcg;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class MonsterCardCatalog extends EntityCardCatalog
{
	@Inject
	MonsterCardCatalog(Gson gson)
	{
		super(gson, "/tracked_monster_names.json");
	}
}

