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

import android.annotation.TargetApi;
import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.github.michalbednarski.intentslab.editor.IntentEditorActivity;
import com.github.michalbednarski.intentslab.editor.IntentEditorConstants;

import java.util.ArrayList;

public class ReceiveBroadcastService extends Service {
    private static final String TAG = "ReceiveBroadcast";
    private static final int SERVICE_NOTIFICATION_ID = 1;
    private static final int RESULT_NOTIFICATION_ID = 2;

	static boolean sIsRunning = false;
	private MyBroadcastReceiver mReceiver = null;
	private boolean mGotBroadcast = false;
    private static class ReceivedBroadcast {
        long time;
        Intent intent;
        String description = "";
    };
    private static ArrayList<ReceivedBroadcast> sReceivedBroadcasts = null;

    public static void startReceiving(Context context, IntentFilter[] filters, boolean multiple) {
        if (!multiple) {
            for (IntentFilter filter : filters) {
                Intent stickyBroadcastIntent = context.registerReceiver(null, filter);
                if (stickyBroadcastIntent != null) {
                    context.startActivity(
                            new Intent(context, IntentEditorActivity.class)
                                    .putExtra(IntentEditorActivity.EXTRA_INTENT, stickyBroadcastIntent)
                                    .putExtra(IntentEditorActivity.EXTRA_COMPONENT_TYPE, IntentEditorConstants.BROADCAST)
                    );
                    Toast.makeText(context, R.string.received_sticky_broadcast, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        context.startService(
                new Intent(context, ReceiveBroadcastService.class)
                        .putExtra("intentFilters", filters)
                        .putExtra("multiple", multiple)
        );
    }
    public static void startReceiving(Context context, String action, boolean multiple) {
        IntentFilter[] filters = { new IntentFilter(action) };
        startReceiving(context, filters, multiple);
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Flag us as running
		sIsRunning = true;

		// Prepare receiver and unregister old one if exist
		if (mReceiver != null) {
			// We were already started, clear old receiver
			unregisterReceiver(mReceiver);
		} else {
			mReceiver = new MyBroadcastReceiver();
		}

        if (intent.getBooleanExtra("multiple", false)) {
            sReceivedBroadcasts = new ArrayList<ReceivedBroadcast>();
        } else {
            sReceivedBroadcasts = null;
        }

		// Get IntentFilter and register receiver
        String action = "";
        Parcelable[] filters = intent.getParcelableArrayExtra("intentFilters");
        if (filters == null || filters.length == 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        for (Parcelable uncastedFilter : filters) {
            IntentFilter filter = (IntentFilter) uncastedFilter;
            registerReceiver(mReceiver, filter);
            if (filters.length == 1) {
                if (filter.countActions() == 1) {
                    action = filter.getAction(0);
                }
            }
        }


		// Show notification
        if (sReceivedBroadcasts != null) {
            showListeningMultipleNotification();
        } else {
		    showWaitingNotification(action);
        }
		return START_NOT_STICKY;
	}

	private boolean isAutoEditEnabled() {
		return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autoeditbroadcast", false);
	}

	private NotificationManager getNotificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	void showWaitingNotification(String action) {
		mGotBroadcast = false;

		Intent requeryAction = new Intent(this, ReceiveBroadcastDialog.WrapperActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				requeryAction, 0);

		String title = getResources().getString(R.string.waiting_for_broadcast);

        final Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_action_send)
                .setOngoing(true)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(action)
                .setContentIntent(contentIntent)
                .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);
	}

	void showCaughtNotification(Intent receivedBroadcast) {
		mGotBroadcast = true;

		Intent runEditor = new Intent(this, IntentEditorActivity.class);
		runEditor.putExtra(IntentEditorActivity.EXTRA_INTENT, receivedBroadcast);
		runEditor.putExtra(IntentEditorActivity.EXTRA_COMPONENT_TYPE, IntentEditorConstants.BROADCAST);
		runEditor.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                runEditor,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

		if (isAutoEditEnabled()) {
			startActivity(runEditor);
		} else {
			final String title = getResources().getString(R.string.got_broadcast);
            final String action = receivedBroadcast.getAction();

            final Notification notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_action_send)
                    .setAutoCancel(true)
                    .setTicker(title)
                    .setContentTitle(title)
                    .setContentText(action)
                    .setContentIntent(contentIntent)
                    .build();

			getNotificationManager().notify(RESULT_NOTIFICATION_ID, notification);
		}
	}

    void showListeningMultipleNotification() {
		mGotBroadcast = true;

		Intent viewBroadcastsListIntent = new Intent(this, BroadcastsListActivity.class);
		viewBroadcastsListIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
					viewBroadcastsListIntent, 0);

        String title = getString(R.string.listening_for_multiple_broadcasts);
        int receivedSoFar = sReceivedBroadcasts.size();
        String message =
            receivedSoFar == 0 ?
                getString(R.string.nothing_received_so_far) :
                getResources().getQuantityString(R.plurals.n_broadcasts_received_so_far, receivedSoFar, receivedSoFar);

        final Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_action_send)
                .setOngoing(true)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(contentIntent)
                .build();

        startForeground(SERVICE_NOTIFICATION_ID, notification);
	}

	@Override
	public void onDestroy() {
		sIsRunning = false;
		unregisterReceiver(mReceiver);
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private class MyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
            if (sReceivedBroadcasts != null) {
                // Running in receive multiple mode, add broadcast to list
                ReceivedBroadcast receivedBroadcast = new ReceivedBroadcast();
                receivedBroadcast.time = System.currentTimeMillis();
                receivedBroadcast.intent = intent;

                // Find last broadcast with same action
                Intent previousBroadcastIntent = null;
                ReceivedBroadcast previousBroadcast = null;
                for (int i = sReceivedBroadcasts.size() - 1; i >= 0; i--) {
                    previousBroadcast = sReceivedBroadcasts.get(i);
                    previousBroadcastIntent = previousBroadcast.intent;
                    if (previousBroadcastIntent.getAction().equals(intent.getAction())) {
                        break;
                    } else if (i == 0) {
                        previousBroadcastIntent = null;
                    }
                }

                // Generate description
                String description = "";
                if (previousBroadcastIntent == null) {
                    if (isInitialStickyBroadcast()) {
                        description = context.getString(R.string.initial_sticky);
                    }
                } else {

                    if (intent.getData() != null) {
                        description = intent.getDataString();
                    } else {
                        description = context.getString(R.string.s_after_previous_broadcast, (receivedBroadcast.time - previousBroadcast.time) / 1000);
                    }

                    if (previousBroadcastIntent.getFlags() != intent.getFlags()) {
                        description += "\n" + context.getString(R.string.flags_changed);
                    }

                    // Extras changes
                    Bundle extras = intent.getExtras();
                    Bundle previousExtras = previousBroadcastIntent.getExtras();
                    if (extras == null || extras.size() == 0) {
                        receivedBroadcast.description += "\n" + context.getString(R.string.no_extras);
                    } else if (previousExtras != null && previousExtras.size() != 0) {
                        // Both have extras, compare them
                        for (String extraName : extras.keySet()) {
                            Object oldValue = previousExtras.get(extraName);
                            Object newValue = extras.get(extraName);
                            if (oldValue == null) {
                                description += "\n" + getString(R.string.added_extra, extraName);
                            } else if (Utils.hasOverriddenEqualsMethod(newValue) && !newValue.equals(oldValue)) {
                                description += "\n" + extraName + ": " + oldValue + " -> " + newValue;
                            }
                            if (description.length() > 500) {
                                description += "\n[...]";
                                break;
                            }
                        }
                    }
                }
                receivedBroadcast.description = description;

                sReceivedBroadcasts.add(receivedBroadcast);
                for (BroadcastsListActivity listActivity : sListActivities) {
                    listActivity.mAdapter.notifyDataSetChanged();
                }
                showListeningMultipleNotification();
            } else {
                showCaughtNotification(intent);
                stopSelf(); // Stop my service, unregister receiver
			}
		}
	}

    // Activity for viewing multiple broadcasts
    private static ArrayList<BroadcastsListActivity> sListActivities = new ArrayList<BroadcastsListActivity>();
    public static class BroadcastsListActivity extends ListActivity implements AdapterView.OnItemClickListener {
        ArrayAdapter<ReceivedBroadcast> mAdapter;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (sReceivedBroadcasts == null) {
                Log.e(TAG, "Unexpected start of BroadcastsListActivity");
                finish();
                return;
            }

            mAdapter = new ArrayAdapter<ReceivedBroadcast>(this, 0, sReceivedBroadcasts) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
                    }
                    ReceivedBroadcast receivedBroadcast = mAdapter.getItem(position);
                    ((TextView) convertView.findViewById(android.R.id.text1)).setText(Utils.afterLastDot(receivedBroadcast.intent.getAction()));
                    ((TextView) convertView.findViewById(android.R.id.text2)).setText(receivedBroadcast.description);
                    return convertView;
                }
            };
            setListAdapter(mAdapter);
            getListView().setOnItemClickListener(this);
            sListActivities.add(this);
        }

        @Override
        protected void onDestroy() {
            sListActivities.remove(this);
            super.onDestroy();
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            startActivity(
                    new Intent(this, IntentEditorActivity.class)
                            .putExtra(IntentEditorActivity.EXTRA_COMPONENT_TYPE, IntentEditorConstants.BROADCAST)
                            .putExtra(IntentEditorActivity.EXTRA_INTENT, mAdapter.getItem(position).intent)
            );
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            getMenuInflater().inflate(R.menu.received_broadcasts, menu);
            return true;
        }

        @Override
        public boolean onPrepareOptionsMenu(Menu menu) {
            menu.findItem(R.id.stop_listening_for_broadcasts).setVisible(sIsRunning).setEnabled(sIsRunning);
            return true;
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == R.id.stop_listening_for_broadcasts) {
                stopService(new Intent(this, ReceiveBroadcastService.class));
                sIsRunning = false;
                try {
                    invalidateOptionsMenu();
                } catch (NoSuchMethodError ignored) {}
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
