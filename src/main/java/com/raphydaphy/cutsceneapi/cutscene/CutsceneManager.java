package com.raphydaphy.cutsceneapi.cutscene;

import com.raphydaphy.crochet.data.PlayerData;
import com.raphydaphy.crochet.network.PacketHandler;
import com.raphydaphy.cutsceneapi.CutsceneAPI;
import com.raphydaphy.cutsceneapi.fakeworld.CutsceneWorld;
import com.raphydaphy.cutsceneapi.mixin.client.ClientPlayNetworkHandlerHooks;
import com.raphydaphy.cutsceneapi.mixin.client.MinecraftClientHooks;
import com.raphydaphy.cutsceneapi.network.CutsceneFinishPacket;
import com.raphydaphy.cutsceneapi.network.CutsceneStartPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class CutsceneManager
{
	private static ICutscene currentCutscene;

	public static boolean hideHud(PlayerEntity player)
	{
		return isActive(player) && currentCutscene != null;
	}

	public static boolean isActive(PlayerEntity player)
	{
		return player != null && PlayerData.get(player).getBoolean(CutsceneAPI.WATCHING_CUTSCENE_KEY);
	}

	@Environment(EnvType.CLIENT)
	public static void updateLook()
	{
	}

	public static boolean showFakeWorld()
	{
		return false;
	}

	@Environment(EnvType.CLIENT)
	public static void renderHud()
	{
		if (currentCutscene != null)
		{
			currentCutscene.render();
		}
	}

	@Environment(EnvType.CLIENT)
	public static void startClient(Identifier cutscene)
	{
		currentCutscene = CutsceneRegistry.get(cutscene);
	}

	private static ClientWorld realWorld;

	@Environment(EnvType.CLIENT)
	public static void startFakeWorld(boolean copy)
	{
		MinecraftClient client = MinecraftClient.getInstance();
		realWorld = client.world;
		CutsceneWorld cutsceneWorld = new CutsceneWorld(client, client.world, copy);
		client.player.setWorld(cutsceneWorld);
		client.world = cutsceneWorld;
		((MinecraftClientHooks) client).setCutsceneWorld(cutsceneWorld);
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		if (handler != null)
		{
			((ClientPlayNetworkHandlerHooks) handler).setCutsceneWorld(cutsceneWorld);
		}

		BlockPos playerPos = client.player.getBlockPos();

		if (!copy)
		{
			for (int x = -2; x <= 2; x++)
			{
				for (int y = -1; y >= -3; y--)
				{
					for (int z = -2; z <= 2; z++)
					{
						cutsceneWorld.setBlockState(playerPos.add(x, y, z), y == -1 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState());
					}
				}
			}
		}
		cutsceneWorld.addPlayer(client.player);

		client.inGameHud.setTitles("§5Welcome!§r", "", 20, 50, 20);
	}

	@Environment(EnvType.CLIENT)
	public static void stopFakeWorld()
	{
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world instanceof CutsceneWorld)
		{
			ClientWorld realWorld = ((CutsceneWorld) client.world).realWorld;
			if (realWorld != null)
			{
				client.player.setWorld(realWorld);
				client.world = realWorld;
				((MinecraftClientHooks) client).setCutsceneWorld(realWorld);
				ClientPlayNetworkHandler handler = client.getNetworkHandler();
				if (handler != null)
				{
					((ClientPlayNetworkHandlerHooks) handler).setCutsceneWorld(realWorld);
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static void finishClient()
	{
		if (currentCutscene != null)
		{
			currentCutscene = null;
		}
		PacketHandler.sendToServer(new CutsceneFinishPacket());
	}

	@Environment(EnvType.CLIENT)
	public static void updateClient()
	{
		MinecraftClient client = MinecraftClient.getInstance();
		if (isActive(client.player))
		{
			if (currentCutscene == null)
				currentCutscene = CutsceneRegistry.get(Identifier.create(PlayerData.get(client.player).getString(CutsceneAPI.CUTSCENE_ID_KEY)));
			if (currentCutscene != null) currentCutscene.tick();
		}
	}

	public static void startServer(ServerPlayerEntity player, Identifier id)
	{
		player.stopRiding();
		PlayerData.get(player).putBoolean(CutsceneAPI.WATCHING_CUTSCENE_KEY, true);
		PlayerData.get(player).putString(CutsceneAPI.CUTSCENE_ID_KEY, id.toString());
		PlayerData.markDirty(player);
		PacketHandler.sendToClient(new CutsceneStartPacket(id), player);
	}

	public static void finishServer(PlayerEntity player)
	{
		PlayerData.get(player).putBoolean(CutsceneAPI.WATCHING_CUTSCENE_KEY, false);
		PlayerData.markDirty(player);
	}
}
