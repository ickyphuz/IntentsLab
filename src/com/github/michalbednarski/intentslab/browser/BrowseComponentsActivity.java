package com.github.michalbednarski.intentslab.browser;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.MenuItem;
import com.github.michalbednarski.intentslab.MasterDetailActivity;

public class BrowseComponentsActivity extends MasterDetailActivity {

    public static final String EXTRA_FETCHER = "BrowseComponentsActivity.fetcher";
    public static final String EXTRA_SERIALIZED_FETCHER = "BrowseComponentsActivity.fetcher.serialized";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 11) {
            onCreateAndroidSDK11AndUp();
        }
    }

    @Override
    protected Fragment createMasterFragment() {
        Bundle args = new Bundle();
        Fetcher fetcher;
        if (getIntent().hasExtra(EXTRA_SERIALIZED_FETCHER)) {
            fetcher = FetcherManager.unserializeFetcher(getIntent().getStringExtra(EXTRA_SERIALIZED_FETCHER));
        } else {
            fetcher = getIntent().getParcelableExtra(EXTRA_FETCHER);
        }
        args.putParcelable(BrowseComponentsFragment.ARG_FETCHER, fetcher);
        BrowseComponentsFragment fragment = new BrowseComponentsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @TargetApi(11)
    private void onCreateAndroidSDK11AndUp() {
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
