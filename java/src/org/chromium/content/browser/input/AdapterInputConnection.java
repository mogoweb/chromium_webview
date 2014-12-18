// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import com.google.common.annotations.VisibleForTesting;

/**
 * InputConnection is created by ContentView.onCreateInputConnection.
 * It then adapts android's IME to chrome's RenderWidgetHostView using the
 * native ImeAdapterAndroid via the class ImeAdapter.
 */
public class AdapterInputConnection extends BaseInputConnection {
    private static final String TAG = "AdapterInputConnection";
    private static final boolean DEBUG = false;
    /**
     * Selection value should be -1 if not known. See EditorInfo.java for details.
     */
    public static final int INVALID_SELECTION = -1;
    public static final int INVALID_COMPOSITION = -1;

    private final View mInternalView;
    private final ImeAdapter mImeAdapter;
    private final Editable mEditable;

    private boolean mSingleLine;
    private int mNumNestedBatchEdits = 0;

    private int mLastUpdateSelectionStart = INVALID_SELECTION;
    private int mLastUpdateSelectionEnd = INVALID_SELECTION;
    private int mLastUpdateCompositionStart = INVALID_COMPOSITION;
    private int mLastUpdateCompositionEnd = INVALID_COMPOSITION;

    @VisibleForTesting
    AdapterInputConnection(View view, ImeAdapter imeAdapter, Editable editable,
            EditorInfo outAttrs) {
        super(view, true);
        mInternalView = view;
        mImeAdapter = imeAdapter;
        mImeAdapter.setInputConnection(this);
        mEditable = editable;
        // The editable passed in might have been in use by a prior keyboard and could have had
        // prior composition spans set.  To avoid keyboard conflicts, remove all composing spans
        // when taking ownership of an existing Editable.
        removeComposingSpans(mEditable);
        mSingleLine = true;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;

        if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeText) {
            // Normal text field
            outAttrs.inputType |= EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeTextArea ||
                imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeContentEditable) {
            // TextArea or contenteditable.
            outAttrs.inputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
                    | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_NONE;
            mSingleLine = false;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypePassword) {
            // Password
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeSearch) {
            // Search
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_SEARCH;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeUrl) {
            // Url
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_URI;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeEmail) {
            // Email
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeTel) {
            // Telephone
            // Number and telephone do not have both a Tab key and an
            // action in default OSK, so set the action to NEXT
            outAttrs.inputType = InputType.TYPE_CLASS_PHONE;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeNumber) {
            // Number
            outAttrs.inputType = InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_VARIATION_NORMAL
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
        }
        outAttrs.initialSelStart = Selection.getSelectionStart(mEditable);
        outAttrs.initialSelEnd = Selection.getSelectionEnd(mEditable);
        mLastUpdateSelectionStart = Selection.getSelectionStart(mEditable);
        mLastUpdateSelectionEnd = Selection.getSelectionEnd(mEditable);

        Selection.setSelection(mEditable, outAttrs.initialSelStart, outAttrs.initialSelEnd);
        updateSelectionIfRequired();
    }

    /**
     * Updates the AdapterInputConnection's internal representation of the text being edited and
     * its selection and composition properties. The resulting Editable is accessible through the
     * getEditable() method. If the text has not changed, this also calls updateSelection on the
     * InputMethodManager.
     *
     * @param text The String contents of the field being edited.
     * @param selectionStart The character offset of the selection start, or the caret position if
     *                       there is no selection.
     * @param selectionEnd The character offset of the selection end, or the caret position if there
     *                     is no selection.
     * @param compositionStart The character offset of the composition start, or -1 if there is no
     *                         composition.
     * @param compositionEnd The character offset of the composition end, or -1 if there is no
     *                       selection.
     * @param isNonImeChange True when the update was caused by non-IME (e.g. Javascript).
     */
    @VisibleForTesting
    public void updateState(String text, int selectionStart, int selectionEnd, int compositionStart,
            int compositionEnd, boolean isNonImeChange) {
        if (DEBUG) {
            Log.w(TAG, "updateState [" + text + "] [" + selectionStart + " " + selectionEnd + "] ["
                    + compositionStart + " " + compositionEnd + "] [" + isNonImeChange + "]");
        }
        // If this update is from the IME, no further state modification is necessary because the
        // state should have been updated already by the IM framework directly.
        if (!isNonImeChange) return;

        // Non-breaking spaces can cause the IME to get confused. Replace with normal spaces.
        text = text.replace('\u00A0', ' ');

        selectionStart = Math.min(selectionStart, text.length());
        selectionEnd = Math.min(selectionEnd, text.length());
        compositionStart = Math.min(compositionStart, text.length());
        compositionEnd = Math.min(compositionEnd, text.length());

        String prevText = mEditable.toString();
        boolean textUnchanged = prevText.equals(text);

        if (!textUnchanged) {
            mEditable.replace(0, mEditable.length(), text);
        }

        Selection.setSelection(mEditable, selectionStart, selectionEnd);

        if (compositionStart == compositionEnd) {
            removeComposingSpans(mEditable);
        } else {
            super.setComposingRegion(compositionStart, compositionEnd);
        }
        updateSelectionIfRequired();
    }

    /**
     * @return Editable object which contains the state of current focused editable element.
     */
    @Override
    public Editable getEditable() {
        return mEditable;
    }

    /**
     * Sends selection update to the InputMethodManager unless we are currently in a batch edit or
     * if the exact same selection and composition update was sent already.
     */
    private void updateSelectionIfRequired() {
        if (mNumNestedBatchEdits != 0) return;
        int selectionStart = Selection.getSelectionStart(mEditable);
        int selectionEnd = Selection.getSelectionEnd(mEditable);
        int compositionStart = getComposingSpanStart(mEditable);
        int compositionEnd = getComposingSpanEnd(mEditable);
        // Avoid sending update if we sent an exact update already previously.
        if (mLastUpdateSelectionStart == selectionStart &&
                mLastUpdateSelectionEnd == selectionEnd &&
                mLastUpdateCompositionStart == compositionStart &&
                mLastUpdateCompositionEnd == compositionEnd) {
            return;
        }
        if (DEBUG) {
            Log.w(TAG, "updateSelectionIfRequired [" + selectionStart + " " + selectionEnd + "] ["
                    + compositionStart + " " + compositionEnd + "]");
        }
        // updateSelection should be called every time the selection or composition changes
        // if it happens not within a batch edit, or at the end of each top level batch edit.
        getInputMethodManagerWrapper().updateSelection(mInternalView,
                selectionStart, selectionEnd, compositionStart, compositionEnd);
        mLastUpdateSelectionStart = selectionStart;
        mLastUpdateSelectionEnd = selectionEnd;
        mLastUpdateCompositionStart = compositionStart;
        mLastUpdateCompositionEnd = compositionEnd;
    }

    /**
     * @see BaseInputConnection#setComposingText(java.lang.CharSequence, int)
     */
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (DEBUG) Log.w(TAG, "setComposingText [" + text + "] [" + newCursorPosition + "]");
        if (maybePerformEmptyCompositionWorkaround(text)) return true;
        super.setComposingText(text, newCursorPosition);
        updateSelectionIfRequired();
        return mImeAdapter.checkCompositionQueueAndCallNative(text, newCursorPosition, false);
    }

    /**
     * @see BaseInputConnection#commitText(java.lang.CharSequence, int)
     */
    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (DEBUG) Log.w(TAG, "commitText [" + text + "] [" + newCursorPosition + "]");
        if (maybePerformEmptyCompositionWorkaround(text)) return true;
        super.commitText(text, newCursorPosition);
        updateSelectionIfRequired();
        return mImeAdapter.checkCompositionQueueAndCallNative(text, newCursorPosition,
                text.length() > 0);
    }

    /**
     * @see BaseInputConnection#performEditorAction(int)
     */
    @Override
    public boolean performEditorAction(int actionCode) {
        if (DEBUG) Log.w(TAG, "performEditorAction [" + actionCode + "]");
        if (actionCode == EditorInfo.IME_ACTION_NEXT) {
            restartInput();
            // Send TAB key event
            long timeStampMs = SystemClock.uptimeMillis();
            mImeAdapter.sendSyntheticKeyEvent(
                    ImeAdapter.sEventTypeRawKeyDown, timeStampMs, KeyEvent.KEYCODE_TAB, 0);
        } else {
            mImeAdapter.sendKeyEventWithKeyCode(KeyEvent.KEYCODE_ENTER,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
                    | KeyEvent.FLAG_EDITOR_ACTION);
        }
        return true;
    }

    /**
     * @see BaseInputConnection#performContextMenuAction(int)
     */
    @Override
    public boolean performContextMenuAction(int id) {
        if (DEBUG) Log.w(TAG, "performContextMenuAction [" + id + "]");
        switch (id) {
            case android.R.id.selectAll:
                return mImeAdapter.selectAll();
            case android.R.id.cut:
                return mImeAdapter.cut();
            case android.R.id.copy:
                return mImeAdapter.copy();
            case android.R.id.paste:
                return mImeAdapter.paste();
            default:
                return false;
        }
    }

    /**
     * @see BaseInputConnection#getExtractedText(android.view.inputmethod.ExtractedTextRequest,
     *                                           int)
     */
    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (DEBUG) Log.w(TAG, "getExtractedText");
        ExtractedText et = new ExtractedText();
        et.text = mEditable.toString();
        et.partialEndOffset = mEditable.length();
        et.selectionStart = Selection.getSelectionStart(mEditable);
        et.selectionEnd = Selection.getSelectionEnd(mEditable);
        et.flags = mSingleLine ? ExtractedText.FLAG_SINGLE_LINE : 0;
        return et;
    }

    /**
     * @see BaseInputConnection#beginBatchEdit()
     */
    @Override
    public boolean beginBatchEdit() {
        if (DEBUG) Log.w(TAG, "beginBatchEdit [" + (mNumNestedBatchEdits == 0) + "]");
        mNumNestedBatchEdits++;
        return true;
    }

    /**
     * @see BaseInputConnection#endBatchEdit()
     */
    @Override
    public boolean endBatchEdit() {
        if (mNumNestedBatchEdits == 0) return false;
        --mNumNestedBatchEdits;
        if (DEBUG) Log.w(TAG, "endBatchEdit [" + (mNumNestedBatchEdits == 0) + "]");
        if (mNumNestedBatchEdits == 0) updateSelectionIfRequired();
        return mNumNestedBatchEdits != 0;
    }

    /**
     * @see BaseInputConnection#deleteSurroundingText(int, int)
     */
    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (DEBUG) {
            Log.w(TAG, "deleteSurroundingText [" + beforeLength + " " + afterLength + "]");
        }
        int availableBefore = Selection.getSelectionStart(mEditable);
        int availableAfter = mEditable.length() - Selection.getSelectionEnd(mEditable);
        beforeLength = Math.min(beforeLength, availableBefore);
        afterLength = Math.min(afterLength, availableAfter);
        super.deleteSurroundingText(beforeLength, afterLength);
        updateSelectionIfRequired();
        return mImeAdapter.deleteSurroundingText(beforeLength, afterLength);
    }

    /**
     * @see BaseInputConnection#sendKeyEvent(android.view.KeyEvent)
     */
    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (DEBUG) {
            Log.w(TAG, "sendKeyEvent [" + event.getAction() + "] [" + event.getKeyCode() + "]");
        }
        // If this is a key-up, and backspace/del or if the key has a character representation,
        // need to update the underlying Editable (i.e. the local representation of the text
        // being edited).
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                deleteSurroundingText(1, 0);
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL) {
                deleteSurroundingText(0, 1);
                return true;
            } else {
                int unicodeChar = event.getUnicodeChar();
                if (unicodeChar != 0) {
                    int selectionStart = Selection.getSelectionStart(mEditable);
                    int selectionEnd = Selection.getSelectionEnd(mEditable);
                    if (selectionStart > selectionEnd) {
                        int temp = selectionStart;
                        selectionStart = selectionEnd;
                        selectionEnd = temp;
                    }
                    mEditable.replace(selectionStart, selectionEnd,
                            Character.toString((char) unicodeChar));
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // TODO(aurimas): remove this workaround when crbug.com/278584 is fixed.
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                beginBatchEdit();
                finishComposingText();
                mImeAdapter.translateAndSendNativeEvents(event);
                endBatchEdit();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL) {
                return true;
            }
        }
        mImeAdapter.translateAndSendNativeEvents(event);
        return true;
    }

    /**
     * @see BaseInputConnection#finishComposingText()
     */
    @Override
    public boolean finishComposingText() {
        if (DEBUG) Log.w(TAG, "finishComposingText");
        if (getComposingSpanStart(mEditable) == getComposingSpanEnd(mEditable)) {
            return true;
        }

        super.finishComposingText();
        updateSelectionIfRequired();
        mImeAdapter.finishComposingText();

        return true;
    }

    /**
     * @see BaseInputConnection#setSelection(int, int)
     */
    @Override
    public boolean setSelection(int start, int end) {
        if (DEBUG) Log.w(TAG, "setSelection [" + start + " " + end + "]");
        int textLength = mEditable.length();
        if (start < 0 || end < 0 || start > textLength || end > textLength) return true;
        super.setSelection(start, end);
        updateSelectionIfRequired();
        return mImeAdapter.setEditableSelectionOffsets(start, end);
    }

    /**
     * Informs the InputMethodManager and InputMethodSession (i.e. the IME) that the text
     * state is no longer what the IME has and that it needs to be updated.
     */
    void restartInput() {
        if (DEBUG) Log.w(TAG, "restartInput");
        getInputMethodManagerWrapper().restartInput(mInternalView);
        mNumNestedBatchEdits = 0;
    }

    /**
     * @see BaseInputConnection#setComposingRegion(int, int)
     */
    @Override
    public boolean setComposingRegion(int start, int end) {
        if (DEBUG) Log.w(TAG, "setComposingRegion [" + start + " " + end + "]");
        int textLength = mEditable.length();
        int a = Math.min(start, end);
        int b = Math.max(start, end);
        if (a < 0) a = 0;
        if (b < 0) b = 0;
        if (a > textLength) a = textLength;
        if (b > textLength) b = textLength;

        if (a == b) {
            removeComposingSpans(mEditable);
        } else {
            super.setComposingRegion(a, b);
        }
        updateSelectionIfRequired();
        return mImeAdapter.setComposingRegion(a, b);
    }

    boolean isActive() {
        return getInputMethodManagerWrapper().isActive(mInternalView);
    }

    private InputMethodManagerWrapper getInputMethodManagerWrapper() {
        return mImeAdapter.getInputMethodManagerWrapper();
    }

    /**
     * This method works around the issue crbug.com/373934 where Blink does not cancel
     * the composition when we send a commit with the empty text.
     *
     * TODO(aurimas) Remove this once crbug.com/373934 is fixed.
     *
     * @param text Text that software keyboard requested to commit.
     * @return Whether the workaround was performed.
     */
    private boolean maybePerformEmptyCompositionWorkaround(CharSequence text) {
        int selectionStart = Selection.getSelectionStart(mEditable);
        int selectionEnd = Selection.getSelectionEnd(mEditable);
        int compositionStart = getComposingSpanStart(mEditable);
        int compositionEnd = getComposingSpanEnd(mEditable);
        if (TextUtils.isEmpty(text) && (selectionStart == selectionEnd)
                && compositionStart != INVALID_COMPOSITION
                && compositionEnd != INVALID_COMPOSITION) {
            beginBatchEdit();
            finishComposingText();
            int selection = Selection.getSelectionStart(mEditable);
            deleteSurroundingText(selection - compositionStart, selection - compositionEnd);
            endBatchEdit();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    static class ImeState {
        public final String text;
        public final int selectionStart;
        public final int selectionEnd;
        public final int compositionStart;
        public final int compositionEnd;

        public ImeState(String text, int selectionStart, int selectionEnd,
                int compositionStart, int compositionEnd) {
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.compositionStart = compositionStart;
            this.compositionEnd = compositionEnd;
        }
    }

    @VisibleForTesting
    ImeState getImeStateForTesting() {
        String text = mEditable.toString();
        int selectionStart = Selection.getSelectionStart(mEditable);
        int selectionEnd = Selection.getSelectionEnd(mEditable);
        int compositionStart = getComposingSpanStart(mEditable);
        int compositionEnd = getComposingSpanEnd(mEditable);
        return new ImeState(text, selectionStart, selectionEnd, compositionStart, compositionEnd);
    }
}
