package de.teammeet.interfaces;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.packet.Message;

public interface IInvitationHandler {
	void handleInvitation(Connection connection,
						  String room,
						  String inviter,
						  String reason,
						  String password,
						  Message message);
}
