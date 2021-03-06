package com.github.michalbednarski.intentslab.appinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.github.michalbednarski.intentslab.BuildConfig;

import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredObject;
import org.jdeferred.impl.DeferredObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around PackageManager that performs caching and additional information loading
 */
public class MyPackageManagerImpl implements MyPackageManager {
    private static final String TAG = "MyPackageManagerImpl";

    public static final int STANDARD_FLAGS = PackageManager.GET_ACTIVITIES |
            PackageManager.GET_RECEIVERS |
            PackageManager.GET_SERVICES |
            PackageManager.GET_PROVIDERS |
            PackageManager.GET_PERMISSIONS |
            PackageManager.GET_DISABLED_COMPONENTS |
            PackageManager.GET_URI_PERMISSION_PATTERNS |
            PackageManager.GET_META_DATA;

    private static MyPackageManagerImpl sInstance;

    public static MyPackageManager getInstance(Context context) {
        if (BuildConfig.DEBUG) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new AssertionError("getInstance must be called on main thread");
            }
            if (context == null) {
                throw new AssertionError("context must not be null");
            }
        }
        if (sInstance == null) {
            sInstance = new MyPackageManagerImpl(context.getApplicationContext());
        }
        return sInstance;
    }

    private Context mContext;
    private PackageManager mPm;

    private Handler mWorkerHandler;

    final Object mLock = new Object();

    /**
     * Scanned and not stale packages
     * Guarded by {@link #mLock}
     */
    Map<String, MyPackageInfoImpl> mPackages = new ArrayMap<>();

    /**
     * True if we can just return mPackages.values()
     * in {@link #getPackages(boolean)} when intent filters are NOT requested
     * Guarded by {@link #mLock}
     */
    boolean mLoadedAllPackages;

    /**
     * True if we can just return mPackages.values()
     * in {@link #getPackages(boolean)} when intent filters are requested
     */
    boolean mLoadedAllPackagesIncludingIntentFilters;


    /**
     * Scanned permissions
     * Guarded by {@link #mLock}
     */
    Map<String, MyPermissionInfoImpl> mPermissions = new ArrayMap<>();

    private MyPackageManagerImpl(Context context) {
        // 'context' is application context
        mContext = context;
        mPm = context.getPackageManager();

        // Start worker thread
        HandlerThread workerThread = new HandlerThread("MyPackageManager");
        workerThread.start();
        mWorkerHandler = new Handler(workerThread.getLooper()) {
            @Override
            public void dispatchMessage(Message msg) {
                super.dispatchMessage(msg);
                Binder.flushPendingCommands();
            }
        };

        // Register receiver for updates
        PackagesChangedReceiver receiver = new PackagesChangedReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.registerReceiver(receiver, filter);
    }


    private void loadAllInstalledPackagesInfoIfNeeded() {
        // Check if we have to do update
        synchronized (mLock) {
            if (mLoadedAllPackages) {
                return;
            }
        }

        // Load all packages
        // We just load package names and not components now because
        // some packages (notably Google Play Services) are too big
        List<PackageInfo> allPackages = mPm.getInstalledPackages(0);

        for (PackageInfo pack : allPackages) {
            loadPackageInfo(pack.packageName);
        }

        mLoadedAllPackages = true;
    }

    private void fillIntentFiltersForPackage(MyPackageInfoImpl myPackageInfo) {
        if (!myPackageInfo.mIntentFiltersLoaded) {
            ScanManifestTask.parseInstalledPackage(mContext, myPackageInfo);
        }
    }

    private void fillIntentFiltersForAllPackages() {
        for (MyPackageInfoImpl myPackageInfo : mPackages.values()) {
            fillIntentFiltersForPackage(myPackageInfo);
        }
    }

    private MyPackageInfoImpl loadPackageInfoOrGetCached(String packageName) {
        synchronized (mLock) {
            if (mPackages.containsKey(packageName)) {
                return mPackages.get(packageName);
            }
        }
        return loadPackageInfo(packageName);
    }

    // Note: this function is currently run in synchronized(mLock)
    private void fillPermissionsBasedOnPackageInfo(MyPackageInfoImpl packageInfo, PermissionInfo[] permissions, List<MyPermissionInfo> outAppPermissions) {
        if (permissions == null || permissions.length == 0) {
            return;
        }

        for (PermissionInfo permissionInfo : permissions) {

            // TODO: convert mPermissions to parameter used only on one thread

            // Get MyPermissionInfoImpl from map or add new to it
            MyPermissionInfoImpl nowRegisteredPermission = mPermissions.get(permissionInfo.name);
            if (nowRegisteredPermission == null) {
                nowRegisteredPermission = new MyPermissionInfoImpl();
                mPermissions.put(permissionInfo.name, nowRegisteredPermission);
            }

            // Fill it with info from PackageInfo
            boolean needInfoFromPackageManager = nowRegisteredPermission.fillWithInfoFromApp(permissionInfo, packageInfo);

            // Fill it with info from PackageManagerImpl
            if (needInfoFromPackageManager) {
                PermissionInfo permissionInfoFromPM = null;
                try {
                    permissionInfoFromPM = mPm.getPermissionInfo(permissionInfo.name, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Found permission in package but not in system", e);
                    // Pass null to fillWithInfoFromPackageManager
                }
                nowRegisteredPermission.fillWithInfoFromPackageManager(permissionInfoFromPM);
            }

            outAppPermissions.add(nowRegisteredPermission);
        }
    }

    private PermissionDetails collectPermissionDetails(String permission) {
        // Fill packages details
        loadAllInstalledPackagesInfoIfNeeded();

        // Get permission details from package manager
        // TODO: deduplicate this code
        MyPermissionInfoImpl myPermissionInfo = mPermissions.get(permission);
        if (myPermissionInfo == null) {
            myPermissionInfo = new MyPermissionInfoImpl();
            // Don't add this permission to map if it doesn't exist
        }
        if (!myPermissionInfo.mOwnerVerified) {
            PermissionInfo permissionInfo = null;
            try {
                permissionInfo = mPm.getPermissionInfo(permission, 0);
            } catch (PackageManager.NameNotFoundException ignored) {}
            myPermissionInfo.fillWithInfoFromPackageManager(permissionInfo);

        }

        // Collect usage info
        ArrayList<MyPackageInfo> grantedTo = new ArrayList<>();
        ArrayList<MyPackageInfo> implicitlyGrantedTo = new ArrayList<>();
        ArrayList<MyPackageInfo> deniedTo = new ArrayList<>();
        ArrayList<MyComponentInfo> enforcingComponents = new ArrayList<>();

        for (MyPackageInfoImpl packageInfo : mPackages.values()) {
            // Find enforcing components
            for (MyComponentInfo component : packageInfo.mActivities) {
                if (permission.equals(component.getPermission())) {
                    enforcingComponents.add(component);
                }
            }
            for (MyComponentInfo component : packageInfo.mReceivers) {
                if (permission.equals(component.getPermission())) {
                    enforcingComponents.add(component);
                }
            }
            for (MyComponentInfo component : packageInfo.mServices) {
                if (permission.equals(component.getPermission())) {
                    enforcingComponents.add(component);
                }
            }
            for (MyComponentInfo component : packageInfo.mProviders) {
                if (permission.equals(component.getPermission()) ||
                        permission.equals(component.getWritePermission())) {
                    enforcingComponents.add(component);
                }
            }

            // Categorize to granted/denied
            String[] requestedPermissions = packageInfo.mSystemPackageInfo.requestedPermissions;
            boolean requested = requestedPermissions != null &&
                    Arrays.asList(requestedPermissions).contains(permission);
            boolean granted = mPm.checkPermission(permission, packageInfo.mPackageName) == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (requested) {
                    grantedTo.add(packageInfo);
                } else {
                    implicitlyGrantedTo.add(packageInfo);
                }
            } else if (requested) {
                deniedTo.add(packageInfo);
            }
        }

        // Pack results and return
        PermissionDetails permissionDetails = new PermissionDetails();
        permissionDetails.permissionInfo = myPermissionInfo;
        permissionDetails.grantedTo = grantedTo.toArray(new MyPackageInfo[grantedTo.size()]);
        permissionDetails.implicitlyGrantedTo = implicitlyGrantedTo.toArray(new MyPackageInfo[implicitlyGrantedTo.size()]);
        permissionDetails.deniedTo = deniedTo.toArray(new MyPackageInfo[deniedTo.size()]);
        permissionDetails.enforcingComponents = enforcingComponents.toArray(new MyComponentInfo[enforcingComponents.size()]);
        return permissionDetails;
    }

    private MyPackageInfoImpl loadPackageInfo(String packageName) {
        PackageInfo packageInfo;
        try {
            // Try loading package info normally
            packageInfo = mPm.getPackageInfo(packageName, STANDARD_FLAGS);
        } catch (PackageManager.NameNotFoundException e) {

            // Failed: no such package
            Log.w(TAG, "getPackageInfo() thrown NameNotFoundException for " + packageName, e);
            return null;
        } catch (Exception e) {

            // Failed: problem with binder buffer, retry by component types
            try {
                // Activities and general
                packageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA | PackageManager.GET_DISABLED_COMPONENTS);
                if (packageInfo.applicationInfo == null) {
                    return null;
                }

                // Receivers
                PackageInfo partialPackageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_RECEIVERS | PackageManager.GET_META_DATA | PackageManager.GET_DISABLED_COMPONENTS);
                packageInfo.receivers = partialPackageInfo.receivers;

                // Services
                partialPackageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_SERVICES | PackageManager.GET_META_DATA | PackageManager.GET_DISABLED_COMPONENTS);
                packageInfo.services = partialPackageInfo.services;

                // Providers
                partialPackageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA | PackageManager.GET_URI_PERMISSION_PATTERNS | PackageManager.GET_DISABLED_COMPONENTS);
                packageInfo.providers = partialPackageInfo.providers;

                // Permissions
                partialPackageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
                packageInfo.permissions = partialPackageInfo.permissions;
                packageInfo.requestedPermissions = partialPackageInfo.requestedPermissions;

                // uncomment when we'll be interested in requestedPermissionFlags
                /*if (Build.VERSION.SDK_INT >= 16) {
                    packageInfo.requestedPermissionsFlags = partialPackageInfo.requestedPermissionsFlags;
                }*/

            } catch (Exception e1) {
                Log.w(TAG, "getPackageInfo() by components thrown Exception for " + packageName + "", e1);
                return null;
            }
        }

        // Construct our wrapper
        return convertPackageInfoAndAddToCache(packageInfo);
    }

    private MyPackageInfoImpl convertPackageInfoAndAddToCache(PackageInfo packageInfo) {
        // Filter out non-applications
        if (packageInfo.applicationInfo == null) {
            return null;
        }

        // Convert package info
        MyPackageInfoImpl myPackageInfo = new MyPackageInfoImpl(packageInfo);
        synchronized (mLock) {
            mPackages.put(packageInfo.packageName, myPackageInfo);

            // And scan permissions
            ArrayList<MyPermissionInfo> appPermissions = new ArrayList<>();
            fillPermissionsBasedOnPackageInfo(myPackageInfo, packageInfo.permissions, appPermissions);
            myPackageInfo.mDefinedPermissions = appPermissions;
        }
        return myPackageInfo;
    }


    @Override
    @SuppressWarnings("unchecked")
    public Promise<Collection<MyPackageInfo>, Void, Void> getPackages(final boolean withIntentFilters) {
        final DeferredObject<Collection<MyPackageInfo>, Void, Void> deferred = new DeferredObject<>();
        synchronized (mLock) {
            if (mLoadedAllPackages && (mLoadedAllPackagesIncludingIntentFilters || !withIntentFilters)) {
                deferred.resolve((Collection) mPackages.values());
            } else {
                mWorkerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadAllInstalledPackagesInfoIfNeeded();
                        if (withIntentFilters) {
                            fillIntentFiltersForAllPackages();
                            mLoadedAllPackagesIncludingIntentFilters = true;
                        }
                        deferred.resolve((Collection) mPackages.values());
                    }
                });
            }
        }

        return new AndroidDeferredObject<>(deferred);
    }

    @Override
    public Promise<MyPackageInfo, Void, Void> getPackageInfo(final boolean withIntentFilters, final String packageName) {
        final DeferredObject<MyPackageInfo, Void, Void> deferred = new DeferredObject<>();
        synchronized (mLock) {
            if (mPackages.containsKey(packageName) && !withIntentFilters) { // TODO: load intent filters only if needed
                deferred.resolve(mPackages.get(packageName));
            } else {
                mWorkerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MyPackageInfoImpl myPackageInfo = loadPackageInfoOrGetCached(packageName);
                        if (myPackageInfo == null) {
                            deferred.reject(null);
                            return;
                        }
                        if (withIntentFilters) {
                            fillIntentFiltersForPackage(myPackageInfo);
                        }
                        deferred.resolve(myPackageInfo);
                    }
                });
            }
        }

        return new AndroidDeferredObject<>(deferred);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, MyPermissionInfo>, Void, Void> getPermissions() {
        final DeferredObject<Map<String, MyPermissionInfo>, Void, Void> deferred = new DeferredObject<>();
        synchronized (mLock) {
            if (mLoadedAllPackages) {
                deferred.resolve((Map) mPermissions);
            } else {
                mWorkerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadAllInstalledPackagesInfoIfNeeded();
                        deferred.resolve((Map) mPermissions);
                    }
                });
            }
        }

        return new AndroidDeferredObject<>(deferred);
    }

    @Override
    public Promise<PermissionDetails, Void, Void> getPermissionDetails(final String permissionName) {
        final DeferredObject<PermissionDetails, Void, Void> deferred = new DeferredObject<>();
        mWorkerHandler.post(new Runnable() {
            @Override
            public void run() {
                deferred.resolve(collectPermissionDetails(permissionName));
            }
        });

        return new AndroidDeferredObject<>(deferred);
    }



    private class PackagesChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            synchronized (mLock) {
                mPackages.remove(packageName);
                mLoadedAllPackages = false;
                mLoadedAllPackagesIncludingIntentFilters = false;
            }
        }
    }
}
