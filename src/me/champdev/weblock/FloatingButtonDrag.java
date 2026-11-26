package me.champdev.weblock;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

/** Small draggable overlay control with a saved position and normal click behavior. */
final class FloatingButtonDrag {
    private FloatingButtonDrag() {}

    static void attach(View view, FrameLayout parent, String key) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY, startX, startY;
            boolean moved;
            final int slop = android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop();

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = v.getX();
                        startY = v.getY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!moved && Math.hypot(dx, dy) > slop) moved = true;
                        if (moved) {
                            float maxX = Math.max(0, parent.getWidth() - v.getWidth());
                            float maxY = Math.max(0, parent.getHeight() - v.getHeight());
                            v.setX(Math.max(0, Math.min(maxX, startX + dx)));
                            v.setY(Math.max(0, Math.min(maxY, startY + dy)));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            view.getContext().getSharedPreferences("floating_controls", Context.MODE_PRIVATE)
                                .edit().putFloat(key + "_x", v.getX()).putFloat(key + "_y", v.getY()).apply();
                        } else {
                            v.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return true;
            }
        });
        view.setOnLongClickListener(v -> true);
        view.post(() -> {
            android.content.SharedPreferences p = view.getContext()
                .getSharedPreferences("floating_controls", Context.MODE_PRIVATE);
            if (!p.contains(key + "_x") || !p.contains(key + "_y")) return;
            float x = p.getFloat(key + "_x", view.getX());
            float y = p.getFloat(key + "_y", view.getY());
            view.setX(Math.max(0, Math.min(Math.max(0, parent.getWidth() - view.getWidth()), x)));
            view.setY(Math.max(0, Math.min(Math.max(0, parent.getHeight() - view.getHeight()), y)));
        });
    }
}
