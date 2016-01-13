/*
 * Copyright (C) 2014 GeoODK
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

/**
 * Responsible for displaying buttons to launch the major activities. Launches
 * some activities based on returns of others.
 *
 * @author Jon Nordling (jonnordling@gmail.com)
 */

package com.kll.collect.android.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import com.kll.collect.android.R;
import com.kll.collect.android.application.Collect;
import com.kll.collect.android.database.ItemsetDbAdapter;
import com.kll.collect.android.database.ODKSQLiteOpenHelper;
import com.kll.collect.android.listeners.DeleteFormsListener;
import com.kll.collect.android.listeners.DiskSyncListener;
import com.kll.collect.android.listeners.FormDownloaderListener;
import com.kll.collect.android.listeners.FormListDownloaderListener;
import com.kll.collect.android.logic.FormDetails;
import com.kll.collect.android.preferences.PreferencesActivity;
import com.kll.collect.android.provider.FormsProviderAPI;
import com.kll.collect.android.provider.InstanceProviderAPI;
import com.kll.collect.android.tasks.CheckForFormUpdateTask;
import com.kll.collect.android.tasks.DeleteFormsTask;
import com.kll.collect.android.tasks.DiskSyncTask;
import com.kll.collect.android.tasks.DownloadFormListTask;
import com.kll.collect.android.tasks.DownloadFormsTask;
import com.kll.collect.android.utilities.VersionHidingCursorAdapter;
import com.kll.collect.android.utilities.WebUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class GeoODK extends Activity implements FormListDownloaderListener,
        FormDownloaderListener, DiskSyncListener,DeleteFormsListener{
    private static final String t = "GeoODK";
    private static boolean EXIT = false;
    private AlertDialog mAlertDialog;
    private String[] assestFormList;

    private static final int PROGRESS_DIALOG = 1;
    private static final int AUTH_DIALOG = 2;

    private ProgressDialog mProgressDialog;

    private ArrayList<Long> mSelected = new ArrayList<Long>();
    private ArrayList<Long> mToDelete = new ArrayList<Long>();

    private static final String BUNDLE_FORM_MAP = "formmap";
    private static final String DIALOG_TITLE = "dialogtitle";
    private static final String DIALOG_MSG = "dialogmsg";
    private static final String DIALOG_SHOWING = "dialogshowing";
    private static final String FORMLIST = "formlist";


    private SimpleAdapter mFormListAdapter;
    private SimpleCursorAdapter mInstances;

    private int mDefaultTxtColor;

    private DownloadFormListTask mDownloadFormListTask;
    private DownloadFormsTask mDownloadFormsTask;

    private static final String FORMNAME = "formname";
    private static final String FORMDETAIL_KEY = "formdetailkey";
    private static final String FORMID_DISPLAY = "formiddisplay";

    private boolean mShouldExit;
    private static final String SHOULD_EXIT = "shouldexit";
    private static final boolean DO_NOT_EXIT = false;

    private String mAlertTitle;
    private String mAlertMsg;
    private boolean mAlertShowing = false;

    private SharedPreferences mSharedPrefrence;

    private ArrayList<HashMap<String, String>> mFormList;

    private HashMap<String, FormDetails> mFormNamesAndURLs = new HashMap<String,FormDetails>();



    static class BackgroundTasks {
        DiskSyncTask mDiskSyncTask = null;
        DeleteFormsTask mDeleteFormsTask = null;

        BackgroundTasks() {
        };
    }

    BackgroundTasks mBackgroundTasks; // handed across orientation changes

	
    public static final String FORMS_PATH = Collect.ODK_ROOT + File.separator + "forms";


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.geoodk_layout);
        
        //Create the files and directorys
        
        Log.i(t, "Starting up, creating directories");
		try {
			Collect.createODKDirs();
		} catch (RuntimeException e) {
			createErrorDialog(e.getMessage(), EXIT);
			return;
		}
		assestFormList = getAssetFormList();
		copyForms(assestFormList);

        mSharedPrefrence = PreferenceManager.getDefaultSharedPreferences(this);

        boolean updateAvailable = mSharedPrefrence.getBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false);

        if (!updateAvailable){

            checkForUpdates();
        }

        TextView txtUpdate = (TextView) findViewById(R.id.geoodk_update_txt);
        mDefaultTxtColor = txtUpdate.getCurrentTextColor();
        txtUpdate.setTextColor(Color.parseColor("#efefef"));
		
		ImageButton geoodk_collect_button = (ImageButton) findViewById(R.id.geoodk_collect_butt);
        geoodk_collect_button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Do something in response to button click
				Collect.getInstance().getActivityLogger().logAction(this, "fillBlankForm", "click");
				Intent i = new Intent(getApplicationContext(), FormChooserList.class);
				startActivity(i);
			}
		});

        ImageButton geoodk_manage_but = (ImageButton) findViewById(R.id.geoodk_edit_butt);
		geoodk_manage_but.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Collect.getInstance().getActivityLogger()
						.logAction(this, "editSavedForm", "click");
				Intent i = new Intent(getApplicationContext(),
						InstanceChooserList.class);
				startActivity(i);
			}
		});
		ImageButton geoodk_update_but = (ImageButton) findViewById(R.id.geoodk_update_butt);
        geoodk_update_but.setEnabled(updateAvailable);
        if (updateAvailable){
            geoodk_update_but.setImageResource(R.drawable.update_enable);
            txtUpdate.setTextColor(mDefaultTxtColor);
        }
		geoodk_update_but.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {

				createUpdateAlert(savedInstanceState);

/*				Collect.getInstance().getActivityLogger()
						.logAction(this, "map_data", "click");
				Intent i = new Intent(getApplicationContext(),
						OSM_Map.class);
				startActivity(i);*/
			}
		});
		
		ImageButton geoodk_settings_but = (ImageButton) findViewById(R.id.geoodk_settings_butt);
		geoodk_settings_but.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Collect.getInstance()
				.getActivityLogger()
				.logAction(this, "Main_Settings", "click");
				Intent ig = new Intent( getApplicationContext(), MainSettingsActivity.class);
						startActivity(ig);
			}
		});
		
		ImageButton geoodk_send_but = (ImageButton) findViewById(R.id.geoodk_send_data_butt);
		geoodk_send_but.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Collect.getInstance().getActivityLogger()
						.logAction(this, "uploadForms", "click");
				Intent i = new Intent(getApplicationContext(),
						InstanceUploaderList.class);
				startActivity(i);
			}
		});
		ImageButton geoodk_send_attachment_but = (ImageButton) findViewById(R.id.geoodk_send_attachment_butt);
		geoodk_send_attachment_but.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Collect.getInstance().getActivityLogger()
						.logAction(this, "uploadForms", "click");
				Intent i = new Intent(getApplicationContext(),
						AttachmentUploaderList.class);
				startActivity(i);
			}
		});
		ImageButton geoodk_stat_but = (ImageButton) findViewById(R.id.geoodk_stat_butt);
		geoodk_stat_but.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Collect.getInstance().getActivityLogger()
						.logAction(this, "stat", "click");
				Intent i = new Intent(getApplicationContext(),
						StatTable.class);
				startActivity(i);
			}
		});

		ImageButton geoodk_delete_but = (ImageButton) findViewById(R.id.geoodk_delete_data_butt);
		geoodk_delete_but.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Collect.getInstance().getActivityLogger()
						.logAction(this, "deleteSavedForms", "click");
				Intent i = new Intent(getApplicationContext(),
						FileManagerTabs.class);
				startActivity(i);
			}
		});



        mSharedPrefrence.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                ImageButton update_but = (ImageButton) findViewById(R.id.geoodk_update_butt);
                TextView update_txt = (TextView) findViewById(R.id.geoodk_update_txt);
                if (sharedPreferences.getBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false)) {
                    update_but.setEnabled(true);
                    update_but.setImageResource(R.drawable.update_enable);
                    update_txt.setTextColor(mDefaultTxtColor);
                }else{
                    update_but.setEnabled(false);
                    update_but.setImageResource(R.drawable.update_disable);
                    update_txt.setTextColor(Color.parseColor("#efefef"));
                }
            }
        });

		//End of Main activity
    }


    @Override
    protected void onResume() {
        super.onResume();
        ImageButton update_but = (ImageButton) findViewById(R.id.geoodk_update_butt);
        TextView update_txt = (TextView) findViewById(R.id.geoodk_update_txt);
        if (mSharedPrefrence.getBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false)) {
            update_but.setEnabled(true);
            update_but.setImageResource(R.drawable.update_enable);
            update_txt.setTextColor(mDefaultTxtColor);
        }else{
            update_but.setEnabled(false);
            update_but.setImageResource(R.drawable.update_disable);
            update_txt.setTextColor(Color.parseColor("#efefef"));
        }

    }


    private void checkForUpdates(){

        SharedPreferences.Editor e = mSharedPrefrence.edit();
        if (isOnline()){
            Log.wtf(t, "You are online");
            //TODO
            new CheckForFormUpdateTask().execute();
            return;

        }

        e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE,false);
        e.commit();
    }


    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }



    private void createUpdateAlert(final Bundle bundle) {
        mAlertDialog = new AlertDialog.Builder(this).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (isOnline()) {
                    updateForm(bundle);
                }else{
                    Toast.makeText(getApplicationContext(),"Please connect to the internet", Toast.LENGTH_SHORT).show();
                }

            }
        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                }).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setTitle("Confirm Update");
        mAlertDialog.setMessage("If possible make sure all the previous data is finalized and uploaded before you update questionnaire. Do you want to proceed with the update? ");
        mAlertDialog.show();
    }




    //From Pujan
    public void updateForm(Bundle savedInstanceState){
        if (savedInstanceState != null) {
            // If the screen has rotated, the hashmap with the form ids and urls is passed here.
            if (savedInstanceState.containsKey(BUNDLE_FORM_MAP)) {
                mFormNamesAndURLs =
                        (HashMap<String, FormDetails>) savedInstanceState
                                .getSerializable(BUNDLE_FORM_MAP);
            }

            // to restore alert dialog.
            if (savedInstanceState.containsKey(DIALOG_TITLE)) {
                mAlertTitle = savedInstanceState.getString(DIALOG_TITLE);
            }
            if (savedInstanceState.containsKey(DIALOG_MSG)) {
                mAlertMsg = savedInstanceState.getString(DIALOG_MSG);
            }
            if (savedInstanceState.containsKey(DIALOG_SHOWING)) {
                mAlertShowing = savedInstanceState.getBoolean(DIALOG_SHOWING);
            }
            if (savedInstanceState.containsKey(SHOULD_EXIT)) {
                mShouldExit = savedInstanceState.getBoolean(SHOULD_EXIT);
            }
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(FORMLIST)) {
            mFormList =
                    (ArrayList<HashMap<String, String>>) savedInstanceState.getSerializable(FORMLIST);
        } else {
            mFormList = new ArrayList<HashMap<String, String>>();
        }
        if (getLastNonConfigurationInstance() instanceof DownloadFormListTask) {
            mDownloadFormListTask = (DownloadFormListTask) getLastNonConfigurationInstance();
            if (mDownloadFormListTask.getStatus() == AsyncTask.Status.FINISHED) {
                try {
                    dismissDialog(PROGRESS_DIALOG);
                } catch (IllegalArgumentException e) {
                    Log.i(t, "Attempting to close a dialog that was not previously opened");
                }
                mDownloadFormsTask = null;
            }
        } else if (getLastNonConfigurationInstance() instanceof DownloadFormsTask) {
            mDownloadFormsTask = (DownloadFormsTask) getLastNonConfigurationInstance();
            if (mDownloadFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
                try {
                    dismissDialog(PROGRESS_DIALOG);
                } catch (IllegalArgumentException e) {
                    Log.i(t, "Attempting to close a dialog that was not previously opened");
                }
                mDownloadFormsTask = null;
            }
        } else if (getLastNonConfigurationInstance() == null) {
            // first time, so get the formlist
            downloadFormList();
        }

        String[] data = new String[] {
                FORMNAME, FORMID_DISPLAY, FORMDETAIL_KEY
        };
        int[] view = new int[] {
                R.id.text1, R.id.text2
        };

        mFormListAdapter =
                new SimpleAdapter(this, mFormList, R.layout.two_item_multiple_choice, data, view);

    }

    /**
     * Starts the download task and shows the progress dialog.
     */
    private void downloadFormList() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

        if (ni == null || !ni.isConnected()) {
            Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } else {

            mFormNamesAndURLs = new HashMap<String, FormDetails>();
            if (mProgressDialog != null) {
                // This is needed because onPrepareDialog() is broken in 1.6.
                mProgressDialog.setMessage(getString(R.string.please_wait));
            }
            showDialog(PROGRESS_DIALOG);

            if (mDownloadFormListTask != null &&
                    mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
                return; // we are already doing the download!!!
            } else if (mDownloadFormListTask != null) {
                mDownloadFormListTask.setDownloaderListener(null);
                mDownloadFormListTask.cancel(true);
                mDownloadFormListTask = null;
            }

            mDownloadFormListTask = new DownloadFormListTask();
            mDownloadFormListTask.setDownloaderListener(this);
            mDownloadFormListTask.execute();
        }
    }

    /**
     * Called when the form list has finished downloading. results will either contain a set of
     * <formname, formdetails> tuples, or one tuple of DL.ERROR.MSG and the associated message.
     *
     * @param result
     */
    public void formListDownloadingComplete(HashMap<String, FormDetails> result) {
        dismissDialog(PROGRESS_DIALOG);
        mDownloadFormListTask.setDownloaderListener(null);
        mDownloadFormListTask = null;

        if (result == null) {
            Log.e(t, "Formlist Downloading returned null.  That shouldn't happen");
            // Just displayes "error occured" to the user, but this should never happen.
            createAlertDialog(getString(R.string.load_remote_form_error),
                    getString(R.string.error_occured), EXIT);
            return;
        }

        if (result.containsKey(DownloadFormListTask.DL_AUTH_REQUIRED)) {
            // need authorization
            showDialog(AUTH_DIALOG);
        } else if (result.containsKey(DownloadFormListTask.DL_ERROR_MSG)) {
            // Download failed
            String dialogMessage =
                    getString(R.string.list_failed_with_error,
                            result.get(DownloadFormListTask.DL_ERROR_MSG).errorStr);
            String dialogTitle = getString(R.string.load_remote_form_error);
            createAlertDialog(dialogTitle, dialogMessage, DO_NOT_EXIT);
        } else {
            // Everything worked. Clear the list and add the results.
            mFormNamesAndURLs = result;

            mFormList.clear();

            ArrayList<String> ids = new ArrayList<String>(mFormNamesAndURLs.keySet());
            for (int i = 0; i < result.size(); i++) {
                String formDetailsKey = ids.get(i);
                FormDetails details = mFormNamesAndURLs.get(formDetailsKey);
                HashMap<String, String> item = new HashMap<String, String>();
                item.put(FORMNAME, details.formName);
                item.put(FORMID_DISPLAY,
                        ((details.formVersion == null) ? "" : (getString(R.string.version) + " " + details.formVersion + " ")) +
                                "ID: " + details.formID );
                item.put(FORMDETAIL_KEY, formDetailsKey);

                // Insert the new form in alphabetical order.
                if (mFormList.size() == 0) {
                    mFormList.add(item);
                } else {
                    int j;
                    for (j = 0; j < mFormList.size(); j++) {
                        HashMap<String, String> compareMe = mFormList.get(j);
                        String name = compareMe.get(FORMNAME);
                        if (name.compareTo(mFormNamesAndURLs.get(ids.get(i)).formName) > 0) {
                            break;
                        }
                    }
                    mFormList.add(j, item);
                }
            }
            mFormListAdapter.notifyDataSetChanged();
        }
        downloadNewFiles(mFormListAdapter);
    }

    /**
     * Creates an alert dialog with the given tite and message. If shouldExit is set to true, the
     * activity will exit when the user clicks "ok".
     *
     * @param title
     * @param message
     * @param shouldExit
     */
    private void createAlertDialog(String title, String message, final boolean shouldExit) {
        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "show");
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // ok
                        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "OK");
                        // just close the dialog
                        mAlertShowing = false;
                        // successful download, so quit
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), quitListener);
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertMsg = message;
        mAlertTitle = title;
        mAlertShowing = true;
        mShouldExit = shouldExit;
        mAlertDialog.show();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.PROGRESS_DIALOG", "show");
                mProgressDialog = new ProgressDialog(this);
                mAlertMsg = getString(R.string.please_wait);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.PROGRESS_DIALOG", "OK");
                                dialog.dismiss();
                                // we use the same progress dialog for both
                                // so whatever isn't null is running
                                if (mDownloadFormListTask != null) {
                                    mDownloadFormListTask.setDownloaderListener(null);
                                    mDownloadFormListTask.cancel(true);
                                    mDownloadFormListTask = null;
                                }
                                if (mDownloadFormsTask != null) {
                                    mDownloadFormsTask.setDownloaderListener(null);
                                    mDownloadFormsTask.cancel(true);
                                    mDownloadFormsTask = null;
                                }
                            }
                        };
                mProgressDialog.setTitle(getString(R.string.downloading_data));
                mProgressDialog.setMessage(mAlertMsg);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return mProgressDialog;
            case AUTH_DIALOG:
                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.AUTH_DIALOG", "show");
                AlertDialog.Builder b = new AlertDialog.Builder(this);

                LayoutInflater factory = LayoutInflater.from(this);
                final View dialogView = factory.inflate(R.layout.server_auth_dialog, null);

                // Get the server, username, and password from the settings
                SharedPreferences settings =
                        PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                String server =
                        settings.getString(PreferencesActivity.KEY_SERVER_URL,
                                getString(R.string.default_server_url));

                String formListUrl = getString(R.string.default_odk_formlist);
                final String url =
                        server + settings.getString(PreferencesActivity.KEY_FORMLIST_URL, formListUrl);
                Log.i(t, "Trying to get formList from: " + url);

                EditText username = (EditText) dialogView.findViewById(R.id.username_edit);
                String storedUsername = settings.getString(PreferencesActivity.KEY_USERNAME, null);
                username.setText(storedUsername);

                EditText password = (EditText) dialogView.findViewById(R.id.password_edit);
                String storedPassword = settings.getString(PreferencesActivity.KEY_PASSWORD, null);
                password.setText(storedPassword);

                b.setTitle(getString(R.string.server_requires_auth));
                b.setMessage(getString(R.string.server_auth_credentials, url));
                b.setView(dialogView);
                b.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.AUTH_DIALOG", "OK");

                        EditText username = (EditText) dialogView.findViewById(R.id.username_edit);
                        EditText password = (EditText) dialogView.findViewById(R.id.password_edit);

                        Uri u = Uri.parse(url);

                        WebUtils.addCredentials(username.getText().toString(), password.getText()
                                .toString(), u.getHost());
                        downloadFormList();
                    }
                });
                b.setNegativeButton(getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.AUTH_DIALOG", "Cancel");
                                finish();
                            }
                        });

                b.setCancelable(false);
                mAlertShowing = false;
                return b.create();
        }
        return null;
    }

    /**
     * starts the task to download the selected forms, also shows progress dialog
     * @param mFormListAdapter
     */
    @SuppressWarnings("unchecked")
    private void downloadNewFiles(SimpleAdapter mFormListAdapter) {
        int totalCount = 0;
        ArrayList<FormDetails> filesToDownload = new ArrayList<FormDetails>();

        for (int i = 0; i < mFormListAdapter.getCount(); i++) {
            HashMap<String, String> item =
                    (HashMap<String, String>) mFormListAdapter.getItem(i);
            filesToDownload.add(mFormNamesAndURLs.get(item.get(FORMDETAIL_KEY)));
        }

        totalCount = filesToDownload.size();

        Collect.getInstance().getActivityLogger().logAction(this, "downloadSelectedFiles", Integer.toString(totalCount));

        if (totalCount > 0) {
            // show dialog box
            showDialog(PROGRESS_DIALOG);

            mDownloadFormsTask = new DownloadFormsTask();
            mDownloadFormsTask.setDownloaderListener(this);
            mDownloadFormsTask.execute(filesToDownload);
        } else {
            Toast.makeText(getApplicationContext(), R.string.noselect_error, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    public void progressUpdate(String currentFile, int progress, int total) {
        mAlertMsg = getString(R.string.fetching_file, currentFile, progress, total);
        mProgressDialog.setMessage(mAlertMsg);
    }


    @Override
    public void formsDownloadingComplete(HashMap<FormDetails, String> result) {
        if (mDownloadFormsTask != null) {
            mDownloadFormsTask.setDownloaderListener(null);
        }

        if (mProgressDialog.isShowing()) {
            // should always be true here
            mProgressDialog.dismiss();
        }

        Set<FormDetails> keys = result.keySet();
        StringBuilder b = new StringBuilder();
        for (FormDetails k : keys) {
            b.append(k.formName +
                    " (" +
                    ((k.formVersion != null) ?
                            (this.getString(R.string.version) + ": " + k.formVersion + " ")
                            : "") +
                    "ID: " + k.formID + ") - " +
                    result.get(k));
            b.append("\n\n");
        }
       // deleteOldFiles();
        SharedPreferences.Editor e = mSharedPrefrence.edit();
        e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE,false);
        e.commit();
        ImageButton update_but = (ImageButton) findViewById(R.id.geoodk_update_butt);
        TextView update_txt = (TextView) findViewById(R.id.geoodk_update_txt);
        if (mSharedPrefrence.getBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false)) {
            update_but.setEnabled(true);
            update_but.setImageResource(R.drawable.update_enable);
            update_txt.setTextColor(mDefaultTxtColor);
        }else{
            update_but.setEnabled(false);
            update_but.setImageResource(R.drawable.update_disable);
            update_txt.setTextColor(Color.parseColor("#efefef"));
        }
        createAlertDialog(getString(R.string.download_forms_result), b.toString().trim(), EXIT);
    }
    @Override
    public void SyncComplete(String result) {

    }

    private void deleteOldFiles() {


        mBackgroundTasks = (BackgroundTasks) getLastNonConfigurationInstance();
        if (mBackgroundTasks == null) {
            mBackgroundTasks = new BackgroundTasks();
            mBackgroundTasks.mDiskSyncTask = new DiskSyncTask();
            mBackgroundTasks.mDiskSyncTask.setDiskSyncListener(this);
            mBackgroundTasks.mDiskSyncTask.execute((Void[]) null);
        }

        String sortOrder = FormsProviderAPI.FormsColumns.DISPLAY_NAME + " ASC, " + FormsProviderAPI.FormsColumns.JR_VERSION + " DESC";
        Cursor c = managedQuery(FormsProviderAPI.FormsColumns.CONTENT_URI,null, null, null, sortOrder);
        ArrayList<String> formId = new ArrayList<String>();
        ArrayList<String> formVersion = new ArrayList<String>();
        c.moveToFirst();
        for(int i = 0;i<c.getCount();i++){
            formId.add(c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID)));
            formVersion.add(c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_VERSION)));
            c.moveToNext();
        }

        String[] data = new String[] { FormsProviderAPI.FormsColumns.DISPLAY_NAME,
                FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT, FormsProviderAPI.FormsColumns.JR_VERSION };
        mInstances = new VersionHidingCursorAdapter(FormsProviderAPI.FormsColumns.JR_VERSION, this,
                R.layout.two_item_multiple_choice, c, data,null);
        for (int pos = 0; pos < mInstances.getCount(); pos++) {
            Long id = mInstances.getItemId(pos);
            if (!mSelected.contains(id)) {
                mSelected.add(id);

            }
        }

        for (int x = 0;x<formId.size();x++){
            for(int y = x+1;y<formId.size();y++){
                if (formId.get(x).equals(formId.get(y))){
                    Log.i("Version 1 ",formVersion.get(x));
                    Log.i("Version 2 ",formVersion.get(y));
                    if(Double.parseDouble(formVersion.get(x))>Double.parseDouble(formVersion.get(y))){
                        mToDelete.add(mSelected.get(y));
                    }else {
                        mToDelete.add(mSelected.get(x));
                    }

                }

            }



        }
        deleteOldForms();

    }

    private void deleteOldForms() {
        // only start if no other task is running
        if (mBackgroundTasks.mDeleteFormsTask == null) {
            mBackgroundTasks.mDeleteFormsTask = new DeleteFormsTask();
            mBackgroundTasks.mDeleteFormsTask
                    .setContentResolver(getContentResolver());
            mBackgroundTasks.mDeleteFormsTask.setDeleteListener(this);
            mBackgroundTasks.mDeleteFormsTask.execute(mToDelete
                    .toArray(new Long[mToDelete.size()]));
        } else {
            Toast.makeText(this, getString(R.string.file_delete_in_progress),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void deleteComplete(int deletedForms) {
        Log.i(t, "Delete forms complete");
        Collect.getInstance().getActivityLogger().logAction(this, "deleteComplete", Integer.toString(deletedForms));
        if (deletedForms == mToDelete.size()) {
            // all deletes were successful
            Toast.makeText(getApplicationContext(),
                    getString(R.string.file_update_ok, deletedForms),
                    Toast.LENGTH_SHORT).show();


        } else {
            // had some failures
            Log.e(t, "Failed to update " + (mSelected.size() - deletedForms)
                    + " forms");
            Toast.makeText(
                    getApplicationContext(),
                    getString(R.string.file_deleted_error, mSelected.size()
                            - deletedForms, mSelected.size()),
                    Toast.LENGTH_LONG).show();
        }
        mBackgroundTasks.mDeleteFormsTask = null;
        mSelected.clear();
        /**
         TO DO: Set Shared Preference to False
         */
    }
























	private String[] getAssetFormList() {
		AssetManager assetManager = getAssets();
		String[] formList = null;
		try {
			formList = assetManager.list("forms");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//assetManager.list(path);
		// TODO Auto-generated method stub
		return formList;
	}



	private void copyForms(String[] forms){
		AssetManager assetManager = getAssets();
		InputStream in = null;
		OutputStream out = null;
		for (int i=0; forms.length>i; i++) {
			String filename = forms[i];
			File form_file = new File(FORMS_PATH,filename);
			if (!form_file.exists()){
				try {
					in = assetManager.open("forms/"+filename);
					out = new FileOutputStream(FORMS_PATH+File.separator+filename);
					copyFile(in, out);
					in.close();
		            out.flush();
		            out.close();
		            in = null;
		            out = null;
					
				} catch (IOException e) {
					Log.e("tag", "Failed to copy asset file: " + FORMS_PATH+File.separator+forms[i], e);
			}
				
			}
			 System.out.println(forms[i]);
		}
		
	}
	
	private void copyFile(InputStream in, OutputStream out) throws IOException
	{
	      byte[] buffer = new byte[1024];
	      int read;
	      while((read = in.read(buffer)) != -1)
	      {
	            out.write(buffer, 0, read);
	      }
	}
	
	private void createErrorDialog(String errorMsg, final boolean shouldExit) {
		Collect.getInstance().getActivityLogger()
				.logAction(this, "createErrorDialog", "show");
		mAlertDialog = new AlertDialog.Builder(this).create();
		mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
		mAlertDialog.setMessage(errorMsg);
		DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE:
					Collect.getInstance()
							.getActivityLogger()
							.logAction(this, "createErrorDialog",
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
