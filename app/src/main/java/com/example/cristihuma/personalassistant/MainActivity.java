package com.example.cristihuma.personalassistant;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ResolveInfo;
import android.provider.MediaStore;

import com.github.ybq.android.spinkit.style.FadingCircle;

import java.util.List;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class MainActivity extends Activity implements RecognitionListener {

    private static final String KWS_SEARCH = "wakeup";
    private static final String COMMANDS_SEARCH = "commands";
    private static final String DIGITS_SEARCH = "numbers";

    private static final String KEYPHRASE = "hello benjamin";

    private static final String CALL_COMMAND = "call someone";
    private static boolean call_command_used = false;
    private static final int REQUEST_CALL= 3;

    private static final String SEND_MESSAGE_COMMAND = "send a message";
    private static boolean send_message_command_used = false;

    private static final String RECORD_VIDEO_COMMAND = "take a video";
    private static final int REQUEST_VIDEO_CAPTURE = 2;

    private static ArrayList<String> number;
    HashMap<String, String> WORD_DIGITS = new HashMap<>();

    private static final int PERMISSIONS_REQUEST = 1;
    private final String[] PERMISSIONS =
            {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };


    private SpeechRecognizer recognizer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        initializeWordDigits();
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.caption_text)).setText(R.string.setting_up_message);
        if(!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS , PERMISSIONS_REQUEST);
            return;
        }
        ProgressBar progressBar = findViewById(R.id.spinKit);
        FadingCircle loader = new FadingCircle();
        progressBar.setIndeterminateDrawable(loader);

        new SetupTask(this).execute();
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;
        String text = hypothesis.getHypstr();

        if(send_message_command_used)
            sendMessageCommandFlow(hypothesis);
        if(call_command_used)
            callCommandFlow(hypothesis);
        switch (text) {
            case KEYPHRASE:
                switchSearch(COMMANDS_SEARCH);
                break;
            case CALL_COMMAND:
                executeCommand(DIGITS_SEARCH,CALL_COMMAND);
                break;
            case SEND_MESSAGE_COMMAND:
                executeCommand(DIGITS_SEARCH,SEND_MESSAGE_COMMAND);
                break;
            case RECORD_VIDEO_COMMAND:
                executeCommand(COMMANDS_SEARCH,RECORD_VIDEO_COMMAND);
                break;
            default:
                System.out.println(hypothesis.getHypstr());
                break;
        }
    }

    private void switchSearch(String searchName) {
        recognizer.stop();
        switch (searchName) {
            case KWS_SEARCH:
                recognizer.startListening(searchName);
                ((TextView) findViewById(R.id.caption_text)).setText(R.string.start_info);
                break;
            case COMMANDS_SEARCH:
                notifyUser();
                recognizer.startListening(searchName);
                ((TextView) findViewById(R.id.caption_text)).setText(R.string.welcome_message);
                break;
            default:
                System.out.println(R.string.unknown_search_error);
                break;
        }
    }

    private void executeCommand(String searchName,String command){
        recognizer.stop();
        switch (command) {
            case CALL_COMMAND:
                notifyUser();
                ((TextView) findViewById(R.id.caption_text)).setText(R.string.call_command);
                call_command_used = true;
                recognizer.startListening(searchName);
                number = new ArrayList<>(50);
                break;
            case SEND_MESSAGE_COMMAND:
                notifyUser();
                ((TextView) findViewById(R.id.caption_text)).setText(R.string.send_messsage_command);
                send_message_command_used = true;
                recognizer.startListening(searchName);
                number = new ArrayList<>(50);
                break;
            case RECORD_VIDEO_COMMAND:
                notifyUser();
                ((TextView) findViewById(R.id.caption_text)).setText(R.string.video_camera_command);
                videoCameraCommandFlow();
                break;
            default:
                System.out.println(R.string.unknown_search_error);
                break;
        }
    }

    // SEND MESSAGE COMMAND
    private void sendMessageCommandFlow(Hypothesis hypothesis ) {
        String phonenumberOrEnd = hypothesis.getHypstr();
        recognizer.stop();
        number.add(phonenumberOrEnd);
        Toast.makeText(getApplicationContext(), phonenumberOrEnd, Toast.LENGTH_SHORT).show();
        if(phonenumberOrEnd.contains("end")) {
            notifyUser();
            send_message_command_used = false;
            number.remove("end");
            StringBuilder phonenumber = new StringBuilder();
            for (String item : number) {
                if(WORD_DIGITS.get(item) !=  null)
                    phonenumber.append(WORD_DIGITS.get(item));
            }
            sendMessage(phonenumber.toString());
            switchSearch(KWS_SEARCH);
        }
        recognizer.startListening(DIGITS_SEARCH);
    }

    private void sendMessage(String phonenumber){
        String message = "Hi You got a message!";
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phonenumber, null, message, null, null);
        Toast.makeText(getApplicationContext(), "SMS sent to " + phonenumber,
                Toast.LENGTH_LONG).show();
    }

    // CALL COMMAND FLOW
     private void callCommandFlow(Hypothesis hypothesis ) {
        String phonenumberOrEnd = hypothesis.getHypstr();
        recognizer.stop();
        number.add(phonenumberOrEnd);
        Toast.makeText(getApplicationContext(), phonenumberOrEnd, Toast.LENGTH_SHORT).show();
        if(phonenumberOrEnd.contains("end")) {
            notifyUser();
            System.out.println(phonenumberOrEnd);
            call_command_used = false;
            number.remove("end");
            StringBuilder phonenumber = new StringBuilder();
            for (String item : number) {
                phonenumber.append(WORD_DIGITS.get(item));
            }
            callNumber(phonenumber.toString());
            switchSearch(KWS_SEARCH);
        }
        recognizer.startListening(DIGITS_SEARCH);
    }

    // if no mobile network, change Intent.ACTION_CALL to Intent.ACTION_DIAL
    @SuppressLint("MissingPermission")
    private void callNumber(String phonenumber){
        Uri number = Uri.parse("tel:" + phonenumber);
        Intent callIntent = new android.content.Intent(Intent.ACTION_DIAL, number);

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(callIntent, 0);
        boolean isIntentSafe = activities.size() > 0;

        if (isIntentSafe) {
            startActivityForResult(callIntent,REQUEST_CALL);
        }
        switchSearch(COMMANDS_SEARCH);
    }

    // VIDEO CAMERA FLOW
    private void videoCameraCommandFlow() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activityReference;
        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                ((TextView) activityReference.get().findViewById(R.id.caption_text))
                        .setText(R.string.setting_up_error);
            } else {
                activityReference.get().switchSearch(KWS_SEARCH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .getRecognizer();

        recognizer.addListener(this);

        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        File menuGrammar = new File(assetsDir, "commands.gram");
        recognizer.addGrammarSearch(COMMANDS_SEARCH, menuGrammar);

        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (recognizer != null) {
            recognizer.stop();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            Toast.makeText(getApplicationContext(), "Video has ben saved to camera!",
                    Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_CALL)
            Toast.makeText(getApplicationContext(),"Call finished",Toast.LENGTH_SHORT).show();
        recognizer.startListening(KWS_SEARCH);
        switchSearch(KWS_SEARCH);
    }

    private void initializeWordDigits(){
        String [] digitsAsWords = { "zero", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine"};
        for(int i=0;i<digitsAsWords.length;i++)
            WORD_DIGITS.put(digitsAsWords[i],Integer.toString(i));

    }

    private void notifyUser(){
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            System.out.println(hypothesis.getHypstr());
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onResume(){
        super.onResume();
    }
}


