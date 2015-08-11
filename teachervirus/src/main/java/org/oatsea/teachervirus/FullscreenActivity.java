package org.oatsea.teachervirus;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Credits:
// https://gist.github.com/rduplain/2638913
// https://developer.chrome.com/multidevice/webview/gettingstarted

// ** TO DO ***
// 0. Download getinfected.php from github and unzip into /mnt/sdcard/htdocs
//  - with option to download from alternative address if unable to access github
// 1. Start Webserver (DroidPHP) via INTENT?? (and confirm success)
// 2. Test can access web pages on local host
// 3. Provide network IP address of this device so can infect


public class FullscreenActivity extends Activity{

    private WebView myWebView;

    // Progress Dialog
    private ProgressDialog pDialog;
    public static final int progress_bar_type = 0;

    private boolean debugFlag = true;

    private boolean tvInstalled = false;
    private boolean webserverUP = false;

    private static String file_url = "https://www.github.com/OATSEA/getinfected/zipball/master";  // File url to download - github
    private static String getinfected_url = "http://localhost:8080/getinfected.php"; // getinfected installer
    private static String play_url = "http://localhost:8080/play"; // default play location to go to if infected already
    private static String splash_url = "file:///android_asset/www/splash.html"; // splash page
    private static String error_url = "file:///android_asset/www/error.html"; // error page

    private static String htdocs = Environment.getExternalStorageDirectory() + File.separator + "htdocs";
    private static String up = htdocs + File.separator + "play" + File.separator + "up.html";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        // Initialise Webview
        myWebView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        myWebView.setWebViewClient(new WebViewClient());

        // Hide Everything but Web Page:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {

            // Hide the bottom bar and prevent it from reactivating
            // Requires minimym of 11
            // Source: http://stackoverflow.com/questions/11027193/maintaining-lights-out-mode-view-setsystemuivisibility-across-restarts
            final View rootView = getWindow().getDecorView();
            // rootView.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);

            // New Immersive config: https://developer.android.com/training/system-ui/immersive.htm
            rootView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);

            rootView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if (visibility == View.SYSTEM_UI_FLAG_VISIBLE) {
                        /* rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
                        rootView.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
                        */
                        // New Immersive config: https://developer.android.com/training/system-ui/immersive.html

                    rootView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

                    }
                }
            });
        } // END Hide Everything

        openPage(splash_url);

        if (checkCanPlay()) {
            openPage(play_url);
        } else {
            if (getinfected()) {
                // Do nothing as we now need to wait for all the various steps to complete
                // getinfected returning true doesn't mean success, just that it hasn't failed yet.
                // Thus need to leave final action/result to steps in process
                // hence splash page needs to be showing on main thread

                // ** TO DO ** add option to cancel install along way?

            } else {
                sayThis("Infection Failed!!!");
                openPage(error_url);
            }
        }

    } // END onCreate

    public boolean checkCanPlay() {
        // Returns true if TV installed and Webserver Is up

        tvInstalled = isTVInstalled();
        webserverUP = isWebServerUP();

        if ((tvInstalled)&&(webserverUP)) {
            return true;
        } else{
            return false;
        }


    } // END checkCanPlay

    public boolean isTVInstalled() {
        // Check htdocs/play/up.html exists
        // if it doesn't then return false (TV not installed)

        File upcheck = new File(up);
        if (upcheck.isFile()) {
            if (debugFlag) {Log.i("teachervirus", "up.html exists so TV is installed");}
            return true;
        } else {
            if (debugFlag) {   Log.i("teachervirus", "up.html DOESN'T exists so TV is NOT installed");}
            return false;
        } // END isFile check

    } // END isTVInstalled


    public boolean isWebServerUP() {
        // if can access localhost play/up.html then server is up and working (and installed)
        // call on curl or something - look at getinfected download?
        return true;



    } // END isWebServerUP()

    public boolean getinfected() {
        // return true if infection successful
        boolean infectedOK = false; // Installation failed by default

        // Advise user installing
        sayThis("Commence Infection!!");

        if (checkHtdocsOK()) {
            installTV();
            infectedOK = true;
        } else {
            infectedOK = false;
        }

        return infectedOK;
    }


    public boolean checkHtdocsOK() {
        // Check HTDOCS folder exists or not
        // handle issue where for some reason DroidPHP creates it as a file sometimes
        boolean htdocsOK = false; // htdocs not okay by default

        File check_htdocs = new File(htdocs);
        if (check_htdocs.isDirectory()) {
            if (debugFlag) {
                Log.i("teachervirus", "htdocs folder exists on SDcard");
            }
            htdocsOK = true;
            // HTDOCS folder all good
        } else {
            if (check_htdocs.isFile()) {
                if (debugFlag) {
                    Log.i("teachervirus", "htdocs is a file - not a folder so DELETE IT!");
                }
                // delete file version of htdocs
                if (check_htdocs.delete()) {
                    // deleted successfully
                    if (debugFlag) {
                        Log.i("teachervirus", "htdocs deleted");
                    }
                    // create new htdocs folder
                    File htdocs_new = new File(htdocs);

                    // as was able to delete file create new directory:
                    if (htdocs_new.mkdir()) {
                        if (debugFlag) {
                            Log.i("teachervirus", "htdocs created successfully");
                        }
                        htdocsOK = true;
                    } else {
                        if (debugFlag) {
                            Log.i("teachervirus", "htdocs NOT created!");
                        }
                        sayThis("Unable to create htdocs folder - Install Failed");

                    } // End make new dir
                } else {
                    if (debugFlag) {
                        Log.i("teachervirus", "htdocs NOT deleted!");
                    }
                    sayThis("Unable to delete htdocs file - Install Failed");

                } // END delete

            } else {
                // htdocs is not a file and doesn't exist
                // so create it as a folder
                if (debugFlag) {
                    Log.i("teachervirus", "htdocs DOESN'T exist - create it");
                }
                if (check_htdocs.mkdir()) {
                    if (debugFlag) {
                        Log.i("teachervirus", "htdocs created successfully");
                    }
                    htdocsOK = true;
                } else {
                    if (debugFlag) {
                        Log.i("teachervirus", "htdocs NOT created!");
                    }
                    sayThis("Unable to create htdocs folder - Install Failed");
                } // htdocs mkdir


            }
        }   // END check for HTDOCS

        return htdocsOK;
    } // END checkHtdocsOK

    public void openPage(String url) {

        // myWebView.loadUrl("http://www.google.com");
        // "http://localhost:8080/getinfected.php"
        myWebView.loadUrl(url);

    }

    @Override
    public void onResume(){
        super.onResume();

        // Hide Everything:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            View rootView = getWindow().getDecorView();
            rootView.setSystemUiVisibility(View.STATUS_BAR_VISIBLE);
            rootView.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
            rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }

        if (checkCanPlay()) {
            openPage(play_url);

            // openPage(error_url);
            // dismissDialog(progress_bar_type);
        } else {
            // getinfected();
            // install possibly in progress already
        }

    } // END onResume

    public void installTV() {

        // check getinfected.zip exists or not
        // if getinfected.zip doesn't exist then:


        // But has getinfected.php.zip already been downloaded?
        File getinfectedzip = new File(htdocs + "/infect/getinfected.php.zip");
        if (getinfectedzip.isFile()) {
            // getinfected zip file already exists
            if (debugFlag) {Log.i("teachervirus", "Teacher Virus already downloaded - just unzip");}

            // check if getinfected.php already exists or not
            File getinfectedphp = new File(Environment.getExternalStorageDirectory() + "/htdocs/getinfected.php");
            if (getinfectedphp.isFile()) {
                // getinfected.php already exists
                if (debugFlag) {Log.i("teachervirus", "getinfected.php already exists so don't unzip");}

                // which suggests a previous install failed
                sayThis("Looks like a previous install was tried? Attempting to continue");
                // as getinfected.php exists start it to do install process

                openPage(getinfected_url);

            } else {
                    // getinfected.php.zip exists but hasn't been unzipped so unzip it
                    if (debugFlag) {Log.i("teachervirus", "Attempt to unzip");}

                    // attempt to unzip
                    new UnzipFile().execute("/htdocs/infect/getinfected.php.zip");
                    if (debugFlag) {Log.i("teachervirus", "executed unzip");}
                    // doesn't mean unzip was a success
            } // END getinfectedphp is file

        } else {
            // no existing getinfected zip file so download it
            if (debugFlag) {Log.i("teachervirus", "getinfected.php.zip doesn't exist so download it . . . ");}

            new DownloadFileFromURL().execute(file_url);
            if (debugFlag) {Log.i("teachervirus", "downloadFile executed");}
        }

    } // END installTV

    public void sayThis(String messageText) {
        Context context = getApplicationContext();
        CharSequence text = messageText;
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    } // END sayInstalling
    /**
     * Showing Dialog
     * */

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case progress_bar_type: // we set this to 0
                pDialog = new ProgressDialog(this);
                pDialog.setMessage("Downloading getinfected - Please wait...");
                pDialog.setIndeterminate(false);
                pDialog.setMax(100);
                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setCancelable(true);
                pDialog.show();
                return pDialog;
            default:
                return null;
        }
    }

    /**
     * Background Async Task to download file
     * */
    class DownloadFileFromURL extends AsyncTask<String, String, String> {

        /**
         * Before starting background thread Show Progress Bar Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(progress_bar_type);
        }

        /**
         * Downloading file in background thread
         * */
        @Override
        protected String doInBackground(String... f_url) {
            int count;
            try {
                URL url = new URL(f_url[0]);
                URLConnection connection = url.openConnection();
                connection.connect();

                // this will be useful so that you can show a typical 0-100%
                // progress bar
                int lengthOfFile = connection.getContentLength();

                // download the file
                InputStream input = new BufferedInputStream(url.openStream(),
                        8192);

                // Create folder if doesn't exist:
                // http://stackoverflow.com/questions/2130932/how-to-create-directory-automatically-on-sd-card

                File infect = new File(htdocs+ "/infect/");

                // create directories
                infect.mkdirs();


                // Output stream
                OutputStream output = new FileOutputStream(htdocs+ "/infect/getinfected.php.zip");

                byte data[] = new byte[1024];

                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    // publishing the progress....
                    // After this onProgressUpdate will be called
                    publishProgress("" + (int) ((total * 100) / lengthOfFile));

                    // writing data to file
                    output.write(data, 0, count);
                }

                // flushing output
                output.flush();

                // closing streams
                output.close();
                input.close();

            } catch (Exception e) {
                Log.e("Error: ", e.getMessage());
                openPage(error_url);
                sayThis("Download Failed");
                // ** TO DO **
                // at this point try to download from alternate location
            }

            return null;
        }

        /**
         * Updating progress bar
         * */
        protected void onProgressUpdate(String... progress) {
            // setting progress percentage
            pDialog.setProgress(Integer.parseInt(progress[0]));
        }

        /**
         * After completing background task Dismiss the progress dialog
         * **/
        @Override
        protected void onPostExecute(String file_url) {
            // dismiss the dialog after the file was downloaded
            dismissDialog(progress_bar_type);
            if(debugFlag) {Log.i("teachervirus", "Download Complete . . . ");}

            File getinfectedcheck = new File(htdocs + "/infect/getinfected.php.zip");
            if(getinfectedcheck.isFile()) {
                if(debugFlag) {Log.i("teachervirus", "and getinfected.php.zip exists");}
                new UnzipFile().execute("/htdocs/infect/getinfected.php.zip");
            } else {
                if(debugFlag) {Log.i("teachervirus", "BUT getinfected.php.zip DOESN'T exist!");}
                openPage(error_url);
                sayThis("Unable Downloaded getinfected.php.zip");
            }


        }

    } // END DownloadFile

    // UNZIP getinfected

    /**
     * Background Async Task to unzip file
     * */
    class UnzipFile extends AsyncTask<String, String, String> {

        /**
         * Before starting background thread Show Progress Bar Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // showDialog(progress_bar_type);
        }

        /**
         * Unzip file in background thread
         * */
        @Override
        protected String doInBackground(String... unzipFileName) {
            int count;
            try {
                if(debugFlag) {Log.i("teachervirus", "try to unzip file:" + unzipFileName);}

                String unziploc = htdocs+"/infect/getinfected.php.zip";


                File zipFile = new File(unziploc);

                File targetDirectory = new File(htdocs+"/unzip_temp");
                // Reference: http://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android
                unzip(zipFile, targetDirectory);


            } catch (Exception e) {
                Log.e("Error: ", e.getMessage());
            }

            return null;
        }

        /**
         * Updating progress bar
         * */
        protected void onProgressUpdate(String... progress) {
            // setting progress percentage
            // pDialog.setProgress(Integer.parseInt(progress[0]));
        }

        /**
         * After completing background task Dismiss the progress dialog
         * **/
        @Override
        protected void onPostExecute(String file_url) {
            // dismiss the dialog after the file was downloaded

            if(debugFlag) {Log.i("teachervirus", "Unzip Complete . . . ");}

            new MoveFile().execute("/htdocs/unzip_temp");

        }

    } // END unzip CLASS

    // Reference: http://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }


    // MOVE CLASS

    // Move unzipped file to root of htdocs and delete unzip_temp folder
    class MoveFile extends AsyncTask<String, String, String> {

        /**
         * Before starting background thread Show Progress Bar Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        /**
         * Move file in background thread
         * */
        @Override
        protected String doInBackground(String... moveTo) {
            int count;
            try {
                if(debugFlag) {Log.i("teachervirus", "try to move file:" + moveTo);}

                // determine subfolder name (it is the only folder in the unzip_temp folder

                // REFERENCE: http://stackoverflow.com/questions/6997364/android-how-to-get-directory-listing
                // get a list of all folders in /htdocs/unzip_temp - there should be only one
                String subfolder = htdocs+"/unzip_temp"; // to handle situation where no subfolder
                File f = new File(htdocs+"/unzip_temp");
                File[] files = f.listFiles();
                for (File inFile : files) {
                    if (inFile.isDirectory()) {
                        subfolder = inFile.toString();
                        if(debugFlag) {Log.i("teachervirus", "subfolder set to:" + subfolder);}

                    }
                } // End For

                // REFERENCE: http://stackoverflow.com/questions/9065514/move-rename-file-in-sd-card

                // Copy file:
                File original = new File(subfolder+"/getinfected.php");
                File destination = new File(htdocs+"/getinfected.php");

                // sayThis("Attempting to Move Files into Place");
                if(debugFlag) {Log.i("teachervirus", "Attempt to copy" + original.toString() + " to " + destination.toString());}

                copy(original,destination);


                // delete unzip_temp folder
                // REFERENCE: http://stackoverflow.com/questions/5701586/delete-a-folder-on-sd-card

                File unzip_temp = new File(htdocs + "/unzip_temp");

                if(debugFlag) {Log.i("teachervirus", "delete directory" + unzip_temp.toString());}
                deleteDirectory(unzip_temp);


            } catch (Exception e) {
                Log.e("Error: ", e.getMessage());
            }

            return null;
        }

        /**
         * After completing background task
         * **/
        @Override
        protected void onPostExecute(String file_url) {


            if(debugFlag) {Log.i("teachervirus", "Move Complete . . . ");}

            File getinfectedphptest = new File(htdocs+"/getinfected.php");
            if(getinfectedphptest.isFile()) {
                if(debugFlag) {Log.i("teachervirus", "and getinfected.php exists ");}
                // open the webview that goes to this file:

                openPage(getinfected_url);
            } else {
                if(debugFlag) {Log.i("teachervirus", "BUT getinfected.php DOESN'T exist!");}
                openPage(error_url);
                sayThis("File Move Failed!");
            }

        }

    } // END Move Class

    // REFERENCE: http://stackoverflow.com/questions/9292954/how-to-make-a-copy-of-a-file-in-android

    public void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    // REFERENCE: http://stackoverflow.com/questions/5701586/delete-a-folder-on-sd-card

    public static boolean deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            if (files == null) {
                return true;
            }
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        return( path.delete() );
    }

    // Make the back button go "back" in browser rather than back to launcher
    @Override
    public void onBackPressed() {

        if(myWebView.canGoBack()) {
                myWebView.goBack();
        } else {
                super.onBackPressed();
        }
    } // END onBackPressed

} // END CLASS FullScreenActivity

