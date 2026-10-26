package com.sealease.backend.support;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * An email a mocked {@link JavaMailSender} was asked to send. The senders pass it a {@link MimeMessagePreparator}
 * ({@code BrandedEmail}); this prepares a real message from it and reads its parts.
 */
public record SentMail(List<String> to, String from, String subject, String text, String html) {

	/** Every email sent through the mock, oldest first. */
	public static List<SentMail> all(JavaMailSender mail) {
		ArgumentCaptor<MimeMessagePreparator> sent = ArgumentCaptor.forClass(MimeMessagePreparator.class);
		verify(mail, atLeastOnce()).send(sent.capture());
		return sent.getAllValues().stream().map(SentMail::of).toList();
	}

	/** The last email sent to {@code address}. */
	public static SentMail lastTo(JavaMailSender mail, String address) {
		return all(mail).stream().filter(m -> m.to().contains(address)).toList().getLast();
	}

	public static SentMail of(MimeMessagePreparator preparator) {
		try {
			MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
			preparator.prepare(message);
			message.saveChanges();
			String[] parts = new String[2];
			collect(message, parts);
			return new SentMail(addresses(message.getRecipients(Message.RecipientType.TO)),
					addresses(message.getFrom()).getFirst(), message.getSubject(), parts[0], parts[1]);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not read the email", ex);
		}
	}

	private static void collect(Part part, String[] parts) throws MessagingException, IOException {
		Object content = part.getContent();
		if (content instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				collect(multipart.getBodyPart(i), parts);
			}
		}
		else if (part.isMimeType("text/plain") && parts[0] == null) {
			parts[0] = (String) content;
		}
		else if (part.isMimeType("text/html") && parts[1] == null) {
			parts[1] = (String) content;
		}
	}

	private static List<String> addresses(Address[] addresses) {
		return addresses == null ? List.of() : Arrays.stream(addresses).map(Address::toString).toList();
	}

}
