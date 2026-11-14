package org.lwjgl.opengl;

/** What the emulation provides: GL 3.0-style FBOs and blits, nothing vendor-specific. */
public final class GLCapabilities {
    public final boolean OpenGL11 = true;
    public final boolean OpenGL13 = true;
    public final boolean OpenGL14 = true;
    public final boolean OpenGL15 = true;
    public final boolean OpenGL20 = true;
    public final boolean OpenGL21 = true;
    public final boolean OpenGL30 = true;
    public final boolean OpenGL31 = false;
    public final boolean OpenGL32 = false;
    public final boolean OpenGL33 = false;
    public final boolean OpenGL43 = false;
    public final boolean GL_ARB_framebuffer_object = true;
    public final boolean GL_EXT_framebuffer_object = true;
    public final boolean GL_EXT_framebuffer_blit = true;
    public final boolean GL_ARB_debug_output = false;
    public final boolean GL_KHR_debug = false;
    public final boolean GL_NV_fog_distance = false;
    public final boolean GL_ARB_multitexture = true;
    public final boolean GL_ARB_vertex_buffer_object = true;
    public final boolean GL_ARB_shader_objects = true;
    public final boolean GL_ARB_vertex_shader = true;
    public final boolean GL_ARB_fragment_shader = true;
    // Sodium's chunk renderer: vertex array objects (emulated) and buffer copies yes, indirect draws no.
    public final boolean GL_ARB_vertex_array_object = true;
    public final boolean GL_ARB_copy_buffer = true;
    public final boolean GL_ARB_instanced_arrays = false;
    public final boolean GL_ARB_draw_indirect = false;
    public final boolean GL_ARB_multi_draw_indirect = false;

    GLCapabilities() {
    }
}
