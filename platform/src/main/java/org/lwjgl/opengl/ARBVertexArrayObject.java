package org.lwjgl.opengl;

/** GL_ARB_vertex_array_object: the same emulated vertex array objects as GL30C. */
public final class ARBVertexArrayObject {
    private ARBVertexArrayObject() {
    }

    public static int glGenVertexArrays() {
        return GL30C.glGenVertexArrays();
    }

    public static void glBindVertexArray(int array) {
        GL30C.glBindVertexArray(array);
    }

    public static void glDeleteVertexArrays(int array) {
        GL30C.glDeleteVertexArrays(array);
    }
}
