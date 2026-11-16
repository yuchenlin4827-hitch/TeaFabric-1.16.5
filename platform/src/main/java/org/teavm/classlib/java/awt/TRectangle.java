package org.teavm.classlib.java.awt;

import org.teavm.classlib.java.awt.geom.TRectangle2D;

/** java.awt.Rectangle: an integer rectangle (used by WTHIT's tooltip layout). */
public class TRectangle extends TRectangle2D {
    public int x;
    public int y;
    public int width;
    public int height;

    public TRectangle() {
    }

    public TRectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public TRectangle(int width, int height) {
        this(0, 0, width, height);
    }

    public TRectangle(TRectangle r) {
        this(r.x, r.y, r.width, r.height);
    }

    public TRectangle(TDimension d) {
        this(0, 0, d.width, d.height);
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getWidth() {
        return width;
    }

    @Override
    public double getHeight() {
        return height;
    }

    /** As in the JDK: the smallest integer rectangle that contains the given one. */
    @Override
    public void setRect(double x, double y, double w, double h) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        setBounds(x0, y0, (int) Math.ceil(x + w) - x0, (int) Math.ceil(y + h) - y0);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setBounds(TRectangle r) {
        setBounds(r.x, r.y, r.width, r.height);
    }

    public TRectangle getBounds() {
        return new TRectangle(x, y, width, height);
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public TDimension getSize() {
        return new TDimension(width, height);
    }

    public void translate(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public void grow(int h, int v) {
        x -= h;
        y -= v;
        width += 2 * h;
        height += 2 * v;
    }

    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    public boolean intersects(TRectangle r) {
        return r.width > 0 && r.height > 0 && width > 0 && height > 0
                && r.x < x + width && r.x + r.width > x && r.y < y + height && r.y + r.height > y;
    }

    public TRectangle union(TRectangle r) {
        int x0 = Math.min(x, r.x);
        int y0 = Math.min(y, r.y);
        return new TRectangle(x0, y0, Math.max(x + width, r.x + r.width) - x0,
                Math.max(y + height, r.y + r.height) - y0);
    }

    @Override
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TRectangle)) {
            return false;
        }
        TRectangle r = (TRectangle) o;
        return r.x == x && r.y == y && r.width == width && r.height == height;
    }

    @Override
    public int hashCode() {
        return ((x * 31 + y) * 31 + width) * 31 + height;
    }

    @Override
    public String toString() {
        return "java.awt.Rectangle[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]";
    }
}
