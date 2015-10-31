package thibautperrin.tetrano.packageSavedGame;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;

public class ListePartiesEnregistreesActivity extends Activity {

    private View buttonRename;
    private View buttonWatch;
    private View buttonDelete;
    private DAOSavedGame dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liste_parties_enregistrees);
        ListView listView = (ListView) findViewById(R.id.listView);
        ApplicationTetrano app = (ApplicationTetrano) getApplication();
        dao = app.getOpenedDAOSavedGame();
        ArrayList<String> strings = dao.getSavedGameNames();
        if (strings.size() == 0) {
            displayDialogNoSavedGame();
        }
        final MyArrayAdapter adapter = new MyArrayAdapter(this, strings);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapter.select(position);
            }
        });
        buttonWatch = findViewById(R.id.buttonWatch);
        buttonDelete = findViewById(R.id.buttonDelete);
        buttonRename = findViewById(R.id.buttonRename);
        View.OnClickListener buttonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.onClick(v);
            }
        };
        buttonWatch.setOnClickListener(buttonClickListener);
        buttonDelete.setOnClickListener(buttonClickListener);
        buttonRename.setOnClickListener(buttonClickListener);
    }

    private void displayDialogNoSavedGame() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.Sentence_No_Saved_Game));
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                ListePartiesEnregistreesActivity.this.finish();
            }
        });
        dialog.show();
    }

    private void lancerSuite(String gameName) {
        Intent activiteeSavedGame = new Intent(ListePartiesEnregistreesActivity.this, WatchSavedGameActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(StaticFields.LABEL_GAME_NAME, gameName);
        activiteeSavedGame.putExtras(bundle);
        startActivityForResult(activiteeSavedGame, StaticFields.REQUEST_CODE_WATCH_SAVEED_GAME);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == StaticFields.RESULT_CODE_LEAVE) {
            finish();
        }
    }

    private class MyArrayAdapter extends ArrayAdapter<String> {
        private final Context context;
        private ArrayList<String> strings;
        private int itemSelected = -1;
        private AlertDialog dialogConfirmDelete = null;
        private AlertDialog dialogRename = null;

        public MyArrayAdapter(Context context, ArrayList<String> strings) {
            super(context, R.layout.text_view_basic, strings);
            this.context = context;
            this.strings = strings;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = new TextView(context);
            textView.setText(strings.get(position));
            textView.setTextSize(getResources().getDimension(R.dimen.Taille_Corps));
            if (position == itemSelected) {
                textView.setTextColor(Color.BLACK);
                textView.setBackgroundColor(StaticFields.COLOR_VERT_CLAIR);
            } else {
                textView.setTextColor(StaticFields.COLOR_VERT_CLAIR);
                textView.setBackgroundColor(Color.BLACK);
            }
            return textView;
        }

        public void select(int numero) {
            if (itemSelected == numero) {
                itemSelected = -1;
            } else if (0 <= numero && numero < strings.size()) {
                itemSelected = numero;
            }
            notifyDataSetChanged();
        }

        public void onClick(View v) {
            if (itemSelected != -1) {
                if (v == buttonWatch) {
                    lancerSuite(strings.get(itemSelected));
                } else if (v == buttonDelete) {
                    if (dialogConfirmDelete == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setMessage(getResources().getString(R.string.Sentence_Confirm_Delete_Game));
                        builder.setPositiveButton(getResources().getString(R.string.Confirm), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dao.delete(strings.get(itemSelected));
                                strings.remove(itemSelected);
                                itemSelected = -1;
                                notifyDataSetChanged();
                            }
                        });
                        builder.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                        builder.setCancelable(true);
                        dialogConfirmDelete = builder.create();
                    }
                    dialogConfirmDelete.show();
                } else if (v == buttonRename) {
                    if (dialogRename == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        final EditText edittext = new EditText(context);
                        builder.setMessage(getResources().getString(R.string.Enter_A_Name));
                        builder.setTitle(getResources().getString(R.string.Rename_A_Saved_Game));
                        builder.setView(edittext);
                        edittext.setText(strings.get(itemSelected));
                        builder.setPositiveButton(getResources().getString(R.string.Confirm), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String editTextValue = edittext.getText().toString();
                                dao.rename(strings.get(itemSelected), editTextValue);
                                strings.remove(itemSelected);
                                strings.add(itemSelected, editTextValue);
                                itemSelected = -1;
                                notifyDataSetChanged();
                            }
                        });

                        builder.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        });
                        builder.setCancelable(true);
                        dialogRename = builder.create();
                    }
                    dialogRename.show();
                }
            }
        }
    }
}
