package tilelogger;

public class Rect {
    public int x1, y1, x2, y2;
    public Rect() {}
    public Rect(int x1, int y1, int x2, int y2) {
        set(x1, y1, x2, y2);
        normalize();
    }
    public Rect copy() {
        return new Rect(x1, y1, x2, y2);
    }
    public long area() {
        return (long) (x2 - x1 + 1) * (y2 - y1 + 1);
    }
    public void normalize() {
        int lowX = Math.min(x1, x2), lowY = Math.min(y1, y2);
        x2 = Math.max(x1, x2);
        y2 = Math.max(y1, y2);
        x1 = lowX;
        y1 = lowY;
    }
    public void set(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    public boolean contains(int x, int y) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }
    @Override
    public String toString() {
        return x1 + " " + y1 + " " + x2 + " " + y2;
    }
}
