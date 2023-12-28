package tilelogger;

public class Rect {
    short x1,y1,x2,y2;
    
    public Rect() {}
    public Rect(short x1, short y1, short x2, short y2) {
        set(x1,y1,x2,y2);
        normalize();
    }

    public int area() {
        return (x2-x1+1)*(y2-y1+1);
    }

    public void normalize() {
        short x1_ = (short)Math.min(x1, x2);
        short x2_ = (short)Math.max(x1, x2);
        short y1_ = (short)Math.min(y1, y2);
        short y2_ = (short)Math.max(y1, y2);
        set(x1_,y1_,x2_,y2_);
    }

    public void set(short x1, short y1, short x2, short y2) {
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
    }

    public String toString() {
        return String.format("%d %d %d %d", x1, y1, x2, y2);
    }
}
