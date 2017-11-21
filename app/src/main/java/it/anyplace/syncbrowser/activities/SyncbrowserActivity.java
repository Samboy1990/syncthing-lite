package it.anyplace.syncbrowser.activities;

import android.app.AlertDialog;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Date;

import it.anyplace.sync.bep.FolderBrowser;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.configuration.ConfigurationService;
import it.anyplace.syncbrowser.utils.LibraryHandler;
import it.anyplace.syncbrowser.R;
import it.anyplace.syncbrowser.utils.UpdateIndexTask;
import it.anyplace.syncbrowser.databinding.DialogLoadingBinding;

public abstract class SyncbrowserActivity extends AppCompatActivity {

    private static final String TAG = "SyncbrowserActivity";

    private static int mActivityCount = 0;
    private static LibraryHandler mLibraryHandler;

    private AlertDialog mLoadingDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivityCount++;
        if (mLibraryHandler == null) {
            initLibrary();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mActivityCount--;
        new Thread(() -> {
            if (mActivityCount == 0) {
                mLibraryHandler.destroy();
                mLibraryHandler = null;
            }
        }).start();
    }

    private void initLibrary() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                onLoadingLibrary();
            }

            @Override
            protected Void doInBackground(Void... voidd) {
                mLibraryHandler = new LibraryHandler();
                mLibraryHandler.init(SyncbrowserActivity.this);
                return null;
            }

            @Override
            protected void onPostExecute(Void voidd) {
                mLoadingDialog.cancel();
                mLibraryHandler.setOnIndexUpdatedListener(new LibraryHandler.OnIndexUpdatedListener() {
                    @Override
                    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
                        onIndexUpdateProgress(folder, percentage);
                    }

                    @Override
                    public void onIndexUpdateComplete() {
                        onIndexUpdateComplete();
                    }
                });
                onLibraryLoaded();
            }
        }.execute();
    }

    public SyncthingClient getSyncthingClient() {
        return mLibraryHandler.getSyncthingClient();
    }

    public ConfigurationService getConfiguration() {
        return mLibraryHandler.getConfiguration();
    }

    public FolderBrowser getFolderBrowser() {
        return mLibraryHandler.getFolderBrowser();
    }

    @SuppressWarnings("unused")
    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
    }

    @SuppressWarnings("unused")
    public void onIndexUpdateComplete() {
    }

    public void onLoadingLibrary() {
        DialogLoadingBinding binding = DataBindingUtil.inflate(
                getLayoutInflater(), R.layout.dialog_loading, null, false);
        binding.loadingText.setText("loading config, starting syncthing client");
        mLoadingDialog = new android.app.AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.getRoot())
                .show();
    }

    public void onLibraryLoaded() {
        Date lastUpdate;
        long lastUpdateMillis = PreferenceManager.getDefaultSharedPreferences(this)
                .getLong(UpdateIndexTask.LAST_INDEX_UPDATE_TS_PREF, -1);
        if (lastUpdateMillis < 0) {
            lastUpdate = null;
        } else {
            lastUpdate = new Date(lastUpdateMillis);
        }
        //trigger update if last was more than 10mins ago
        if (lastUpdate == null || new Date().getTime() - lastUpdate.getTime() > 10 * 60 * 1000) {
            Log.d(TAG, "trigger index update, last was " + lastUpdate);
            new UpdateIndexTask(this, getSyncthingClient()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
}