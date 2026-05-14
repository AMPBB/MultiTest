package pbbadd.opengl.multitest.resizeableview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class SlowLayoutView extends View {
    public SlowLayoutView(Context c) {
        super(c);
    }

    public SlowLayoutView(Context c, AttributeSet a) {
        super(c, a);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 100) {
            double dummy = Math.random() * Math.random();
        }
    }
}
