/**
 *    Copyright 2012 Daniel Kreischer, Christopher Holm, Christopher Schwardt
 *
 *    This file is part of TeamMeet.
 *
 *    TeamMeet is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    TeamMeet is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with TeamMeet.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package de.teammeet;

import org.jivesoftware.smack.XMPPException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import de.teammeet.helper.ToastDisposerSingleton;
import de.teammeet.tasks.ConnectTask;
import de.teammeet.tasks.CreateGroupTask;
import de.teammeet.tasks.DisconnectTask;
import de.teammeet.tasks.InviteTask;
import de.teammeet.xmpp.XMPPService;

public class MainActivity extends Activity {

	private String CLASS = MainActivity.class.getSimpleName();

	public static final String TYPE = "type";
	public static final String ROOM = "room";
	public static final String INVITER = "inviter";
	public static final String REASON = "reason";
	public static final String PASSWORD = "password";
	public static final String FROM = "from";

	public static final int TYPE_NONE = 0;
	public static final int TYPE_JOIN = 1;

	private XMPPService mXMPPService = null;

	private ToastDisposerSingleton mToastSingleton = null;
	private SharedPreferences mSettings = null;

	private ServiceConnection mServiceConnection = new XMPPServiceConnection();

	private class XMPPServiceConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			Log.d(CLASS, "MainActivity.ServiceConnection.onServiceConnected('" + className + "')");
			mXMPPService = ((XMPPService.LocalBinder) binder).getService();
			handleIntent(getIntent());
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.d(CLASS, "MainActivity.ServiceConnection.onServiceDisconnected('" + className +
					"')");
			mXMPPService = null;
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mToastSingleton = ToastDisposerSingleton.getInstance(getApplicationContext());
		mSettings = getSharedPreferences(SettingsActivity.PREFS_NAME, 0);

		Button b;

		b = (Button) findViewById(R.id.buttonShowMap);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View arg0) {
				startMapActivity();
			}
		});

		b = (Button) findViewById(R.id.buttonShowRoster);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View arg0) {
				startRosterActivity();
			}
		});

		b = (Button) findViewById(R.id.buttonShowSettings);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View arg0) {
				startSettingsActivity();
			}
		});

		b = (Button) findViewById(R.id.buttonConnect);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View arg0) {
				connectToXMPP();
			}
		});

		b = (Button) findViewById(R.id.buttonCreate);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View arg0) {
				String groupName = mSettings.getString(SettingsActivity.SETTING_XMPP_GROUP_NAME,
				                                       "");
				String conferenceServer = mSettings.getString(
				        SettingsActivity.SETTING_XMPP_CONFERENCE_SERVER, "");
				if (groupName.equals("") || conferenceServer.equals("")) {
					mToastSingleton.showError("You need to configure a group name and a " +
											  "conference server in the settings");
				} else {
					createGroup(groupName, conferenceServer);
				}
			}
		});

		b = (Button) findViewById(R.id.buttonInvite);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View arg0) {
				String groupName = mSettings.getString(SettingsActivity.SETTING_XMPP_GROUP_NAME,
				                                       "");
				String contact = mSettings.getString(
				        SettingsActivity.SETTING_XMPP_CONTACT_TO_INVITE, "");
				if (groupName.equals("") || contact.equals("")) {
					mToastSingleton.showError("You need to configure a group name and a contact " +
											  "to invite in the settings");
				} else {
					inviteMate(contact, groupName);
				}
			}
		});

		// If the user has not yet configured his XMPP settings lead the way
		if (mSettings.getString(SettingsActivity.SETTING_XMPP_USER_ID, "").equals("") ||
				mSettings.getString(SettingsActivity.SETTING_XMPP_SERVER, "").equals("") ||
				mSettings.getString(SettingsActivity.SETTING_XMPP_PASSWORD, "").equals("")) {
			mToastSingleton.show("Please configure your XMPP Account.");
			Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
			startActivity(settingsIntent);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// create the service (if it isn't already running
		final Intent intent = new Intent(getApplicationContext(), XMPPService.class);
		startService(intent);

		// now connect to the service
		final boolean bindSuccess = bindService(intent, mServiceConnection, 0);
		if (bindSuccess) {
			Log.d(CLASS, "bind to XMPP service succeeded");
		} else {
			Log.e(CLASS, "bind to XMPP service failed");
			mToastSingleton.showError("Couldn't connect to XMPP service.");
			this.finish();
		}
	}

	private void handleIntent(Intent intent) {
		Log.d(CLASS, "MainActivity.handleIntent()");
		final int type = intent.getIntExtra(TYPE, TYPE_NONE);
		intent.removeExtra(TYPE);
		switch (type) {
		case TYPE_JOIN:
			Log.d(CLASS, "Intent to join a group");
			handleJoinIntent(intent);
			break;
		default:
			Log.d(CLASS, "Intent of unknown type");
			break;
		}
	}

	private void handleJoinIntent(Intent intent) {
		final String room = intent.getStringExtra(ROOM);
		final String inviter = intent.getStringExtra(INVITER);
		final String reason = intent.getStringExtra(REASON);
		final String password = intent.getStringExtra(PASSWORD);
		final String from = intent.getStringExtra(FROM);
		// cleanup the extras so that this is only executed once, not every time the activity is
		// brought to foreground again
		intent.removeExtra(ROOM);
		intent.removeExtra(INVITER);
		intent.removeExtra(REASON);
		intent.removeExtra(PASSWORD);
		intent.removeExtra(FROM);
		Log.d(CLASS, String.format("room: '%s' inviter: '%s' reason: '%s' password: '%s' from: '%s'", room, inviter, reason, password, from));
		if (room != null && inviter != null && reason != null && from != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Group Invitation");
			builder.setMessage(String.format("%s wants you to join '%s':\n%s", from, room, reason));
			builder.setCancelable(false);
			builder.setPositiveButton("Join", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.dismiss();
			                SharedPreferences settings =
			                		getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
			                final String userID =
			                		settings.getString(SettingsActivity.SETTING_XMPP_USER_ID,
			                		                   "anonymous");
			                try {
								mXMPPService.joinRoom(room, userID, password);
							} catch (XMPPException e) {
								e.printStackTrace();
								Log.e(CLASS, "Unable to join room.");
								// TODO show the user
							}
			           }
			       });
			builder.setNegativeButton("Decline", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.dismiss();
			           }
			       });
			final AlertDialog alert = builder.create();
			alert.show();
		} else {
			Log.e(CLASS, "Cannot handle invite: Missing parameters.");
		}
	}

	@Override
	protected void onPause() {
		if (mServiceConnection != null) {
			unbindService(mServiceConnection);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	protected void startMapActivity() {
		final Intent intent = new Intent(MainActivity.this, TeamMeetActivity.class);
		startActivity(intent);
	}

	protected void startRosterActivity() {
		final Intent intent = new Intent(MainActivity.this, RosterActivity.class);
		startActivity(intent);
	}

	protected void startSettingsActivity() {
		final Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
		startActivity(intent);
	}

	protected void connectToXMPP() {
		Log.d(CLASS, "MainActivity.connectToXMPP()");
		Button connectButton = (Button) findViewById(R.id.buttonConnect);

		if (!mXMPPService.isAuthenticated()) {
			new ConnectTask(mXMPPService, connectButton).execute();
		} else {
			new DisconnectTask(mXMPPService, connectButton).execute();
		}
	}

	private void createGroup(String groupName, String conferenceServer) {
		Log.d(CLASS, "MainActivity.createGroup()");
		Button createButton = (Button) findViewById(R.id.buttonCreate);

		new CreateGroupTask(mXMPPService, createButton).execute(groupName, conferenceServer);
	}

	private void inviteMate(String contact, String group) {
		Log.d(CLASS, "MainActivity.inviteMate()");
		Button inviteButton = (Button) findViewById(R.id.buttonInvite);

		new InviteTask(mXMPPService, inviteButton).execute(contact, group);
	}
}