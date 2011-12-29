package thelab.o2.co.uk.tapontag;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class TapOnTagActivity extends Activity {

	private static final String TAG = "TapO2Tag";

	private static final String OPERATION_LAUNCH_BROWSER = "1";
	private static final String OPERATION_PHONE_CALL = "2";
	private static final String OPERATION_SEND_SMS = "3";

	/**
	 * Resumed true or false
	 */
	private boolean mResumed = false;

	/**
	 * tag write mode true or false
	 */
	private boolean mWriteMode = false;

	NfcAdapter mNfcAdapter;
	EditText tagNote;

	PendingIntent mNfcPendingIntent;
	IntentFilter[] mWriteTagFilters;
	IntentFilter[] mNdefExchangeFilters;
	private Button writeTagBt;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

		setContentView(R.layout.main);

		writeTagBt = (Button) findViewById(R.id.write_tag);
		writeTagBt.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// Write to a tag for as long as the dialog is shown.
				disableNdefExchangeMode();
				enableTagWriteMode();

				new AlertDialog.Builder(TapOnTagActivity.this).setTitle("Touch tag to write")
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						disableTagWriteMode();
						enableNdefExchangeMode();
					}
				}).create().show();

			}
		});

		tagNote = ((EditText) findViewById(R.id.note));

		tagNote.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

			}

			@Override
			public void afterTextChanged(Editable arg0) {
				if (mResumed) {
					mNfcAdapter.enableForegroundNdefPush(TapOnTagActivity.this, getNoteAsNdef());
				}
			}
		});

		// Handle all of our received NFC intents in this activity.
		mNfcPendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		// Intent filters for reading a note from a tag or exchanging over p2p.
		IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndefDetected.addDataType("text/plain");
		} catch (MalformedMimeTypeException e) { }
		mNdefExchangeFilters = new IntentFilter[] { ndefDetected };

		// Intent filters for writing to a tag
		IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
		mWriteTagFilters = new IntentFilter[] { tagDetected };
	}

	@Override
	protected void onResume() {

		super.onResume();
		mResumed = true;

		Log.v(TAG, " Intent Action received: "+getIntent().getAction());

		// Sticky notes received from Android        
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
			NdefMessage[] messages = getNdefMessages(getIntent());
			byte[] payload = messages[0].getRecords()[0].getPayload();
			setNoteBody(new String(payload));
			setIntent(new Intent()); // Consume this intent.            
			parseOperation(new String(payload));
			//  finish();
		}

		enableNdefExchangeMode();
	}

	private void parseOperation(String text) {
		String[] string = text.split("#");
		if (string[0].startsWith(OPERATION_LAUNCH_BROWSER)){
			openWebBrowser(string[1]);
		}
		else if (string[0].startsWith(OPERATION_PHONE_CALL)){
			makeAPhoneCall(string[1]);
		}		
		else if (string[0].startsWith(OPERATION_SEND_SMS)){
			sendSMS(string[1]);
		}


	}



	@Override
	protected void onPause() {
		super.onPause();
		mResumed = false;

		mNfcAdapter.disableForegroundNdefPush(this);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// NDEF exchange mode
		if (!mWriteMode && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			NdefMessage[] msgs = getNdefMessages(intent);
			promptForContent(msgs[0]);
		}

		// Tag writing mode
		if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			writeTag(getNoteAsNdef(), detectedTag);
		}
	}


	private void promptForContent(final NdefMessage msg) {

		new AlertDialog.Builder(this).setTitle("Replace current content?")
		.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				String body = new String(msg.getRecords()[0].getPayload());
				setNoteBody(body);
			}
		})
		.setNegativeButton("No", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {

			}
		}).show();
	}

	private void setNoteBody(String body) {
		Log.v(TAG, " Tag text: "+body);
		tagNote.getText().clear();
		tagNote.getText().append(body);
	}

	private NdefMessage getNoteAsNdef() {
		byte[] textBytes = tagNote.getText().toString().getBytes();
		NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "text/plain".getBytes(),
				new byte[] {}, textBytes);
		return new NdefMessage(new NdefRecord[] {
				textRecord
		});
	}

	NdefMessage[] getNdefMessages(Intent intent) {
		// Parse the intent
		NdefMessage[] msgs = null;
		String action = intent.getAction();
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
			} else {
				// Unknown tag type
				byte[] empty = new byte[] {};
				NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
				NdefMessage msg = new NdefMessage(new NdefRecord[] {
						record
				});
				msgs = new NdefMessage[] {
						msg
				};
			}
		} else {
			Log.d(TAG, "Unknown intent.");
			finish();
		}
		return msgs;
	}

	private void enableNdefExchangeMode() {

		Log.v(TAG, " Enable NDEF Exchange Mode ");
		mNfcAdapter.enableForegroundNdefPush(TapOnTagActivity.this, getNoteAsNdef());
		mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mNdefExchangeFilters, null);
	}

	private void disableNdefExchangeMode() {
		mNfcAdapter.disableForegroundNdefPush(this);
		mNfcAdapter.disableForegroundDispatch(this);
	}

	private void enableTagWriteMode() {
		mWriteMode = true;
		IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
		mWriteTagFilters = new IntentFilter[] {
				tagDetected
		};
		mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);
	}

	private void disableTagWriteMode() {
		mWriteMode = false;
		mNfcAdapter.disableForegroundDispatch(this);
	}

	boolean writeTag(NdefMessage message, Tag tag) {

		Log.v(TAG, "Writing the TAG!!!");

		int size = message.toByteArray().length;

		try {
			Ndef ndef = Ndef.get(tag);
			if (ndef != null) {
				ndef.connect();

				if (!ndef.isWritable()) {
					toast("Tag is read-only.");
					return false;
				}
				if (ndef.getMaxSize() < size) {
					toast("Tag capacity is " + ndef.getMaxSize() + " bytes, message is " + size
							+ " bytes.");
					return false;
				}

				ndef.writeNdefMessage(message);
				Log.v(TAG, "Writing DONE!!!");
				toast("Wrote message to pre-formatted tag.");
				return true;
			} else {
				NdefFormatable format = NdefFormatable.get(tag);
				if (format != null) {
					try {
						format.connect();
						format.format(message);
						toast("Formatted tag and wrote message");
						return true;
					} catch (IOException e) {
						toast("Failed to format tag.");
						return false;
					}
				} else {
					toast("Tag doesn't support NDEF.");
					return false;
				}
			}
		} catch (Exception e) {
			toast("Failed to write tag");
		}

		return false;
	}

	private void toast(String text) {
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}

	private void openWebBrowser(String url){
		Uri uriUrl = Uri.parse(url);
		Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl); 
		startActivity(launchBrowser);
	}
	
	private void makeAPhoneCall(String number) {
		try {
			Intent callIntent = new Intent(Intent.ACTION_CALL);
			callIntent.setData(Uri.parse("tel:"+number));
			startActivity(callIntent);
		} catch (ActivityNotFoundException e) {
			Log.e("helloandroid dialing example", "Call failed", e);
		}

	}	
	
	
	private void sendSMS(String number) {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"
	            + number)));


	}
}