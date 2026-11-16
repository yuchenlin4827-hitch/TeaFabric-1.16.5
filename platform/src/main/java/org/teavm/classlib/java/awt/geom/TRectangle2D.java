package org.teavm.classlib.java.awt.geom;

/** The part of java.awt.geom.Rectangle2D that java.awt.Rectangle needs (WTHIT's tooltip layout). */
public abstract class TRectangle2D {
    protected TRectangle2D() {
    }

    public abstract double getX();

    public abstract double getY();

    public abstract double getWidth();

    public abstract double getHeight();

    public abstract void setRect(double x, double y, double w, double h);

    public void setRect(TRectangle2D r) {
        setRect(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    public double getMinX() {
        return getX();
    }

    public double getMinY() {
        return getY();
    }

    public double getMaxX() {
        return getX() + getWidth();
    }

    public double getMaxY() {
        return getY() + getHeight();
    }

    public double getCenterX() {
        return getX() + getWidth() / 2;
    }

    public double getCenterY() {
        return getY() + getHeight() / 2;
    }

    public boolean isEmpty() {
        return getWidth() <= 0 || getHeight() <= 0;
    }

    public boolean contains(double x, double y) {
        double x0 = getX();
        double y0 = getY();
        return x >= x0 && y >= y0 && x < x0 + getWidth() && y < y0 + getHeight();
    }
}
