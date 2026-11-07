package teafabric.web.gl;

final class MatrixStack {
    private final float[][] stack;
    private int depth;
    int version;

    MatrixStack(int maxDepth) {
        stack = new float[maxDepth][];
        for (int i = 0; i < maxDepth; i++) {
            stack[i] = Mat4.identity();
        }
    }

    float[] top() {
        return stack[depth];
    }

    void push() {
        if (depth + 1 < stack.length) {
            System.arraycopy(stack[depth], 0, stack[depth + 1], 0, 16);
            depth++;
        }
    }

    void pop() {
        if (depth > 0) {
            depth--;
            version++;
        }
    }

    void changed() {
        version++;
    }
}
