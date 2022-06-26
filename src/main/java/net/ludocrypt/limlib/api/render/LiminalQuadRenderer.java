package net.ludocrypt.limlib.api.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.lwjgl.system.MemoryStack;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.math.Vector4f;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public abstract class LiminalQuadRenderer {

	public List<Runnable> renderQueue = Lists.newArrayList();

	public abstract void renderQuad(BakedQuad quad, BufferBuilder bufferBuilder, Matrix4f matrix, Camera camera, World world, MatrixStack matrices, List<Pair<BakedQuad, Optional<Direction>>> quads);

	public void renderQuads(List<Pair<BakedQuad, Optional<Direction>>> quads, World world, BlockPos pos, BlockState state, MatrixStack matrices, Camera camera) {
		for (Pair<BakedQuad, Optional<Direction>> quadPair : quads) {
			BakedQuad quad = quadPair.getFirst();

			Matrix4f matrix = matrices.peek().getPositionMatrix().copy();
			matrix.loadIdentity();
			matrix.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(180));
			matrix.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(camera.getYaw()));
			matrix.multiply(Vec3f.NEGATIVE_X.getDegreesQuaternion(camera.getPitch()));
			matrix.multiply(matrices.peek().getPositionMatrix().copy());

			RenderSystem.depthMask(true);
			RenderSystem.enableBlend();
			RenderSystem.enableDepthTest();
			RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.polygonOffset(this.renderBehind() ? 3.0F : -3.0F, this.renderBehind() ? 3.0F : -3.0F);
			RenderSystem.enablePolygonOffset();
			BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
			bufferBuilder.begin(drawMode(), vertexFormat());

			if (quadPair.getSecond().isPresent()) {
				if (Block.shouldDrawSide(state, world, pos, quadPair.getSecond().get(), pos.offset(quadPair.getSecond().get()))) {
					this.renderQuad(quad, bufferBuilder, matrix, camera, world, matrices, quads);
				}
			} else {
				this.renderQuad(quad, bufferBuilder, matrix, camera, world, matrices, quads);
			}

			BufferRenderer.drawWithShader(bufferBuilder.end());
			RenderSystem.polygonOffset(0.0F, 0.0F);
			RenderSystem.disablePolygonOffset();
			RenderSystem.disableBlend();
		}
	}

	public void renderItemQuads(List<Pair<BakedQuad, Optional<Direction>>> quads, World world, ItemStack stack, MatrixStack matrices, Camera camera) {
		Matrix4f matrix = new MatrixStack().peek().getPositionMatrix().copy();

		// Stationize
		matrix.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(180));
		matrix.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(camera.getYaw()));
		matrix.multiply(Vec3f.NEGATIVE_X.getDegreesQuaternion(camera.getPitch()));

		matrix.multiply(matrices.peek().getPositionMatrix().copy());

		for (Pair<BakedQuad, Optional<Direction>> quadPair : quads) {
			BakedQuad quad = quadPair.getFirst();

			RenderSystem.depthMask(true);
			RenderSystem.enableBlend();
			RenderSystem.enableDepthTest();
			RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.polygonOffset(this.renderBehind() ? 3.0F : -3.0F, this.renderBehind() ? 3.0F : -3.0F);
			RenderSystem.enablePolygonOffset();
			BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
			bufferBuilder.begin(drawMode(), vertexFormat());

			this.renderQuad(quad, bufferBuilder, matrix, camera, world, matrices, quads);

			BufferRenderer.drawWithShader(bufferBuilder.end());
			RenderSystem.polygonOffset(0.0F, 0.0F);
			RenderSystem.disablePolygonOffset();
			RenderSystem.disableBlend();
		}
	}

	public static void quad(Consumer<Vec3f> consumer, Matrix4f matrix4f, BakedQuad quad) {
		int[] js = quad.getVertexData();
		int j = js.length / 8;
		MemoryStack memoryStack = MemoryStack.stackPush();
		try {
			ByteBuffer byteBuffer = memoryStack.malloc(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte());
			IntBuffer intBuffer = byteBuffer.asIntBuffer();

			for (int k = 0; k < j; ++k) {
				intBuffer.clear();
				intBuffer.put(js, k * 8, 8);

				Vector4f vector4f = new Vector4f(byteBuffer.getFloat(0), byteBuffer.getFloat(4), byteBuffer.getFloat(8), 1.0F);
				vector4f.transform(matrix4f);
				consumer.accept(new Vec3f(vector4f.getX(), vector4f.getY(), vector4f.getZ()));
			}
		} catch (Throwable var33) {
			if (memoryStack != null) {
				try {
					memoryStack.close();
				} catch (Throwable var32) {
					var33.addSuppressed(var32);
				}
			}

			throw var33;
		}
		if (memoryStack != null) {
			memoryStack.close();
		}
	}

	public abstract boolean renderBehind();

	public abstract VertexFormat vertexFormat();

	public abstract DrawMode drawMode();

}