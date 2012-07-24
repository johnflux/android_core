/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.view.visualization;

import com.google.common.base.Preconditions;

import android.graphics.Bitmap;
import android.opengl.GLUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.rosjava_geometry.Transform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * Renders a texture.
 * 
 * @author moesenle@google.com (Lorenz Moesenlechner)
 * @author damonkohler@google.com (Damon Kohler)
 */
public class TextureBitmap implements OpenGlDrawable {

  /**
   * The maximum height of a texture.
   */
  private final static int TEXTURE_HEIGHT = 1024;

  /**
   * The maximum width of a texture.
   */
  private final static int TEXTURE_STRIDE = 1024;
  private final static int VERTEX_BUFFER_STRIDE = 3;

  private final int[] pixels;
  private final FloatBuffer vertexBuffer;
  private final FloatBuffer textureBuffer;
  private final Bitmap bitmap;

  private int[] handle;
  private Transform origin;
  private double scaledWidth;
  private double scaledHeight;
  private boolean reload;

  public TextureBitmap() {
    pixels = new int[TEXTURE_HEIGHT * TEXTURE_STRIDE];
    float vertexCoordinates[] = {
        // Triangle 1
        0.0f, 0.0f, 0.0f, // Bottom left
        1.0f, 0.0f, 0.0f, // Bottom right
        0.0f, 1.0f, 0.0f, // Top left
        // Triangle 2
        1.0f, 0.0f, 0.0f, // Bottom right
        0.0f, 1.0f, 0.0f, // Top left
        1.0f, 1.0f, 0.0f, // Top right
    };
    {
      ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCoordinates.length * 4);
      buffer.order(ByteOrder.nativeOrder());
      vertexBuffer = buffer.asFloatBuffer();
      vertexBuffer.put(vertexCoordinates);
      vertexBuffer.position(0);
    }
    float textureCoordinates[] = {
        // Triangle 1
        0.0f, 0.0f, // Bottom left
        1.0f, 0.0f, // Bottom right
        0.0f, 1.0f, // Top left
        // Triangle 2
        1.0f, 0.0f, // Bottom right
        0.0f, 1.0f, // Top left
        1.0f, 1.0f, // Top right
    };
    {
      ByteBuffer buffer = ByteBuffer.allocateDirect(textureCoordinates.length * 4);
      buffer.order(ByteOrder.nativeOrder());
      textureBuffer = buffer.asFloatBuffer();
      textureBuffer.put(textureCoordinates);
      textureBuffer.position(0);
    }
    bitmap = Bitmap.createBitmap(TEXTURE_STRIDE, TEXTURE_HEIGHT, Bitmap.Config.ARGB_8888);
    reload = true;
  }

  public void updateFromPixelArray(int[] pixels, int stride, float resolution, Transform origin,
      int fillColor) {
    Preconditions.checkArgument(pixels.length % stride == 0);
    int height = pixels.length / stride;
    for (int y = 0; y < TEXTURE_HEIGHT; y++) {
      for (int x = 0; x < TEXTURE_STRIDE; x++) {
        // If the pixel is within the bounds of the specified pixel array then
        // we copy the specified value. Otherwise, we use the specified fill
        // color.
        int sourceIndex = y * stride + x;
        int targetIndex = y * TEXTURE_STRIDE + x;
        if (x < stride && y < height) {
          this.pixels[targetIndex] = pixels[sourceIndex];
        } else {
          this.pixels[targetIndex] = fillColor;
        }
      }
    }
    update(origin, stride, resolution, fillColor);
  }

  public void updateFromPixelBuffer(ChannelBuffer pixels, int stride, float resolution,
      Transform origin, int fillColor) {
    Preconditions.checkNotNull(pixels);
    for (int y = 0, i = 0; y < TEXTURE_HEIGHT; y++) {
      for (int x = 0; x < TEXTURE_STRIDE; x++, i++) {
        // If the pixel is within the bounds of the specified pixel array then
        // we copy the specified value. Otherwise, we use the specified fill
        // color.
        if (x < stride && pixels.readable()) {
          this.pixels[i] = pixels.readInt();
        } else {
          this.pixels[i] = fillColor;
        }
      }
    }
    update(origin, stride, resolution, fillColor);
  }

  private void update(Transform origin, int stride, float resolution, int fillColor) {
    this.origin = origin;
    scaledWidth = TEXTURE_STRIDE * resolution;
    scaledHeight = TEXTURE_HEIGHT * resolution;
    bitmap.setPixels(pixels, 0, TEXTURE_STRIDE, 0, 0, TEXTURE_STRIDE, TEXTURE_HEIGHT);
    reload = true;
  }

  private void bind(GL10 gl) {
    if (handle == null) {
      handle = new int[1];
      gl.glGenTextures(1, handle, 0);
    }
    if (reload) {
      gl.glBindTexture(GL10.GL_TEXTURE_2D, handle[0]);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);
      GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
      reload = false;
    }
    gl.glBindTexture(GL10.GL_TEXTURE_2D, handle[0]);
  }

  @Override
  public void draw(GL10 gl) {
    gl.glEnable(GL10.GL_TEXTURE_2D);
    bind(gl);
    gl.glPushMatrix();
    OpenGlTransform.apply(gl, origin);
    gl.glScalef((float) scaledWidth, (float) scaledHeight, 1.0f);
    gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
    gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer);
    gl.glDrawArrays(GL10.GL_TRIANGLES, 0, VERTEX_BUFFER_STRIDE);
    gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    gl.glDisable(GL10.GL_TEXTURE_2D);
    gl.glPopMatrix();
  }
}