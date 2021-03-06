/*
 * Copyright (C) 2009 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.kll.collect.android.activities;

import com.kll.collect.android.R;

import com.kll.collect.android.application.Collect;
import com.kll.collect.android.listeners.DiskSyncListener;
import com.kll.collect.android.provider.FormsProviderAPI;
import com.kll.collect.android.provider.FormsProviderAPI.FormsColumns;
import com.kll.collect.android.tasks.DiskSyncTask;
import com.kll.collect.android.utilities.VersionHidingCursorAdapter;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Responsible for displaying all the valid forms in the forms directory. Stores the path to
 * selected form for use by {@link MainMenuActivity}.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FormChooserList extends ListActivity implements DiskSyncListener {

    private static final String t = "FormChooserList";
    private static final boolean EXIT = true;
    private static final String syncMsgKey = "syncmsgkey";

    private DiskSyncTask mDiskSyncTask;

    private AlertDialog mAlertDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // must be at the beginning of any activity that can be called from an external intent
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        setContentView(R.layout.chooser_list_layout);
        /*setTitle(getString(R.string.app_name) + " > " + getString(R.string.enter_data));*/
        setTitle(getString(R.string.app_name));

        String sortOrder = FormsColumns.DISPLAY_NAME + " ASC, " + FormsColumns.JR_VERSION + " DESC";
        Cursor c = managedQuery(FormsColumns.CONTENT_URI, null, null, null, sortOrder);

        ArrayList<String> formId = new ArrayList<String>();
        ArrayList<String> formVersion = new ArrayList<String>();
        ArrayList<String> formIdTemp = new ArrayList<String>();
        ArrayList<String> formVersionTemp = new ArrayList<String>();
        c.moveToFirst();
        for(int i = 0;i<c.getCount();i++){
            formId.add(c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID)));
            formVersion.add(c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_VERSION)));
            c.moveToNext();

        }
        formIdTemp = formId;
        formVersionTemp = formVersion;

        for (int x = 0;x<formId.size();x++){

            for(int y = x+1;y<formId.size();y++){
                if (formId.get(x).equals(formId.get(y))){
                    Log.i("Version 1 ",formVersion.get(x));
                    Log.i("Version 2 ",formVersion.get(y));
                    if(Double.parseDouble(formVersion.get(x))<Double.parseDouble(formVersion.get(y))){
                            formId.remove(x);
                            formVersion.remove(x);
                           x--;
                    }else {
                        formId.remove(y);
                        formVersion.remove(y);
                        y--;
                    }

                }

            }


        }
        String selection = "";
        for(int pos = 0;pos<formId.size();pos++){
            Log.i("form ID",formId.get(pos));
            Log.i("form version",formVersion.get(pos));
            if (selection.equals("")){
                selection = ("("+ FormsColumns.JR_FORM_ID + "='"+formId.get(pos) + "' and " + FormsColumns.JR_VERSION + "='" +formVersion.get(pos) + "') ");
            }else {
                selection = selection + (" or ("+ FormsColumns.JR_FORM_ID + "='"+formId.get(pos) + "' and " + FormsColumns.JR_VERSION + "='" +formVersion.get(pos) + "')");
            }
        }
        Cursor c1 = managedQuery(FormsColumns.CONTENT_URI, null, selection, null, sortOrder);
        String[] data = new String[] {
                FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_SUBTEXT, FormsColumns.JR_VERSION
        };
        int[] view = new int[] {
                R.id.text1, R.id.text2, R.id.text3
        };

        // render total instance view
        SimpleCursorAdapter instances =
            new VersionHidingCursorAdapter(FormsColumns.JR_VERSION, this, R.layout.two_item, c1, data, view);


        setListAdapter(instances);

        if (savedInstanceState != null && savedInstanceState.containsKey(syncMsgKey)) {
            TextView tv = (TextView) findViewById(R.id.status_text);
            tv.setText(savedInstanceState.getString(syncMsgKey));
        }

        // DiskSyncTask checks the disk for any forms not already in the content provider
        // that is, put here by dragging and dropping onto the SDCard
        mDiskSyncTask = (DiskSyncTask) getLastNonConfigurationInstance();
        if (mDiskSyncTask == null) {
            Log.i(t, "Starting new disk sync task");
            mDiskSyncTask = new DiskSyncTask();
            mDiskSyncTask.setDiskSyncListener(this);
            mDiskSyncTask.execute((Void[]) null);
        }
    }


    @Override
    public Object onRetainNonConfigurationInstance() {
        // pass the thread on restart
        return mDiskSyncTask;
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        TextView tv = (TextView) findViewById(R.id.status_text);
        outState.putString(syncMsgKey, tv.getText().toString());
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        // get uri to form
    	long idFormsTable = ((SimpleCursorAdapter) getListAdapter()).getItemId(position);
        Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, idFormsTable);

		Collect.getInstance().getActivityLogger().logAction(this, "onListItemClick", formUri.toString());

        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action)) {
            // caller is waiting on a picked form
            setResult(RESULT_OK, new Intent().setData(formUri));
        } else {
            // caller wants to view/edit a form, so launch formentryactivity
            startActivity(new Intent(Intent.ACTION_EDIT, formUri));
        }

        finish();
    }


    @Override
    protected void onResume() {
        mDiskSyncTask.setDiskSyncListener(this);
        super.onResume();

        if (mDiskSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
        	SyncComplete(mDiskSyncTask.getStatusMessage());
        }
    }


    @Override
    protected void onPause() {
        mDiskSyncTask.setDiskSyncListener(null);
        super.onPause();
    }


    @Override
    protected void onStart() {
    	super.onStart();
		Collect.getInstance().getActivityLogger().logOnStart(this);
    }

    @Override
    protected void onStop() {
		Collect.getInstance().getActivityLogger().logOnStop(this);
    	super.onStop();
    }


    /**
     * Called by DiskSyncTask when the task is finished
     */
    @Override
    public void SyncComplete(String result) {
        Log.i(t, "disk sync task complete");
        TextView tv = (TextView) findViewById(R.id.status_text);
        tv.setText(result);
    }


    /**
     * Creates a dialog with the given message. Will exit the activity when the user preses "ok" if
     * shouldExit is set to true.
     *
     * @param errorMsg
     * @param shouldExit
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {

    	Collect.getInstance().getActivityLogger().logAction(this, "createErrorDialog", "show");

        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                    	Collect.getInstance().getActivityLogger().logAction(this, "createErrorDialog",
                    			shouldExit ? "exitApplication" : "OK");
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }

}
