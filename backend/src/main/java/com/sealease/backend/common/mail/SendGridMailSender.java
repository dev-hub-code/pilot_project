package com.sealease.backend.common.mail;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link org.springframework.mail.javamail.JavaMailSender} that delivers through SendGrid's Web API
 * (https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send) instead of SMTP. The senders still build
 * ordinary MIME messages; each is translated to one {@code POST /v3/mail/send}: the addresses, subject, the plain-text
 * and HTML bodies, and every other part (such as the inline logo) as an attachment. Click and open tracking are off,
 * so verification and reset links reach the user unchanged.
 */
public class SendGridMailSender extends JavaMailSenderImpl {

	private static final Logger log = LoggerFactory.getLogger(SendGridMailSender.class);

	private final RestClient client;

	public SendGridMailSender(SendGridProperties properties) {
		this(properties, RestClient.builder().requestFactory(requestFactory(properties)));
	}

	SendGridMailSender(SendGridProperties properties, RestClient.Builder builder) {
		setDefaultEncoding("UTF-8");
		this.client = builder.baseUrl(properties.baseUrl().toString())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
			.build();
	}

	private static JdkClientHttpRequestFactory requestFactory(SendGridProperties properties) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		return requests;
	}

	@Override
	protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
		Map<Object, Exception> failed = new LinkedHashMap<>();
		for (int i = 0; i < mimeMessages.length; i++) {
			Object original = originalMessages != null ? originalMessages[i] : mimeMessages[i];
			try {
				send(request(mimeMessages[i]));
			}
			catch (MailSendException ex) {
				failed.put(original, ex);
			}
			catch (MessagingException | IOException | RuntimeException ex) {
				failed.put(original, new MailParseException("Could not read the email to send", ex));
			}
		}
		if (!failed.isEmpty()) {
			throw new MailSendException(failed);
		}
	}

	private void send(Map<String, Object> request) {
		try {
			var response = client.post()
				.uri("/v3/mail/send")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toBodilessEntity();
			log.debug("SendGrid accepted the email: {}", response.getHeaders().getFirst("X-Message-Id"));
		}
		catch (RestClientResponseException ex) {
			throw new MailSendException("SendGrid refused the email (HTTP " + ex.getStatusCode().value() + "): "
					+ ex.getResponseBodyAsString(), ex);
		}
		catch (RestClientException ex) {
			throw new MailSendException("Could not reach SendGrid", ex);
		}
	}

	/** The mail/send request for one MIME message. */
	static Map<String, Object> request(MimeMessage mime) throws MessagingException, IOException {
		// Fixes the headers (Content-Type and so on) from the content, as an SMTP transport does before sending.
		mime.saveChanges();
		Map<String, Object> personalization = new LinkedHashMap<>();
		putAddresses(personalization, "to", mime.getRecipients(Message.RecipientType.TO));
		putAddresses(personalization, "cc", mime.getRecipients(Message.RecipientType.CC));
		putAddresses(personalization, "bcc", mime.getRecipients(Message.RecipientType.BCC));
		if (!personalization.containsKey("to")) {
			throw new MessagingException("The email has no recipient");
		}
		Address[] from = mime.getFrom();
		if (from == null || from.length == 0) {
			throw new MessagingException("The email has no sender");
		}

		Body body = new Body();
		body.collect(mime);
		List<Map<String, String>> content = new ArrayList<>();
		// SendGrid wants text/plain before text/html.
		if (body.text != null) {
			content.add(Map.of("type", "text/plain", "value", body.text));
		}
		if (body.html != null) {
			content.add(Map.of("type", "text/html", "value", body.html));
		}
		if (content.isEmpty()) {
			throw new MessagingException("The email has no text");
		}

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("personalizations", List.of(personalization));
		request.put("from", address(from[0]));
		Address[] replyTo = mime.getReplyTo();
		if (replyTo != null && replyTo.length > 0 && !replyTo[0].equals(from[0])) {
			request.put("reply_to", address(replyTo[0]));
		}
		request.put("subject", mime.getSubject() == null ? "" : mime.getSubject());
		request.put("content", content);
		if (!body.attachments.isEmpty()) {
			request.put("attachments", body.attachments);
		}
		request.put("tracking_settings", Map.of(
				"click_tracking", Map.of("enable", false, "enable_text", false),
				"open_tracking", Map.of("enable", false)));
		return request;
	}

	private static void putAddresses(Map<String, Object> target, String key, Address[] addresses) {
		if (addresses == null || addresses.length == 0) {
			return;
		}
		List<Map<String, String>> list = new ArrayList<>();
		for (Address a : addresses) {
			list.add(address(a));
		}
		target.put(key, list);
	}

	private static Map<String, String> address(Address address) {
		Map<String, String> out = new LinkedHashMap<>();
		if (address instanceof InternetAddress internet) {
			out.put("email", internet.getAddress());
			if (internet.getPersonal() != null && !internet.getPersonal().isBlank()) {
				out.put("name", internet.getPersonal());
			}
		}
		else {
			out.put("email", address.toString());
		}
		return out;
	}

	/** Walks the MIME tree: the first plain-text and HTML parts are the body; every other leaf is an attachment. */
	private static final class Body {

		private String text;
		private String html;
		private final List<Map<String, String>> attachments = new ArrayList<>();

		void collect(Part part) throws MessagingException, IOException {
			if (part.getContent() instanceof Multipart multipart) {
				for (int i = 0; i < multipart.getCount(); i++) {
					BodyPart child = multipart.getBodyPart(i);
					collect(child);
				}
				return;
			}
			boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
			if (!attachment && text == null && part.isMimeType("text/plain")) {
				text = (String) part.getContent();
			}
			else if (!attachment && html == null && part.isMimeType("text/html")) {
				html = (String) part.getContent();
			}
			else {
				attachments.add(attachment(part));
			}
		}

		private static Map<String, String> attachment(Part part) throws MessagingException, IOException {
			byte[] bytes;
			try (InputStream in = part.getInputStream()) {
				bytes = in.readAllBytes();
			}
			String type = part.getContentType().split(";", 2)[0].strip();
			String contentId = part instanceof MimePart mime && mime.getContentID() != null
					? mime.getContentID().replaceAll("^<|>$", "") : null;
			String filename = part.getFileName() != null ? part.getFileName()
					: contentId != null ? contentId : "attachment";
			Map<String, String> out = new LinkedHashMap<>();
			out.put("content", Base64.getEncoder().encodeToString(bytes));
			out.put("type", type);
			out.put("filename", filename);
			if (contentId != null && !Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
				out.put("disposition", "inline");
				out.put("content_id", contentId);
			}
			else {
				out.put("disposition", "attachment");
			}
			return out;
		}

	}

}
