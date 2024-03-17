package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockApplyCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class Trigger {
    public static final long TIME_PER_DAY = 24000;

    public Stats Stats;
    public Diary Diary;
    public long CurrentDay;

    // いずれはInterfaceの配列にでも
    Trigger(Stats s, Diary d) {
        this.Stats = s;
        this.Diary = d;
        this.CurrentDay = -1;
        registerCallback();
    }

    public void registerCallback() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        //ClientPickBlockApplyCallback.EVENT.register((player, result, stack) -> {
        //    onClientPickBlockApply(player, result, stack);
        //    return stack;
        //});
        //ClientPickBlockGatherCallback.EVENT.register((player, result) -> {
        //    onClientPickBlockGather(player, result);
        //    return null;
        //});
        //EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
        //    onEntityTrackingEventsStart(trackedEntity, player);
        //});
        //EntityTrackingEvents.STOP_TRACKING.register((trackedEntity, player) -> {
        //    onEntityTrackingEventsStop(trackedEntity, player);
        //});
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            onAttackBlockCallback(player, world, hand, pos, direction);
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onAttackEntityCallback(player, world, hand, entity, hitResult);
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            onPlayerBlockBreakEvents(world, player, pos, state, entity);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            onUseBlockCallback(player, world, hand, hitResult);
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onUseEntityCallback(player, world, hand, entity, hitResult);
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            onUseItemCallback(player, world, hand);
            return TypedActionResult.pass(ItemStack.EMPTY);
        });
    }

    // TODO 使用したアイテムの形状
    public void onUseItemCallback(PlayerEntity player, World world, Hand hand) {
        ItemStack s = player.getStackInHand(hand);
        if (s != null) {
            this.Stats.onItemUsed( s.getTranslationKey() );
            //AIDiaryClient.LOGGER.info("onUseItemCallback);
        }
    }

    // TODO 「使った」mobの計上
    public void onUseEntityCallback(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        String e = "";
        if (entity != null) {
            e = entity.getDisplayName().getString();
        }
        AIDiaryClient.LOGGER.info("onUseEntityCallback e{}", e);
    }

    // TODO 使ったブロックの計上
    public void onUseBlockCallback(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        String r = "";
        if ((hitResult != null) && (world != null)) {
            r = world.getBlockState(hitResult.getBlockPos()).getBlock().getName().getString();
        }
        AIDiaryClient.LOGGER.info("onUseBlockCallback r{}", r);
    }

    // TODO 壊したブロックの計上
    public void onPlayerBlockBreakEvents(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity entity) {
        String e = "";
        String s = "";
        String p = "";
        if (entity != null) {
            e = entity.getType().toString();
        }
        if (state != null) {
            s = state.getBlock().getName().getString();
        }
        if (pos != null) {
            p = pos.toString();
        }
        AIDiaryClient.LOGGER.info("onPlayerBlockBreakEvents e[{}] s[{}] p[{}]", e, s, p);
    }

    public void onAttackEntityCallback(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (entity != null) {
            //String target = entity.getDisplayName().getString();
            String target = entity.getType().getTranslationKey();
            //String how = player.getStackInHand(hand).getName().getString();
            String how = player.getStackInHand(hand).getTranslationKey();
            AIDiaryClient.LOGGER.info("onAttackEntityCallback target[{}] how[{}]", target, how);
            this.Stats.onClientAttacked(target, how);
        }
    }

    // TODO 攻撃対象の計上
    // TODO 攻撃方法の計上
    public void onAttackBlockCallback(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction
            direction) {
        AIDiaryClient.LOGGER.info("onAttackBlockCallback {}", hand.name());
    }

    // 近辺のmobに反応してそうだが視界の外にも反応してそう
    public void onEntityTrackingEventsStop(Entity trackedEntity, ServerPlayerEntity player) {
        AIDiaryClient.LOGGER.info("onEntityTrackingEventsStop {}", trackedEntity.getDisplayName().getString());
    }

    public void onEntityTrackingEventsStart(Entity trackedEntity, ServerPlayerEntity player) {
        AIDiaryClient.LOGGER.info("onEntityTrackingEventsStart {}", trackedEntity.getDisplayName().getString());
    }

    // よくわからん
    public void onClientPickBlockGather(PlayerEntity player, HitResult result) {
        AIDiaryClient.LOGGER.info("onClientPickBlockGather");
    }

    // よくわからん
    public void onClientPickBlockApply(PlayerEntity player, HitResult result, ItemStack stack) {
        AIDiaryClient.LOGGER.info("onClientPickBlockApply");
    }

    public void onClientTick(MinecraftClient client) {
        if (client == null || client.world == null) {
            return;
        }
        long tod = client.world.getTimeOfDay();
        long days = tod / TIME_PER_DAY;
        if (this.CurrentDay == -1) {
            this.CurrentDay = days;
            return;
        }
        if (days != this.CurrentDay) {
            this.CurrentDay = days;
            this.Diary.onSave(client, this.Stats);
            this.Stats.reset();
        }
        this.Stats.onClientTick(client);
        this.Diary.onClientTick(client);
    }
}
