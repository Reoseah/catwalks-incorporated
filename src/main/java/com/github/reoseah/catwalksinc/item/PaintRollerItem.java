package com.github.reoseah.catwalksinc.item;

import com.github.reoseah.catwalksinc.CIncItems;
import com.github.reoseah.catwalksinc.block.Paintable;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.*;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class PaintRollerItem extends Item implements CustomDurabilityItem {
	public static final Map<DyeColor, PaintRollerItem> INSTANCES = new EnumMap<>(DyeColor.class);

	protected final DyeColor color;

	public static PaintRollerItem byColor(DyeColor color) {
		return INSTANCES.get(color);
	}

	public PaintRollerItem(DyeColor color, Item.Settings settings) {
		super(settings);
		this.color = color;
		INSTANCES.put(color, this);
	}

	@Override
	public String getTranslationKey() {
		return CIncItems.PAINT_ROLLER.getTranslationKey();
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(new TranslatableText("misc.catwalksinc." + this.color.asString()).formatted(Formatting.GRAY));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if (block == Blocks.WATER_CAULDRON && state.get(LeveledCauldronBlock.LEVEL) > 0
				&& context.getPlayer() != null) {
			world.setBlockState(pos, state.with(LeveledCauldronBlock.LEVEL, state.get(LeveledCauldronBlock.LEVEL) - 1));
			context.getPlayer().setStackInHand(context.getHand(), new ItemStack(CIncItems.PAINT_ROLLER));

			return ActionResult.SUCCESS;
		}

		if (block instanceof Paintable) {
			Paintable paintable = (Paintable) block;

			if (paintable.canPaintBlock(this.color, state, world, pos)) {
				int amount = paintable.getPaintConsumption(this.color, state, world, pos);
				if (amount <= this.getMaxPaint() - this.getDamage(context.getStack())) {
					paintable.paintBlock(this.color, state, world, pos);

					if (context.getPlayer() != null) {
						this.damage(context.getStack(), amount, context.getPlayer(), player -> {
							player.sendToolBreakStatus(context.getHand());
							player.setStackInHand(context.getHand(), new ItemStack(CIncItems.PAINT_ROLLER));
						});
					}

					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.FAIL;
		}

		if (!state.hasBlockEntity()) {
			Item blockItem = state.getBlock().asItem();
			if (blockItem instanceof BlockItem //
					&& ((BlockItem) blockItem).getBlock() == block) {
				CraftingInventory inventory = new CraftingInventory(new ScreenHandler(null, -1) {
					@Override
					public boolean canUse(PlayerEntity player) {
						return false;
					}
				}, 3, 3);
				for (int i = 0; i < 3; i++) {
					for (int j = 0; j < 3; j++) {
						if (i == 1 && j == 1) {
							inventory.setStack(i * 3 + j, DyeItem.byColor(this.color).getDefaultStack());
						} else {
							inventory.setStack(i * 3 + j, blockItem.getDefaultStack());
						}
					}
				}
				ItemStack result = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, inventory, world)
						.map(recipe -> recipe.craft(inventory)).orElse(null);
				if (result != null && !result.hasNbt() && result.getItem() instanceof BlockItem resultItem) {
					Block resultBlock = ((BlockItem) resultItem).getBlock();
					BlockState resultState = resultBlock.getDefaultState();
					for (Property<?> property : resultState.getProperties()) {
						if (!state.contains(property)) {
							return ActionResult.FAIL;
						}
						resultState = resultState.with((Property) property, state.get(property));
					}

					world.setBlockState(pos, resultState);
					this.damage(context.getStack(), 1, context.getPlayer(), player -> {
						player.sendToolBreakStatus(context.getHand());
						player.setStackInHand(context.getHand(), new ItemStack(CIncItems.PAINT_ROLLER));
					});
					return ActionResult.SUCCESS;
				}
			}
		}
		return ActionResult.FAIL;
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	public DyeColor getColor() {
		return this.color;
	}

	public int getMaxPaint() {
		return 32;
	}

	public int getDamage(ItemStack stack) {
		return stack.getNbt() == null ? 0 : stack.getNbt().getInt("DyeUsed");
	}

	public void setDamage(ItemStack stack, int damage) {
		stack.getOrCreateNbt().putInt("DyeUsed", Math.max(0, damage));
	}

	public boolean damage(ItemStack stack, int amount, Random random, @Nullable ServerPlayerEntity player) {
		if (player != null && amount != 0) {
			Criteria.ITEM_DURABILITY_CHANGED.trigger(player, stack, this.getDamage(stack) + amount);
		}

		int i = this.getDamage(stack) + amount;
		this.setDamage(stack, i);
		return i >= this.getMaxPaint();
	}

	public <T extends LivingEntity> void damage(ItemStack stack, int amount, T entity, Consumer<T> breakCallback) {
		if (!entity.world.isClient
				&& (!(entity instanceof PlayerEntity) || !((PlayerEntity) entity).getAbilities().creativeMode)) {
			if (this.damage(stack, amount, entity.getRandom(),
					entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null)) {
				breakCallback.accept(entity);
				Item item = stack.getItem();
				stack.decrement(1);
				if (entity instanceof PlayerEntity) {
					((PlayerEntity) entity).incrementStat(Stats.BROKEN.getOrCreateStat(item));
				}

				this.setDamage(stack, 0);

			}
		}
	}

	@Override
	public double getDurabilityBarProgress(ItemStack stack) {
		return (double) this.getDamage(stack) / (double) this.getMaxPaint();
	}

	@Override
	public boolean hasDurabilityBar(ItemStack stack) {
		return this.getDamage(stack) > 0;
	}
}
