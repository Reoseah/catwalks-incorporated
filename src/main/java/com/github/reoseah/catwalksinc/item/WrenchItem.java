package com.github.reoseah.catwalksinc.item;

import com.github.reoseah.catwalksinc.CIncSoundEvents;
import com.github.reoseah.catwalksinc.block.Wrenchable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WrenchItem extends ToolItem {
	private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

	public WrenchItem(Item.Settings settings) {
		super(ToolMaterials.IRON, settings);

		ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> attributeBuilder = ImmutableMultimap
				.builder();
		attributeBuilder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(
				ATTACK_DAMAGE_MODIFIER_ID, "Tool modifier", 2, EntityAttributeModifier.Operation.ADDITION));
		attributeBuilder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(
				ATTACK_SPEED_MODIFIER_ID, "Tool modifier", 0, EntityAttributeModifier.Operation.ADDITION));
		this.attributeModifiers = attributeBuilder.build();
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);
		PlayerEntity player = context.getPlayer();
		Hand hand = context.getHand();
		if (state.getBlock()instanceof Wrenchable wrenchable //
				&& wrenchable.useWrench(state, world, pos, context.getSide(), player, hand, context.getHitPos())) {
			if (player != null) {
				context.getStack().damage(1, player, p -> p.sendToolBreakStatus(hand));
			}
			world.playSound(null, player.getX(), player.getY(), player.getZ(), CIncSoundEvents.WRENCH_USE,
					SoundCategory.PLAYERS, 1.0f, 1.0f / (world.getRandom().nextFloat() * 0.4f + 1.2f));

			return ActionResult.SUCCESS;
		}
		return super.useOnBlock(context);
	}

	@Override
	public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND) {
			return this.attributeModifiers;
		}
		return super.getAttributeModifiers(slot);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.damage(2, attacker, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
		return true;
	}
}
