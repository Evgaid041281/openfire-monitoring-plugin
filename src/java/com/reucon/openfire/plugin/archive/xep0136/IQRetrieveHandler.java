package com.reucon.openfire.plugin.archive.xep0136;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.util.XmppDateUtil;
import com.reucon.openfire.plugin.archive.xep.AbstractIQHandler;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.Element;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.List;

/**
 * Message Archiving Retrieve Handler.
 */
public class IQRetrieveHandler extends AbstractIQHandler {

    private static final Logger Log = LoggerFactory.getLogger( IQRetrieveHandler.class );
    private static final String NAMESPACE = "urn:xmpp:archive";

    public IQRetrieveHandler() {
        super("Message Archiving Retrieve Handler", "retrieve", NAMESPACE);
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        final IQ reply = IQ.createResultIQ(packet);
        final RetrieveRequest retrieveRequest = new RetrieveRequest(
                packet.getChildElement());
        int fromIndex; // inclusive
        int toIndex; // exclusive
        int max;

        Log.debug( "Processing a request to retrieve a conversation. Requestor: "+packet.getFrom()+", with: {}, start: {}", retrieveRequest.getWith(), retrieveRequest.getStart() );
        final Conversation conversation = retrieve(packet.getFrom(),
                retrieveRequest);

        if (conversation == null) {
            Log.debug( "Unable to find conversation." );
            return error(packet, PacketError.Condition.item_not_found);
        }

        final Element chatElement = reply.setChildElement("chat", NAMESPACE);
        chatElement.addAttribute("with", conversation.getWithBareJid().toString());
        chatElement.addAttribute("start",
                XmppDateUtil.formatDate(conversation.getStart()));

        max = conversation.getMessages().size();
        fromIndex = 0;
        toIndex = max > 0 ? max : 0;
        Log.debug( "Found conversation with {} messages.", max );

        final XmppResultSet resultSet = retrieveRequest.getResultSet();
        if (resultSet != null) {
            if (resultSet.getMax() != null && resultSet.getMax() <= max) {
                max = resultSet.getMax();
                toIndex = fromIndex + max;
            }

            if (resultSet.getIndex() != null) {
                fromIndex = resultSet.getIndex();
                toIndex = fromIndex + max;
            } else if (resultSet.getAfter() != null) {
                fromIndex = Long.valueOf(resultSet.getAfter()).intValue() + 1;
                toIndex = fromIndex + max;
            } else if (resultSet.getBefore() != null) {
                if (Long.valueOf(resultSet.getBefore())!=Long.MAX_VALUE)
                                toIndex = Long.valueOf(resultSet.getBefore()).intValue();
                        else
                                toIndex = conversation.getMessages().size();
                fromIndex = toIndex - max;
            }
        }
        fromIndex = fromIndex < 0 ? 0 : fromIndex;
        toIndex = toIndex > conversation.getMessages().size() ? conversation
                .getMessages().size() : toIndex;
        toIndex = toIndex < fromIndex ? fromIndex : toIndex;

        final List<ArchivedMessage> messages = conversation.getMessages()
                .subList(fromIndex, toIndex);
        for (int i = 0; i < messages.size(); i++) {
            if (i == 0) {
                addMessageElement(chatElement, conversation, messages.get(i), null);
            } else {
                addMessageElement(chatElement, conversation, messages.get(i), messages.get(i - 1));
            }
        }

        if (resultSet != null) {
            if (messages.size() > 0) {
                        resultSet.setFirst(String.valueOf(fromIndex));
                        resultSet.setFirstIndex(fromIndex);
                        resultSet.setLast(String.valueOf(toIndex - 1));
                    }
                    resultSet.setCount(conversation.getMessages().size());
                    chatElement.add(resultSet.createResultElement());
        }

        return reply;
    }

    private Conversation retrieve(JID from, RetrieveRequest request) {
        return getPersistenceManager(from).getConversation(from.asBareJID(),
                request.getWith(), request.getStart());
    }

    private Element addMessageElement(Element parentElement,
            Conversation conversation, ArchivedMessage message, ArchivedMessage previousMessage) {
        final Element messageElement;
        
        final long secs;
        if (previousMessage == null) {
            secs = (message.getTime().getTime() - conversation.getStart().getTime()) / 1000;
        } else {
            secs = (message.getTime().getTime() - previousMessage.getTime().getTime()) / 1000;
        }
        
        messageElement = parentElement.addElement(message.getDirection()
                .toString());
        messageElement.addAttribute("secs", Long.toString(secs));
        if (message.getWith() != null) {
            messageElement.addAttribute("jid", message.getWith().toBareJID());
        }
        messageElement.addElement("body").setText(message.getBody());

        return messageElement;
    }
}
