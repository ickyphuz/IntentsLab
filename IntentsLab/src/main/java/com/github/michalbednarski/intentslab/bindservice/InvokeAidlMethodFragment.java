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

package com.github.michalbednarski.intentslab.bindservice;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.github.michalbednarski.intentslab.R;
import com.github.michalbednarski.intentslab.Utils;
import com.github.michalbednarski.intentslab.bindservice.manager.AidlInterface;
import com.github.michalbednarski.intentslab.bindservice.manager.BaseServiceFragment;
import com.github.michalbednarski.intentslab.bindservice.manager.BindServiceManager;
import com.github.michalbednarski.intentslab.clipboard.ClipboardService;
import com.github.michalbednarski.intentslab.runas.IRemoteInterface;
import com.github.michalbednarski.intentslab.runas.RunAsManager;
import com.github.michalbednarski.intentslab.sandbox.InvokeMethodResult;
import com.github.michalbednarski.intentslab.sandbox.SandboxedMethod;
import com.github.michalbednarski.intentslab.sandbox.SandboxedObject;
import com.github.michalbednarski.intentslab.valueeditors.framework.EditorLauncher;
import com.github.michalbednarski.intentslab.valueeditors.methodcall.ArgumentsEditorHelper;
import com.github.michalbednarski.intentslab.valueeditors.object.InlineValueEditorsLayout;

/**
 * Created by mb on 03.10.13.
 */
public class InvokeAidlMethodFragment extends BaseServiceFragment implements BindServiceManager.AidlReadyCallback, EditorLauncher.EditorLauncherCallbackDelegate {
    static final String ARG_METHOD_NUMBER = "method-number";
    private static final String STATE_METHOD_ARGUMENTS = "method-arguments";

    private AidlInterface mAidlInterface;
    private int mMethodNumber;
    private SandboxedObject[] mMethodArgumentsToRestore;
    private EditorLauncher mEditorLauncher;

    private InlineValueEditorsLayout mEditorsLayout;
    private ArgumentsEditorHelper mArgumentsEditorHelper;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // Prepare editor launcher
        mEditorLauncher = EditorLauncher.getForFragment(this);

        // Restore method arguments from state
        if (savedInstanceState != null) {
            mMethodArgumentsToRestore = (SandboxedObject[]) Utils.deepCastArray
                    (savedInstanceState.getParcelableArray(STATE_METHOD_ARGUMENTS),
                    SandboxedObject[].class
            );
        }

        // Read arguments and continue to preparing aidl
        Bundle args = getArguments();
        mMethodNumber = args.getInt(ARG_METHOD_NUMBER);
        getServiceHelper().prepareAidlAndRunWhenReady(getActivity(), this);
    }

    @Override
    public EditorLauncher.EditorLauncherCallback getEditorLauncherCallback() {
        return mArgumentsEditorHelper;
    }

    @Override
    public void onAidlReady(AidlInterface anInterface) {
        mAidlInterface = anInterface;
        if (mAidlInterface == null) {
            getActivity().finish();
            return;
        }

        // Prepare method info
        SandboxedMethod sandboxedMethod;
        try {
            sandboxedMethod = mAidlInterface.getMethods()[mMethodNumber];
        } catch (Exception e) { // TODO: remove?
            e.printStackTrace();
            getActivity().finish();
            return;
        }

        // Prepare arguments editor helper
        mArgumentsEditorHelper = new ArgumentsEditorHelper(sandboxedMethod, true);
        // TODO: merge ArgumentEditorHelper and EditorLauncher lifecycles
        mArgumentsEditorHelper.setEditorLauncher(mEditorLauncher);

        // Restore arguments
        if (mMethodArgumentsToRestore != null) {
            mArgumentsEditorHelper.setSandboxedArguments(mMethodArgumentsToRestore);
            mMethodArgumentsToRestore = null;
        }

        // Show value editors if their layout was ready first
        if (mEditorsLayout != null) {
            mArgumentsEditorHelper.fillEditorsLayout(mEditorsLayout);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArray(
                STATE_METHOD_ARGUMENTS,
                mMethodArgumentsToRestore != null ? mMethodArgumentsToRestore : // Not fully restored
                        mArgumentsEditorHelper != null ? mArgumentsEditorHelper.getSandboxedArguments() : null
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mEditorsLayout = new InlineValueEditorsLayout(getActivity());
        if (mArgumentsEditorHelper != null) {
            mArgumentsEditorHelper.fillEditorsLayout(mEditorsLayout);
        }
        return mEditorsLayout;
    }

    @Override
    public void onDestroyView() {
        mEditorsLayout = null;
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.aidl_method, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.execute:
                invokeAidlMethod();
                return true;
        }
        return false;
    }

    private void invokeAidlMethod() {
        try {
            InvokeMethodResult result;
            final IRemoteInterface runAs = RunAsManager.getSelectedRemoteInterface();
            if (runAs != null) {
                result = mAidlInterface.invokeMethodUsingBinder(runAs.createOneShotProxyBinder(getServiceHelper().getBinderIfAvailable()), mMethodNumber, mArgumentsEditorHelper.getSandboxedArguments());
            } else {
                result = mAidlInterface.invokeMethod(mMethodNumber, mArgumentsEditorHelper.getSandboxedArguments());
            }
            if (result.exception == null) { // True if there weren't error
                if (!"null".equals(result.returnValueAsString)) {
                    ResultDialog resultDialog = new ResultDialog();
                    Bundle args = new Bundle();
                    args.putString(ResultDialog.ARG_RESULT_AS_STRING, result.returnValueAsString);
                    args.putParcelable(ResultDialog.ARG_RESULT, result.sandboxedReturnValue);
                    resultDialog.setArguments(args);
                    resultDialog.show(getFragmentManager(), "ResultOf" + getTag());
                } else {
                    Toast.makeText(getActivity(), result.returnValueAsString, Toast.LENGTH_LONG).show();
                }
                return;
            } else {
                Toast.makeText(getActivity(), result.exception, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            // Fall through
        }
        Toast.makeText(getActivity(), "Something went wrong...", Toast.LENGTH_SHORT).show(); // Should never happen
    }

    public static class ResultDialog extends DialogFragment {
        public static final String ARG_RESULT = "invokeAidl.ResultDialog.TheResult";
        public static final String ARG_RESULT_AS_STRING = "invokeAidl.ResultDialog.TheResultAsString";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final SandboxedObject result = getArguments().getParcelable(ARG_RESULT);

            final String string = getArguments().getString(ARG_RESULT_AS_STRING);
            return new AlertDialog.Builder(getActivity())
                    .setMessage(string)
                    .setPositiveButton(getString(R.string.edit_or_add_to_clipboard), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ClipboardService.saveSandboxedObject(string, result);
                        }
                    })
                    .setNegativeButton(getString(R.string.dismiss), null)
                    .create();
        }
    }
}
