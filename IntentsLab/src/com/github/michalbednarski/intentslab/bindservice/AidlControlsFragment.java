package com.github.michalbednarski.intentslab.bindservice;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.github.michalbednarski.intentslab.SingleFragmentActivity;
import com.github.michalbednarski.intentslab.bindservice.manager.BindServiceManager;
import com.github.michalbednarski.intentslab.sandbox.IAidlInterface;
import com.github.michalbednarski.intentslab.sandbox.SandboxedMethod;

/**
 * Created by mb on 30.09.13.
 */
public class AidlControlsFragment extends BaseServiceFragment {

    private IAidlInterface mAidlInterface;
    private BaseAdapter mAdapter;
    private ListView mListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        getServiceHelper().prepareAidlAndRunWhenReady(getActivity(), new BindServiceManager.AidlReadyCallback() {
            @Override
            public void onAidlReady(IAidlInterface anInterface) {
                mAidlInterface = anInterface;
                if (anInterface != null) {
                    createAdapter();
                } else {
                    UnrecognizedAidlFragment fragment = new UnrecognizedAidlFragment();
                    fragment.setArguments(getArguments());
                    getFragmentManager()
                            .beginTransaction()
                            .replace(getId(), fragment)
                            .commitAllowingStateLoss();
                }
            }
        });
    }

    private void createAdapter() {
        try {
            // Get interface name and format it
            final String name = mAidlInterface.getInterfaceName();
            int lastNamePart = name.lastIndexOf('.') + 1;
            final SpannableString displayName = new SpannableString(name.substring(0, lastNamePart) + "\n" + name.substring(lastNamePart));
            displayName.setSpan(new RelativeSizeSpan(1.5f), lastNamePart + 1, name.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Get list of methods
            final SandboxedMethod[] sandboxedMethods = mAidlInterface.getMethods();

            mAdapter = new BaseAdapter() {
                @Override
                public int getCount() {
                    return 1 + sandboxedMethods.length;
                }

                @Override
                public Object getItem(int position) {
                    return null;
                }

                @Override
                public long getItemId(int position) {
                    return position - 1;
                }

                @Override
                public int getViewTypeCount() {
                    return 2;
                }

                @Override
                public int getItemViewType(int position) {
                    return position == 0 ? 1 : 0;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (position == 0) {
                        if (convertView == null) {
                            TextView headerText = new TextView(getActivity());
                            headerText.setText(displayName);
                            return headerText;
                        }
                    } else {
                        if (convertView == null) {
                            convertView = new Button(getActivity());
                            convertView.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    int methodNumber = (Integer) v.getTag();
                                    startActivity(
                                            new Intent(getActivity(), SingleFragmentActivity.class)
                                                    .putExtra(SingleFragmentActivity.EXTRA_FRAGMENT, InvokeAidlMethodDialog.class.getName())
                                                    .putExtra(ARG_SERVICE_DESCRIPTOR, getArguments().getParcelable(ARG_SERVICE_DESCRIPTOR))
                                                    .putExtra(InvokeAidlMethodDialog.ARG_METHOD_NUMBER, methodNumber)
                                    );
                                }
                            });
                        }
                        ((Button) convertView).setText(sandboxedMethods[position - 1].name);
                        convertView.setTag(position - 1);
                    }
                    return convertView;
                }

                @Override
                public boolean areAllItemsEnabled() {
                    return false;
                }

                @Override
                public boolean isEnabled(int position) {
                    return false;
                }
            };
            if (mListView != null) {
                mListView.setAdapter(mAdapter);
            }
        } catch (RemoteException e) {
            // TODO: handle crashed sandbox
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mListView = new ListView(getActivity());
        if (mAdapter != null) {
            mListView.setAdapter(mAdapter);
        }
        return mListView;
    }

    @Override
    public void onDestroyView() {
        mListView = null;
        super.onDestroyView();
    }
}
