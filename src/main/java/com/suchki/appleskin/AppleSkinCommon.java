package com.suchki.appleskin;

import net.fabricmc.api.ModInitializer;
import com.suchki.appleskin.network.SyncHandler;

public class AppleSkinCommon implements ModInitializer
{
	@Override
	public void onInitialize()
	{
		SyncHandler.init();
	}
}
