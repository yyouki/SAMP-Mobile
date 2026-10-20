package com.samp.mobile.launcher.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.SplashActivity;
import com.samp.mobile.launcher.util.ButtonAnimator;
import com.samp.mobile.launcher.util.SharedPreferenceCore;
import com.samp.mobile.launcher.util.Util;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
@Obfuscate
public class SettingsFragment extends Fragment {

    Wini mWini = null;
    EditText mNickName;
    SwitchCompat mKeyboardSwitch;
    SwitchCompat mVoiceSwitch;
    SwitchCompat mModifySwitch;
    SwitchCompat mFPSSwitch;
    SwitchCompat mMonetSwitch;
    SeekBar mMessagesSeekBar;
    TextView mMessagesText;
    SeekBar mFPSSeekBar;
    TextView mFPSText;
    Button mAdvancedSettingsButton;

    String[] titles = {"0.3.7", "0.3.7-R1", "0.3.7-R3","0.3.7-R4","0.3.7-R5"};

    Spinner autoCompleteTextView;
    ArrayAdapter<String> adapter;


    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View view = layoutInflater.inflate(R.layout.fragment_settings, viewGroup, false);

        ((MainActivity)getActivity()).hideKeyboard(getActivity());

        mNickName = view.findViewById(R.id.settings_nickname);
        autoCompleteTextView = view.findViewById(R.id.spinner);
        mKeyboardSwitch = view.findViewById(R.id.keyboard_switch);
        mFPSSwitch = view.findViewById(R.id.fps_switch);
        mMonetSwitch = view.findViewById(R.id.monet_switch);
        mVoiceSwitch = view.findViewById(R.id.voice_switch);
        mModifySwitch = view.findViewById(R.id.modify_switch);
        mMessagesSeekBar = view.findViewById(R.id.messages_seekbar);
        mMessagesText = view.findViewById(R.id.messages_count);
        mFPSSeekBar = view.findViewById(R.id.fps_seekbar);
        mFPSText = view.findViewById(R.id.fps_count);
        mAdvancedSettingsButton = view.findViewById(R.id.advanced_settings_button);

        adapter = new ArrayAdapter<String>(getActivity(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, titles);
        adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item);
        autoCompleteTextView.setAdapter(adapter);

        File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
        try {
            mWini = new Wini(file);

            mNickName.setText(mWini.get("client", "name"));

            mWini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }

        autoCompleteTextView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "VERSION", position);
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if(file.exists()) {
                    try {
                        if(mWini != null) {
                            mWini.put("client", "version", titles[new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "VERSION")]);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mNickName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String text = charSequence.toString();
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if(file.exists()) {
                    try {
                        if(mWini != null) {
                            mWini.put("client", "name", text);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        mModifySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "MODIFIED_DATA", b);
            }
        });

        mKeyboardSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "ANDROID_KEYBOARD", b);
                try {
                    if(mWini != null) {
                        mWini.put("gui", "androidkeyboard", b);
                        mWini.store();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mVoiceSwitch.setOnCheckedChangeListener((compoundButton, enabled) -> {
            new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "VOICE_CHAT_ENABLED", enabled);
            storeIniValue("gui", "VoiceChatEnable", enabled);
        });

        mFPSSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "FPS_DISPLAY", b);
                try {
                    if(mWini != null) {
                        mWini.put("gui", "fps", b ? 1 : 0);
                        mWini.store();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mMonetSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "MLOADER", b);
            }
        });

        // perform seek bar change listener event used for getting the progress value
        mMessagesSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int realProgress = 0;
                switch(progress)
                {
                    case 0: {
                        realProgress = 6;
                        break;
                    }
                    case 1: {
                        realProgress = 9;
                        break;
                    }
                    case 2: {
                        realProgress = 12;
                        break;
                    }
                    case 3:{
                        realProgress = 15;
                        break;
                    }
                }
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "MESSAGE_COUNT", realProgress);
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if(file.exists()) {
                    try {
                        if(mWini != null) {
                            mWini.put("gui", "ChatMaxMessages", realProgress);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mMessagesText.setText(String.valueOf(realProgress));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // perform seek bar change listener event used for getting the progress value
        mFPSSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int realProgress = 0;
                switch(progress)
                {
                    case 0: {
                        realProgress = 30;
                        break;
                    }
                    case 1: {
                        realProgress = 60;
                        break;
                    }
                    case 2: {
                        realProgress = 90;
                        break;
                    }
                    case 3:{
                        realProgress = 120;
                        break;
                    }
                }
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "FPS_LIMIT", realProgress);
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if(file.exists()) {
                    try {
                        if(mWini != null) {
                            mWini.put("gui", "FPSLimit", realProgress);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mFPSText.setText(String.valueOf(realProgress));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });


        mAdvancedSettingsButton.setOnClickListener(v -> showAdvancedSettingsDialog());

        return view;
    }

    private void showAdvancedSettingsDialog() {
        if (mWini == null) {
            Toast.makeText(requireContext(), "Client settings are not available yet.", Toast.LENGTH_LONG).show();
            return;
        }

        final android.content.Context context = requireContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, dp(8), padding, dp(12));

        SwitchCompat autoAim = addToggle(content, "Auto aim", readIniBoolean("client", "autoaim", false));
        SwitchCompat voiceToggle = addToggle(content, "Toggle-to-talk (tap to start/stop)", readIniBoolean("gui", "VoiceChatToggle", false));
        SwitchCompat chatTimestamps = addToggle(content, "Show chat timestamps", readIniBoolean("gui", "ChatTimestamps", false));
        SwitchCompat fullscreen = addToggle(content, "Immersive fullscreen (hide system bars)",
                new SharedPreferenceCore().getBoolean(requireContext(), "FULLSCREEN_MODE", true));
        SwitchCompat autoReconnect = addToggle(content, "Automatically reconnect after connection loss",
                readIniBoolean("client", "autoreconnect", true));

        SeekSetting receivedVolume = addSeekSetting(content, "Incoming voice volume", 0, 100,
                readIniInt("gui", "VoiceSoundVolume", 100), "%");
        SeekSetting microphoneVolume = addSeekSetting(content, "Microphone volume", 0, 100,
                readIniInt("gui", "VoiceMicroVolume", 75), "%");
        SeekSetting fontSize = addSeekSetting(content, "Chat font size", 8, 72,
                Math.round(readIniFloat("gui", "FontSize", 30.0f)), " px");
        SeekSetting chatX = addSeekSetting(content, "Chat horizontal position", 0, 1920,
                readIniInt("gui", "ChatPosX", 325), "");
        SeekSetting chatY = addSeekSetting(content, "Chat vertical position", 0, 1080,
                readIniInt("gui", "ChatPosY", 25), "");
        SeekSetting chatWidth = addSeekSetting(content, "Chat width", 120, 1920,
                readIniInt("gui", "ChatSizeX", 1150), "");
        SeekSetting chatHeight = addSeekSetting(content, "Chat height", 36, 1080,
                readIniInt("gui", "ChatSizeY", 220), "");

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxDialogHeight = Math.min(dp(500), (int) (getResources().getDisplayMetrics().heightPixels * 0.68f));
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxDialogHeight));

        new AlertDialog.Builder(context)
                .setTitle("Advanced client settings")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try {
                        mWini.put("client", "autoaim", autoAim.isChecked());
                        mWini.put("gui", "VoiceChatToggle", voiceToggle.isChecked());
                        mWini.put("gui", "ChatTimestamps", chatTimestamps.isChecked());
                        mWini.put("gui", "Fullscreen", fullscreen.isChecked());
                        new SharedPreferenceCore().setBoolean(context, "FULLSCREEN_MODE", fullscreen.isChecked());
                        mWini.put("client", "autoreconnect", autoReconnect.isChecked());
                        mWini.put("gui", "VoiceSoundVolume", receivedVolume.getValue());
                        mWini.put("gui", "VoiceMicroVolume", microphoneVolume.getValue());
                        mWini.put("gui", "FontSize", fontSize.getValue());
                        mWini.put("gui", "ChatPosX", chatX.getValue());
                        mWini.put("gui", "ChatPosY", chatY.getValue());
                        mWini.put("gui", "ChatSizeX", chatWidth.getValue());
                        mWini.put("gui", "ChatSizeY", chatHeight.getValue());
                        mWini.store();
                        Toast.makeText(context, "Saved. Restart the game to apply native client settings.", Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e("SettingsFragment", "Could not save advanced client settings", e);
                        Toast.makeText(context, "Could not save client settings.", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private SwitchCompat addToggle(LinearLayout parent, String label, boolean checked) {
        SwitchCompat toggle = new SwitchCompat(requireContext());
        toggle.setText(label);
        toggle.setTextColor(Color.DKGRAY);
        toggle.setTextSize(14);
        toggle.setChecked(checked);
        parent.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return toggle;
    }

    private SeekSetting addSeekSetting(LinearLayout parent, String label, int min, int max, int initial, String suffix) {
        TextView valueLabel = new TextView(requireContext());
        valueLabel.setTextColor(Color.DKGRAY);
        valueLabel.setTextSize(13);
        SeekBar seekBar = new SeekBar(requireContext());
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(min, Math.min(max, initial)) - min);
        Runnable updateLabel = () -> valueLabel.setText(label + ": " + (seekBar.getProgress() + min) + suffix);
        updateLabel.run();
        seekBar.setContentDescription(label);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateLabel.run();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(8);
        parent.addView(valueLabel, labelParams);
        parent.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        return new SeekSetting(seekBar, min);
    }

    private String readIniString(String section, String key, String defaultValue) {
        if (mWini == null) {
            return defaultValue;
        }
        String value = mWini.get(section, key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private boolean readIniBoolean(String section, String key, boolean defaultValue) {
        String value = readIniString(section, key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value) || "1".equals(value);
    }

    private int readIniInt(String section, String key, int defaultValue) {
        try {
            // Existing client settings include integral values serialized as decimals, e.g. 325.0.
            return (int) Float.parseFloat(readIniString(section, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private float readIniFloat(String section, String key, float defaultValue) {
        try {
            return Float.parseFloat(readIniString(section, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void storeIniValue(String section, String key, Object value) {
        if (mWini == null) return;
        try {
            mWini.put(section, key, value);
            mWini.store();
        } catch (IOException e) {
            Log.e("SettingsFragment", "Could not save " + section + "." + key, e);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SeekSetting {
        private final SeekBar seekBar;
        private final int minValue;

        private SeekSetting(SeekBar seekBar, int minValue) {
            this.seekBar = seekBar;
            this.minValue = minValue;
        }

        private int getValue() {
            return seekBar.getProgress() + minValue;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mKeyboardSwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "ANDROID_KEYBOARD"));
        mVoiceSwitch.setChecked(readIniBoolean("gui", "VoiceChatEnable", true));
        mFPSSwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "FPS_DISPLAY"));
        mModifySwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "MODIFIED_DATA"));
        mMonetSwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "MLOADER"));
        autoCompleteTextView.setSelection(new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "VERSION"));

        // Defaults must match the seek-bar choices on a fresh install.
        SharedPreferenceCore preferences = new SharedPreferenceCore();
        int fps = preferences.getInt(requireContext(), "FPS_LIMIT", 60);
        switch (fps)
        {
            case 30: mFPSSeekBar.setProgress(0); break;
            case 60: mFPSSeekBar.setProgress(1); break;
            case 90: mFPSSeekBar.setProgress(2); break;
            case 120: mFPSSeekBar.setProgress(3); break;
            default:
                fps = 60;
                preferences.setInt(requireContext(), "FPS_LIMIT", fps);
                mFPSSeekBar.setProgress(1);
                break;
        }
        mFPSText.setText(String.valueOf(fps));

        int message = preferences.getInt(requireContext(), "MESSAGE_COUNT", 9);
        switch (message)
        {
            case 6: mMessagesSeekBar.setProgress(0); break;
            case 9: mMessagesSeekBar.setProgress(1); break;
            case 12: mMessagesSeekBar.setProgress(2); break;
            case 15: mMessagesSeekBar.setProgress(3); break;
            default:
                message = 9;
                preferences.setInt(requireContext(), "MESSAGE_COUNT", message);
                mMessagesSeekBar.setProgress(1);
                break;
        }
        mMessagesText.setText(String.valueOf(message));
    }
}
