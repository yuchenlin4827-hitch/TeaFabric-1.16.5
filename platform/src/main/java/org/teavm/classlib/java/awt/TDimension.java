package org.teavm.classlib.java.awt;

/** Replaces TeaVM's, which has only the (int, int) constructor; WTHIT also uses the empty one. */
public class TDimension {
    public int width;
    public int height;

    public TDimension() {
    }

    public TDimension(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public TDimension(TDimension d) {
        this(d.width, d.height);
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setSize(double width, double height) {
        this.width = (int) Math.ceil(width);
        this.height = (int) Math.ceil(height);
    }

    public void setSize(TDimension d) {
        setSize(d.width, d.height);
    }

    public TDimension getSize() {
        return new TDimension(width, height);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TDimension && ((TDimension) o).width == width && ((TDimension) o).height == height;
    }

    @Override
    public int hashCode() {
        return width * 31 + height;
    }

    @Override
    public String toString() {
        return "java.awt.Dimension[width=" + width + ",height=" + height + "]";
    }
}
