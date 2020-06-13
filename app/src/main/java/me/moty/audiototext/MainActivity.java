package me.moty.audiototext;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;

public class MainActivity extends AppCompatActivity {
    // Replace below with your own subscription key
    private static final String SpeechSubscriptionKey = "***";
    // Replace below with your own service region (e.g., "westus").
    private static final String SpeechRegion = "westus2";
    private SpeechSynthesizer synthesizer;

    private TextView recognizedTextView;
    private EditText textBox;
    private Button recognizeButton;
    private boolean isConverting = false;
    private MicrophoneStream microphoneStream;
    private MicrophoneStream createMicrophoneStream() {
        if (microphoneStream != null) {
            microphoneStream.close();
            microphoneStream = null;
        }

        microphoneStream = new MicrophoneStream();
        return microphoneStream;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recognizeButton = findViewById(R.id.button);
        Button toAudioButton = findViewById(R.id.button2);
        recognizedTextView = findViewById(R.id.textView);
        textBox = findViewById(R.id.editText);
        recognizedTextView.setMovementMethod(new ScrollingMovementMethod());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            // a unique number within the application to allow
            // correlating permission request responses with the request.
            int permissionRequestId = 5;

            // Request permissions needed for speech recognition
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, INTERNET, READ_EXTERNAL_STORAGE}, permissionRequestId);
        }
        catch(Exception ex) {
            recognizedTextView.setText("初始化失敗: " + ex.toString());
        }
        final SpeechConfig speechConfig;
        try {
            speechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
            speechConfig.setSpeechRecognitionLanguage("zh-TW");
            speechConfig.setSpeechSynthesisLanguage("zh-TW");
            speechConfig.setSpeechSynthesisVoiceName("zh-TW-Yating-Apollo");
            synthesizer = new SpeechSynthesizer(speechConfig);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            displayException(ex);
            return;
        }
        toAudioButton.setOnClickListener(view -> {
            if(!isConverting) {
                if(textBox.getText().toString().trim().length() > 0) {
                        // Note: this will block the UI thread, so eventually, you want to register for the event
                        SpeechSynthesisResult result = synthesizer.SpeakText(textBox.getText().toString());
                        assert (result != null);
                        if (result.getReason() == ResultReason.Canceled) {
                            String cancellationDetails =
                                    SpeechSynthesisCancellationDetails.fromResult(result).toString();
                            Toast.makeText(getApplicationContext(), "Error synthesizing. Error detail: " +
                                    System.lineSeparator() + cancellationDetails +
                                    System.lineSeparator() + "Did you update the subscription info?", Toast.LENGTH_LONG).show();
                        }
                        result.close();
                } else {
                    Toast.makeText(getApplicationContext(), "請輸入欲輸出的文字.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "請先停止語音轉文字的功能.", Toast.LENGTH_LONG).show();
            }
        });
        recognizeButton.setOnClickListener(new View.OnClickListener() {
            private boolean continuousListeningStarted = false;
            private SpeechRecognizer reco = null;
            private String buttonText = "";
            private ArrayList<String> content = new ArrayList<>();

            @Override
            public void onClick(final View view) {
                final Button clickedButton = (Button) view;
                disableButtons();
                if (continuousListeningStarted) {
                    if (reco != null) {
                        final Future<Void> task = reco.stopContinuousRecognitionAsync();
                        setOnTaskCompletedListener(task, result -> {
                            MainActivity.this.runOnUiThread(() -> clickedButton.setText(buttonText));
                            enableButtons();
                            continuousListeningStarted = false;
                            MainActivity.this.isConverting = false;
                        });
                    } else {
                        continuousListeningStarted = false;
                        MainActivity.this.isConverting = false;
                    }

                    return;
                }

                clearTextBox();

                try {
                    content.clear();

                    // audioInput = AudioConfig.fromDefaultMicrophoneInput();
                    AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                    reco = new SpeechRecognizer(speechConfig, audioInput);

                    reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                        content.remove(content.size() - 1);
                    });

                    reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                    });

                    final Future<Void> task = reco.startContinuousRecognitionAsync();
                    setOnTaskCompletedListener(task, result -> {
                        continuousListeningStarted = true;
                        MainActivity.this.isConverting = true;
                        MainActivity.this.runOnUiThread(() -> {
                            buttonText = clickedButton.getText().toString();
                            clickedButton.setText("停止");
                            clickedButton.setEnabled(true);
                        });
                    });
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    displayException(ex);
                }
            }
        });
    }
    @SuppressLint("SetTextI18n")
    private void displayException(Exception ex) {
        recognizedTextView.setText(ex.getMessage() + System.lineSeparator() + TextUtils.join(System.lineSeparator(), ex.getStackTrace()));
    }

    private void clearTextBox() {
        AppendTextLine("");
    }

    private void setRecognizedText(final String s) {
        AppendTextLine(s);
    }

    @SuppressLint("SetTextI18n")
    private void AppendTextLine(final String s) {
        MainActivity.this.runOnUiThread(() -> recognizedTextView.setText(s));
    }

    private void disableButtons() {
        MainActivity.this.runOnUiThread(() -> recognizeButton.setEnabled(false));
    }

    private void enableButtons() {
        MainActivity.this.runOnUiThread(() -> recognizeButton.setEnabled(true));
    }
    private <T> void setOnTaskCompletedListener(Future<T> task, OnTaskCompletedListener<T> listener) {
        s_executorService.submit(() -> {
            T result = task.get();
            listener.onCompleted(result);
            return null;
        });
    }

    private interface OnTaskCompletedListener<T> {
        void onCompleted(T taskResult);
    }
    private static ExecutorService s_executorService;
    static {
        s_executorService = Executors.newCachedThreadPool();
    }
}
