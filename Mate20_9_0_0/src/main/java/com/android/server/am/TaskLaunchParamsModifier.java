package com.android.server.am;

import android.app.ActivityOptions;
import android.content.pm.ActivityInfo.WindowLayout;
import android.graphics.Rect;
import android.util.Slog;
import com.android.internal.annotations.VisibleForTesting;
import java.util.ArrayList;

class TaskLaunchParamsModifier implements LaunchParamsModifier {
    private static final boolean ALLOW_RESTART = true;
    private static final int BOUNDS_CONFLICT_MIN_DISTANCE = 4;
    private static final int MARGIN_SIZE_DENOMINATOR = 4;
    private static final int MINIMAL_STEP = 1;
    private static final int SHIFT_POLICY_DIAGONAL_DOWN = 1;
    private static final int SHIFT_POLICY_HORIZONTAL_LEFT = 3;
    private static final int SHIFT_POLICY_HORIZONTAL_RIGHT = 2;
    private static final int STEP_DENOMINATOR = 16;
    private static final String TAG = "ActivityManager";
    private static final int WINDOW_SIZE_DENOMINATOR = 2;
    private final Rect mAvailableRect = new Rect();
    private final Rect mTmpOriginal = new Rect();
    private final Rect mTmpProposal = new Rect();

    TaskLaunchParamsModifier() {
    }

    public int onCalculate(TaskRecord task, WindowLayout layout, ActivityRecord activity, ActivityRecord source, ActivityOptions options, LaunchParams currentParams, LaunchParams outParams) {
        WindowLayout windowLayout = layout;
        if (task == null || task.getStack() == null || !task.inFreeformWindowingMode()) {
            LaunchParams launchParams = outParams;
            return 0;
        }
        ArrayList<TaskRecord> tasks = task.getStack().getAllTasks();
        this.mAvailableRect.set(task.getParent().getBounds());
        Rect resultBounds = outParams.mBounds;
        if (windowLayout == null) {
            positionCenter(tasks, this.mAvailableRect, getFreeformWidth(this.mAvailableRect), getFreeformHeight(this.mAvailableRect), resultBounds);
            return 2;
        }
        int width = getFinalWidth(windowLayout, this.mAvailableRect);
        int height = getFinalHeight(windowLayout, this.mAvailableRect);
        int verticalGravity = windowLayout.gravity & 112;
        int horizontalGravity = windowLayout.gravity & 7;
        if (verticalGravity != 48) {
            int horizontalGravity2 = horizontalGravity;
            if (verticalGravity != 80) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Received unsupported gravity: ");
                stringBuilder.append(windowLayout.gravity);
                stringBuilder.append(", positioning in the center instead.");
                Slog.w("ActivityManager", stringBuilder.toString());
                positionCenter(tasks, this.mAvailableRect, width, height, resultBounds);
            } else if (horizontalGravity2 == 5) {
                positionBottomRight(tasks, this.mAvailableRect, width, height, resultBounds);
            } else {
                positionBottomLeft(tasks, this.mAvailableRect, width, height, resultBounds);
            }
        } else if (horizontalGravity == 5) {
            positionTopRight(tasks, this.mAvailableRect, width, height, resultBounds);
        } else {
            positionTopLeft(tasks, this.mAvailableRect, width, height, resultBounds);
        }
        return 2;
    }

    @VisibleForTesting
    static int getFreeformStartLeft(Rect bounds) {
        return bounds.left + (bounds.width() / 4);
    }

    @VisibleForTesting
    static int getFreeformStartTop(Rect bounds) {
        return bounds.top + (bounds.height() / 4);
    }

    @VisibleForTesting
    static int getFreeformWidth(Rect bounds) {
        return bounds.width() / 2;
    }

    @VisibleForTesting
    static int getFreeformHeight(Rect bounds) {
        return bounds.height() / 2;
    }

    @VisibleForTesting
    static int getHorizontalStep(Rect bounds) {
        return Math.max(bounds.width() / 16, 1);
    }

    @VisibleForTesting
    static int getVerticalStep(Rect bounds) {
        return Math.max(bounds.height() / 16, 1);
    }

    private int getFinalWidth(WindowLayout windowLayout, Rect availableRect) {
        int width = getFreeformWidth(availableRect);
        if (windowLayout.width > 0) {
            width = windowLayout.width;
        }
        if (windowLayout.widthFraction > 0.0f) {
            return (int) (((float) availableRect.width()) * windowLayout.widthFraction);
        }
        return width;
    }

    private int getFinalHeight(WindowLayout windowLayout, Rect availableRect) {
        int height = getFreeformHeight(availableRect);
        if (windowLayout.height > 0) {
            height = windowLayout.height;
        }
        if (windowLayout.heightFraction > 0.0f) {
            return (int) (((float) availableRect.height()) * windowLayout.heightFraction);
        }
        return height;
    }

    private void positionBottomLeft(ArrayList<TaskRecord> tasks, Rect availableRect, int width, int height, Rect result) {
        Rect rect = availableRect;
        this.mTmpProposal.set(rect.left, rect.bottom - height, rect.left + width, rect.bottom);
        position(tasks, rect, this.mTmpProposal, false, 2, result);
    }

    private void positionBottomRight(ArrayList<TaskRecord> tasks, Rect availableRect, int width, int height, Rect result) {
        Rect rect = availableRect;
        this.mTmpProposal.set(rect.right - width, rect.bottom - height, rect.right, rect.bottom);
        position(tasks, rect, this.mTmpProposal, false, 3, result);
    }

    private void positionTopLeft(ArrayList<TaskRecord> tasks, Rect availableRect, int width, int height, Rect result) {
        Rect rect = availableRect;
        this.mTmpProposal.set(rect.left, rect.top, rect.left + width, rect.top + height);
        position(tasks, rect, this.mTmpProposal, false, 2, result);
    }

    private void positionTopRight(ArrayList<TaskRecord> tasks, Rect availableRect, int width, int height, Rect result) {
        Rect rect = availableRect;
        this.mTmpProposal.set(rect.right - width, rect.top, rect.right, rect.top + height);
        position(tasks, rect, this.mTmpProposal, false, 3, result);
    }

    private void positionCenter(ArrayList<TaskRecord> tasks, Rect availableRect, int width, int height, Rect result) {
        int defaultFreeformLeft = getFreeformStartLeft(availableRect);
        int defaultFreeformTop = getFreeformStartTop(availableRect);
        this.mTmpProposal.set(defaultFreeformLeft, defaultFreeformTop, defaultFreeformLeft + width, defaultFreeformTop + height);
        position(tasks, availableRect, this.mTmpProposal, true, 1, result);
    }

    private void position(ArrayList<TaskRecord> tasks, Rect availableRect, Rect proposal, boolean allowRestart, int shiftPolicy, Rect result) {
        this.mTmpOriginal.set(proposal);
        boolean restarted = false;
        while (boundsConflict(proposal, tasks)) {
            shiftStartingPoint(proposal, availableRect, shiftPolicy);
            if (shiftedTooFar(proposal, availableRect, shiftPolicy)) {
                if (!allowRestart) {
                    proposal.set(this.mTmpOriginal);
                    break;
                } else {
                    proposal.set(availableRect.left, availableRect.top, availableRect.left + proposal.width(), availableRect.top + proposal.height());
                    restarted = true;
                }
            }
            if (restarted && (proposal.left > getFreeformStartLeft(availableRect) || proposal.top > getFreeformStartTop(availableRect))) {
                proposal.set(this.mTmpOriginal);
                break;
            }
        }
        result.set(proposal);
    }

    private boolean shiftedTooFar(Rect start, Rect availableRect, int shiftPolicy) {
        boolean z = false;
        switch (shiftPolicy) {
            case 2:
                if (start.right > availableRect.right) {
                    z = true;
                }
                return z;
            case 3:
                if (start.left < availableRect.left) {
                    z = true;
                }
                return z;
            default:
                if (start.right > availableRect.right || start.bottom > availableRect.bottom) {
                    z = true;
                }
                return z;
        }
    }

    private void shiftStartingPoint(Rect posposal, Rect availableRect, int shiftPolicy) {
        int defaultFreeformStepHorizontal = getHorizontalStep(availableRect);
        int defaultFreeformStepVertical = getVerticalStep(availableRect);
        switch (shiftPolicy) {
            case 2:
                posposal.offset(defaultFreeformStepHorizontal, 0);
                return;
            case 3:
                posposal.offset(-defaultFreeformStepHorizontal, 0);
                return;
            default:
                posposal.offset(defaultFreeformStepHorizontal, defaultFreeformStepVertical);
                return;
        }
    }

    private static boolean boundsConflict(Rect proposal, ArrayList<TaskRecord> tasks) {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            TaskRecord task = (TaskRecord) tasks.get(i);
            if (!(task.mActivities.isEmpty() || task.matchParentBounds())) {
                Rect bounds = task.getOverrideBounds();
                if (closeLeftTopCorner(proposal, bounds) || closeRightTopCorner(proposal, bounds) || closeLeftBottomCorner(proposal, bounds) || closeRightBottomCorner(proposal, bounds)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final boolean closeLeftTopCorner(Rect first, Rect second) {
        return Math.abs(first.left - second.left) < 4 && Math.abs(first.top - second.top) < 4;
    }

    private static final boolean closeRightTopCorner(Rect first, Rect second) {
        return Math.abs(first.right - second.right) < 4 && Math.abs(first.top - second.top) < 4;
    }

    private static final boolean closeLeftBottomCorner(Rect first, Rect second) {
        return Math.abs(first.left - second.left) < 4 && Math.abs(first.bottom - second.bottom) < 4;
    }

    private static final boolean closeRightBottomCorner(Rect first, Rect second) {
        return Math.abs(first.right - second.right) < 4 && Math.abs(first.bottom - second.bottom) < 4;
    }
}
