package org.lwjgl.glfw;

/** Video mode of the page (the canvas' screen); a plain value object instead of a native struct. */
public class GLFWVidMode {
    final int width;
    final int height;
    final int refresh;

    GLFWVidMode(int width, int height, int refresh) {
        this.width = width;
        this.height = height;
        this.refresh = refresh;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int redBits() {
        return 8;
    }

    public int greenBits() {
        return 8;
    }

    public int blueBits() {
        return 8;
    }

    public int refreshRate() {
        return refresh;
    }

    public static class Buffer extends org.lwjgl.system.CustomBuffer<Buffer> {
        private final GLFWVidMode[] modes;

        Buffer(GLFWVidMode[] modes) {
            super(0, modes.length);
            this.modes = modes;
        }

        @Override
        protected Buffer self() {
            return this;
        }

        public GLFWVidMode get(int index) {
            return modes[index];
        }

        public GLFWVidMode get() {
            return modes[position++];
        }

        public int width() {
            return modes[position].width;
        }

        public int height() {
            return modes[position].height;
        }

        public int redBits() {
            return 8;
        }

        public int greenBits() {
            return 8;
        }

        public int blueBits() {
            return 8;
        }

        public int refreshRate() {
            return modes[position].refresh;
        }
    }
}
