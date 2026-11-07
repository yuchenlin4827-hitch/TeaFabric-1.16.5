package org.lwjgl.glfw;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

/** Window icon images. Browsers use the page favicon, so the pixels are only recorded. */
public class GLFWImage {
    int width;
    int height;
    ByteBuffer pixels;

    public static GLFWImage malloc() {
        return new GLFWImage();
    }

    public static Buffer malloc(int capacity) {
        return new Buffer(capacity);
    }

    public static Buffer mallocStack(int capacity, MemoryStack stack) {
        return new Buffer(capacity);
    }

    public static Buffer mallocStack(int capacity) {
        return new Buffer(capacity);
    }

    public GLFWImage set(int width, int height, ByteBuffer pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        return this;
    }

    public void free() {
    }

    public static class Buffer extends org.lwjgl.system.CustomBuffer<Buffer> {
        final GLFWImage[] images;

        Buffer(int capacity) {
            super(0, capacity);
            images = new GLFWImage[capacity];
            for (int i = 0; i < capacity; i++) {
                images[i] = new GLFWImage();
            }
        }

        @Override
        protected Buffer self() {
            return this;
        }

        public Buffer width(int value) {
            images[position].width = value;
            return this;
        }

        public Buffer height(int value) {
            images[position].height = value;
            return this;
        }

        public Buffer pixels(ByteBuffer value) {
            images[position].pixels = value;
            return this;
        }

        public GLFWImage get(int index) {
            return images[index];
        }
    }
}
