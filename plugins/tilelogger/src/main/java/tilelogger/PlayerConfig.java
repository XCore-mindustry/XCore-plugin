package tilelogger;

public class PlayerConfig {
    public enum Selection { NONE, FIRST_POINT, SECOND_POINT, READY }
    public int historySize;
    public Selection selection = Selection.NONE;
    public long session;
    public Rect rect = new Rect();
}
