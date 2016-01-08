package com.kll.collect.android.tasks;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.kll.collect.android.R;
import com.kll.collect.android.application.Collect;
import com.kll.collect.android.listeners.FormListDownloaderListener;
import com.kll.collect.android.logic.FormDetails;
import com.kll.collect.android.preferences.PreferencesActivity;
import com.kll.collect.android.provider.FormsProviderAPI;
import com.kll.collect.android.provider.InstanceProviderAPI;
import com.kll.collect.android.utilities.DocumentFetchResult;
import com.kll.collect.android.utilities.WebUtils;

import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by nirab on 1/7/16. This is background task to query ODK server for available form updates. This is modification of  DownloadFormListTask.
 */
public class CheckForFormUpdateTask extends AsyncTask<Void, String, HashMap<String, FormDetails>> {
    private static final String t = "DownloadFormsTask";

    // used to store error message if one occurs
    public static final String DL_ERROR_MSG = "dlerrormessage";
    public static final String DL_AUTH_REQUIRED = "dlauthrequired";

    private FormListDownloaderListener mStateListener;

    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST =
            "http://openrosa.org/xforms/xformsList";


    private boolean isXformsListNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST);
    }


    @Override
    protected HashMap<String, FormDetails> doInBackground(Void... values) {
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
        String downloadListUrl =
                settings.getString(PreferencesActivity.KEY_SERVER_URL,
                        Collect.getInstance().getString(R.string.default_server_url));
        // NOTE: /formlist must not be translated! It is the well-known path on the server.
        String formListUrl = Collect.getInstance().getApplicationContext().getString(R.string.default_odk_formlist);
        String downloadPath = settings.getString(PreferencesActivity.KEY_FORMLIST_URL, formListUrl);
        downloadListUrl += downloadPath;

        Collect.getInstance().getActivityLogger().logAction(this, formListUrl, downloadListUrl);

        // We populate this with available forms from the specified server.
        // <formname, details>
        HashMap<String, FormDetails> formList = new HashMap<String, FormDetails>();

        //TODO Authenticate if Username and Password present in Prefrence
        /*WebUtils.addCredentials(username.getText().toString(), password.getText()
                .toString(), u.getHost());*/

        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Collect.getInstance().getHttpContext();
        HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

        DocumentFetchResult result =
                WebUtils.getXmlDocument(downloadListUrl, localContext, httpclient);

        // If we can't get the document, return the error, cancel the task
        if (result.errorMessage != null) {
            if (result.responseCode == 401) {
                // need to check if authentication is present in
                formList.put(DL_AUTH_REQUIRED, new FormDetails(result.errorMessage));

            } else {
                formList.put(DL_ERROR_MSG, new FormDetails(result.errorMessage));
            }
            return formList;
        }

        if (result.isOpenRosaResponse) {
            // Attempt OpenRosa 1.0 parsing
            Element xformsElement = result.doc.getRootElement();
            if (!xformsElement.getName().equals("xforms")) {
                String error = "root element is not <xforms> : " + xformsElement.getName();
                Log.e(t, "Parsing OpenRosa reply -- " + error);
                formList.put(
                        DL_ERROR_MSG,
                        new FormDetails(Collect.getInstance().getString(
                                R.string.parse_openrosa_formlist_failed, error)));
                return formList;
            }
            String namespace = xformsElement.getNamespace();
            if (!isXformsListNamespacedElement(xformsElement)) {
                String error = "root element namespace is incorrect:" + namespace;
                Log.e(t, "Parsing OpenRosa reply -- " + error);
                formList.put(
                        DL_ERROR_MSG,
                        new FormDetails(Collect.getInstance().getString(
                                R.string.parse_openrosa_formlist_failed, error)));
                return formList;
            }
            int nElements = xformsElement.getChildCount();
            for (int i = 0; i < nElements; ++i) {
                if (xformsElement.getType(i) != Element.ELEMENT) {
                    // e.g., whitespace (text)
                    continue;
                }
                Element xformElement = (Element) xformsElement.getElement(i);
                if (!isXformsListNamespacedElement(xformElement)) {
                    // someone else's extension?
                    continue;
                }
                String name = xformElement.getName();
                if (!name.equalsIgnoreCase("xform")) {
                    // someone else's extension?
                    continue;
                }

                // this is something we know how to interpret
                String formId = null;
                String formName = null;
                String version = null;
                String majorMinorVersion = null;
                String description = null;
                String downloadUrl = null;
                String manifestUrl = null;
                String md5hash = null;
                // don't process descriptionUrl
                int fieldCount = xformElement.getChildCount();
                for (int j = 0; j < fieldCount; ++j) {
                    if (xformElement.getType(j) != Element.ELEMENT) {
                        // whitespace
                        continue;
                    }
                    Element child = xformElement.getElement(j);
                    if (!isXformsListNamespacedElement(child)) {
                        // someone else's extension?
                        continue;
                    }
                    String tag = child.getName();
                    if (tag.equals("formID")) {
                        formId = XFormParser.getXMLText(child, true);
                        if (formId != null && formId.length() == 0) {
                            formId = null;
                        }
                    } else if (tag.equals("name")) {
                        formName = XFormParser.getXMLText(child, true);
                        if (formName != null && formName.length() == 0) {
                            formName = null;
                        }
                    } else if (tag.equals("version")) {
                        version = XFormParser.getXMLText(child, true);
                        if (version != null && version.length() == 0) {
                            version = null;
                        }
                    } else if (tag.equals("majorMinorVersion")) {
                        majorMinorVersion = XFormParser.getXMLText(child, true);
                        if (majorMinorVersion != null && majorMinorVersion.length() == 0) {
                            majorMinorVersion = null;
                        }
                    } else if (tag.equals("descriptionText")) {
                        description = XFormParser.getXMLText(child, true);
                        if (description != null && description.length() == 0) {
                            description = null;
                        }
                    } else if (tag.equals("downloadUrl")) {
                        downloadUrl = XFormParser.getXMLText(child, true);
                        if (downloadUrl != null && downloadUrl.length() == 0) {
                            downloadUrl = null;
                        }
                    } else if (tag.equals("manifestUrl")) {
                        manifestUrl = XFormParser.getXMLText(child, true);
                        if (manifestUrl != null && manifestUrl.length() == 0) {
                            manifestUrl = null;
                        }
                    } else if (tag.equals("hash")) {
                        md5hash = XFormParser.getXMLText(child, true);
                        Log.wtf("found Hash", md5hash);
                        if (md5hash != null && md5hash.length() == 0) {
                            md5hash = null;
                        }
                    }
                }
                if (formId == null || downloadUrl == null || formName == null) {
                    String error =
                            "Forms list entry " + Integer.toString(i)
                                    + " is missing one or more tags: formId, name, or downloadUrl";
                    Log.e(t, "Parsing OpenRosa reply -- " + error);
                    formList.clear();
                    formList.put(
                            DL_ERROR_MSG,
                            new FormDetails(Collect.getInstance().getString(
                                    R.string.parse_openrosa_formlist_failed, error)));
                    return formList;
                }
                formList.put(formId, new FormDetails(formName, downloadUrl, manifestUrl, formId, (version != null) ? version : majorMinorVersion, md5hash));
            }
        } else {
            // Aggregate 0.9.x mode...
            // populate HashMap with form names and urls
            Element formsElement = result.doc.getRootElement();
            int formsCount = formsElement.getChildCount();
            String formId = null;
            for (int i = 0; i < formsCount; ++i) {
                if (formsElement.getType(i) != Element.ELEMENT) {
                    // whitespace
                    continue;
                }
                Element child = formsElement.getElement(i);
                String tag = child.getName();
                if (tag.equals("formID")) {
                    formId = XFormParser.getXMLText(child, true);
                    if (formId != null && formId.length() == 0) {
                        formId = null;
                    }
                }
                if (tag.equalsIgnoreCase("form")) {
                    String formName = XFormParser.getXMLText(child, true);
                    if (formName != null && formName.length() == 0) {
                        formName = null;
                    }
                    String downloadUrl = child.getAttributeValue(null, "url");
                    downloadUrl = downloadUrl.trim();
                    if (downloadUrl != null && downloadUrl.length() == 0) {
                        downloadUrl = null;
                    }
                    if (downloadUrl == null || formName == null) {
                        String error =
                                "Forms list entry " + Integer.toString(i)
                                        + " is missing form name or url attribute";
                        Log.e(t, "Parsing OpenRosa reply -- " + error);
                        formList.clear();
                        formList.put(
                                DL_ERROR_MSG,
                                new FormDetails(Collect.getInstance().getString(
                                        R.string.parse_legacy_formlist_failed, error)));
                        return formList;
                    }
                    formList.put(formName, new FormDetails(formName, downloadUrl, null, formId, null));

                    formId = null;
                }
            }
        }
        return formList;
    }


    @Override
    protected void onPostExecute(HashMap<String, FormDetails> value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getApplicationContext());
        SharedPreferences.Editor e = sharedPreferences.edit();

        if (value == null) {
            Log.wtf(t, "Formlist Downloading returned null.  That shouldn't happen");
            // A crazy stuff happened so we assume that update is not available
            e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false);
            e.commit();
            return;
        }

        if (value.containsKey(DownloadFormListTask.DL_AUTH_REQUIRED)) {
            Log.wtf(t, "Formlist Downloading Auth Required.");
            // need authorization and the user has not set it so we assume
            e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false);
            e.commit();
            return;

        } else if (value.containsKey(DownloadFormListTask.DL_ERROR_MSG)) {
            // Download failed
            Log.wtf(t, "Download Error");
            e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, false);
            e.commit();
            return;
        } else {
            // Everything worked. Clear the list and add the results.
            Log.wtf(t, "Works Now: Size of Result " + String.valueOf(value.size()));

            ArrayList<String> ids = new ArrayList<String>(value.keySet());
            for (int i = 0; i < value.size(); i++) {
                String formDetailsKey = ids.get(i);
                FormDetails details = value.get(formDetailsKey);
                String formid = details.formID;
                Log.wtf(formid,formid);
                //TODO query for this ID in db
                String[] selectionArgs = new String[]{formid};
                String selection = FormsProviderAPI.FormsColumns.JR_FORM_ID + "=?";
                Cursor cursor = Collect.getInstance().getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, null, selection, selectionArgs, null);
                Log.wtf("Cursor Count", String.valueOf(cursor.getCount()));
                if (cursor.getCount() > 0){
                    cursor.moveToFirst();
                    String md5hash = (details.mMd5Hash == null) ? "" : details.mMd5Hash;
                    if (md5hash != null){
                    Log.wtf(md5hash,"md5:"+cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.MD5_HASH)));
                    if (md5hash.equals("md5:"+cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.MD5_HASH)))){
                        Log.wtf(formid,"Same Old Same Old");
                    }else{
                        Log.wtf(formid, "Update Found");
                        e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE, true);
                        e.commit();
                        return;
                    }
                    }
                }


            }
            e.putBoolean(PreferencesActivity.KEY_UPDATE_AVAILABLE,false);
            e.commit();
        }
    }




}
