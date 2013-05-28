package com.example.testapp1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;


public class CatchBroadcastDialog implements OnClickListener, OnCancelListener {

    Context mContext;
    TextView mActionTextView;
    private Activity mWrapperActivity = null;
    private AlertDialog.Builder mBuilder;


    CatchBroadcastDialog(Context context) {
        mContext = context;
        mBuilder = new AlertDialog.Builder(mContext);
        View view = ((LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.dialog_catch_broadcast, null);
        mActionTextView = (TextView) view.findViewById(R.id.action);
        mActionTextView.setText(PreferenceManager
                .getDefaultSharedPreferences(mContext)
                .getString("lastcatchbroadcastaction", "")
        );
        mBuilder.setView(view);
        mBuilder.setTitle(R.string.title_activity_catch_broadcast);
        mBuilder.setPositiveButton(R.string.register_receiver, this);
        if (CatchBroadcastService.sIsRunning) {
            mBuilder.setNegativeButton(R.string.unregister_receiver, this);
        }
    }

    private CatchBroadcastDialog setWrapperActivty(Activity wrapperActivty) {
        mWrapperActivity = wrapperActivty;
        mBuilder.setOnCancelListener(this);
        return this;
    }

    void show() {
        mBuilder.show();
    }


    @Override
    public void onCancel(DialogInterface dialog) {
        // Note: this listener is set only if we are running with WrapperActivty
        // Note: this is not DRY, but we cannot use onDismiss because we support older Android versions
        mWrapperActivity.finish();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_NEGATIVE) {
            stopCatcher();
        } else {
            startCatcher();
        }
        if (mWrapperActivity != null) {
            mWrapperActivity.finish();
        }
    }

    void startCatcher() {
        String action = mActionTextView.getText().toString();
        Utils.applyOrCommitPrefs(
                PreferenceManager
                        .getDefaultSharedPreferences(mContext)
                        .edit()
                        .putString("lastcatchbroadcastaction", action)
        );
        Intent intent = new Intent(mContext, CatchBroadcastService.class);
        intent.putExtra("action", action);
        mContext.startService(intent);
    }

    void stopCatcher() {
        mContext.stopService(new Intent(mContext, CatchBroadcastService.class));
    }

    public static class WrapperActivity extends Activity {
        //public WrapperActivity() { super(); }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            new CatchBroadcastDialog(this).setWrapperActivty(this).show();
        }
    }


}