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

package com.github.michalbednarski.intentslab.runas;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.github.michalbednarski.intentslab.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Entry point for remote process of RunAs mode
 */
public class RemoteEntryPoint {

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void main(String[] args) {
        // First arg is name of component of RunAsInitReceiver
        Intent initBroadcastIntent = new Intent();
        initBroadcastIntent.setComponent(ComponentName.unflattenFromString(args[0]));

        // Prepare exitLock to keep alive while UI process is running
        final Object exitLock = new Object();
        synchronized (exitLock) {

            // Get remote interface request via broadcast
            ActivityManagerWrapper.get().sendOrderedBroadcast(initBroadcastIntent, new RealIIntentReceiver() {
                @Override
                public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered) {
                    // Debugging
                    System.out.print("extras=");
                    System.out.println(extras);

                    // Get remote request interface
                    final IBinder requestBinder = (IBinder) extras.get(RunAsInitReceiver.RESULT_EXTRA_REMOTE_INTERFACE_REQUEST);

                    // Link to exit
                    try {
                        requestBinder.linkToDeath(new IBinder.DeathRecipient() {
                            @Override
                            public void binderDied() {
                                synchronized (exitLock) {
                                    System.out.println("=> UI disconnected");
                                    exitLock.notify();
                                }
                            }
                        }, 0);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    // Post remote interface to UI process
                    IRemoteInterfaceRequest remoteInterfaceRequest = IRemoteInterfaceRequest.Stub.asInterface(requestBinder);
                    try {
                        remoteInterfaceRequest.setRemoteInterface(new RemoteInterfaceImpl());
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }

                    // Tell user that we're ready
                    System.out.println("=> RunAs remote ready");
                }
            });

            // Wait until UI process exits
            try {
                exitLock.wait();
            } catch (InterruptedException e) {
                System.out.println("=> exitLock interrupted");
            }
            System.out.println("=> Exiting");
        }
    }

    public static File getScriptFile(Context context) {
        return new File(context.getFilesDir().getParentFile(), "rr");
    }

    @SuppressLint("CommitPrefEdits")
    public static void ensureInstalled(Context context) {
        int checksum = context.getPackageCodePath().hashCode();
        final SharedPreferences preferences = getDefaultSharedPreferences(context);
        if (preferences.getInt("runAsModeInstallationChecksum", 0) != checksum) {
            try {
                install(context);
                Utils.applyOrCommitPrefs(
                    preferences
                        .edit()
                        .putInt("runAsModeInstallationChecksum", checksum)
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void install(Context context) throws IOException {
        File script = getScriptFile(context);
        FileWriter writer = new FileWriter(script);
        writer.write(
            "#!/system/bin/sh\n" +
            "unset LD_LIBRARY_PATH\n" + // For Termux
            "export CLASSPATH=" + context.getPackageCodePath() + "\n" +
            "exec /system/bin/app_process /system/bin " + RemoteEntryPoint.class.getName() + " " + new ComponentName(context, RunAsInitReceiver.class).flattenToShortString() + " \"$@\"\n"
        );
        writer.close();
        Runtime.getRuntime().exec(new String[] {
            "chmod",
            "0755",
            script.getAbsolutePath()
        });
    }


}
