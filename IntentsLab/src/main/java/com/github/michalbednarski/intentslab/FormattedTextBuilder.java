/*
 * IntentsLab - Android app for playing with Intents and Binder IPC
 * Copyright (C) 2014 Michał Bednarski
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.michalbednarski.intentslab;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.*;
import android.view.View;

public class FormattedTextBuilder {
    private SpannableStringBuilder ssb = new SpannableStringBuilder();

    private void appendSpan(CharSequence text, Object style) {
        int baseLen = ssb.length();
        int textLen = text.length();
        ssb.append(text);
        ssb.setSpan(style, baseLen, baseLen + textLen, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public void appendHeader(String text) {
        ssb.append("\n\n");
        appendSpan(text, new StyleSpan(Typeface.BOLD));
    }

    public void appendGlobalHeader(String text) {
        if (ssb.length() != 0) {
            ssb.append("\n");
        }
        appendSpan(text, new RelativeSizeSpan(1.5f));
    }

    public void appendValue(String key, String value) {
        appendValue(key, value, true, ValueSemantic.NONE);
    }

    public void appendValueNoNewLine(String key, String value) {
        appendValue(key, value, false, ValueSemantic.NONE);
    }

    public void appendValuelessKeyContinuingGroup(CharSequence key) {
        ssb.append("\n");
        appendSpan(key, new StyleSpan(Typeface.BOLD));
    }

    public void appendRaw(String text) {
        ssb.append(text);
    }

    public enum ValueSemantic {
        NONE,
        ERROR,
        PERMISSION
    }

    public void appendValue(String key, String value, boolean startGroup, ValueSemantic valueSemantic) {
        final String originalValue = value;
        Object span = null;
        switch (valueSemantic) {
            case ERROR:
                span = new ForegroundColorSpan(Color.RED);
                value = "[" + value + "]";
                break;
            case PERMISSION:
                span = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        Context context = widget.getContext();
                        context.startActivity(
                                new Intent(context, SingleFragmentActivity.class)
                                .putExtra(SingleFragmentActivity.EXTRA_FRAGMENT, PermissionInfoFragment.class.getName())
                                .putExtra(PermissionInfoFragment.ARG_PERMISSION_NAME, originalValue)
                        );
                    }
                };
                break;
        }

        ssb.append(startGroup ? "\n\n" : "\n");
        appendSpan(key + ":", new StyleSpan(Typeface.BOLD));
        ssb.append(" ");
        if (span != null) {
            appendSpan(value, span);
        } else {
            ssb.append(value);
        }

    }

    public void appendText(String text) {
        ssb.append("\n" + text);
    }

    public void appendList(String header, String[] items) {
        ssb.append("\n\n");
        appendSpan(header + ":", new StyleSpan(Typeface.BOLD));
        //Log.v("FTB-list-header", header);

        for (String item : items) {
            //Log.v("FTB-list-item", item);
            ssb.append("\n");
            appendSpan(item, new BulletSpan());
        }
    }

    public void appendColoured(String string, int color) {
        appendSpan(string, new ForegroundColorSpan(color));
    }

    public void appendColouredAndLinked(String string, final int color, final Runnable action) {
        appendSpan(string, new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                action.run();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setColor(color);
                ds.setUnderlineText(true);
            }
        });
    }

    public void appendClickable(String text, ClickableSpan clickableSpan) {
        ssb.append(' ');
        appendSpan(text, clickableSpan);
    }

    public CharSequence getText() {
        return ssb;
    }

    public void appendFormattedText(CharSequence text) {
        ssb.append(text);
    }


}