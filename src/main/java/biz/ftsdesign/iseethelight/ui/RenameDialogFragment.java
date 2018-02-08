package biz.ftsdesign.iseethelight.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;

import com.ftstrading.iseethelight.R;

import biz.ftsdesign.iseethelight.PersistentLightState;

public class RenameDialogFragment extends DialogFragment {
    private PersistentLightState persistentLightState;
    private LightNameChangedListener listener;

    public void setPersistentLightState(PersistentLightState persistentLightState) {
        this.persistentLightState = persistentLightState;
    }

    public void setLightNameChangedListener(LightNameChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final EditText editText = new EditText(getActivity());
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setMaxLines(1);
        editText.setText(persistentLightState.getGivenName());

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder
                .setView(editText)
                .setTitle(R.string.dialog_rename)
                .setMessage(String.format(getString(R.string.name_light_as), persistentLightState.getName()))
                .setPositiveButton(R.string.button_rename, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String newGivenName = editText.getText().toString();
                        if (!newGivenName.equals(persistentLightState.getGivenName())) {
                            if (newGivenName.trim().isEmpty()) {
                                newGivenName = persistentLightState.getName();
                            }
                            persistentLightState.setGivenName(newGivenName);
                            if (listener != null) {
                                listener.onLightNameChanged(persistentLightState.getName(), newGivenName);
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });
        return builder.create();
    }
}
