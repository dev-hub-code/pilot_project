package com.sealease.backend.common.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * An email in the SeaLease layout, written once and sent as both plain text and HTML: a white card on the site's
 * warm background with the logo, a gold rule, and the content blocks in order. The logo travels inside the email
 * (an inline image), so mail apps that block remote images still show it. The HTML sticks to what mail apps render:
 * tables, inline styles, a 560 px column.
 *
 * <pre>
 * BrandedEmail.builder("123456 is your SeaLease verification code", appUrl)
 *     .preheader("Your code is 123456")
 *     .greeting("Asha")
 *     .paragraph("Thanks for signing up to SeaLease.")
 *     .code("123456")
 *     .button("Confirm my email", link)
 *     .build()
 *     .message(from, to)
 * </pre>
 */
public final class BrandedEmail {

	private static final String LOGO_CID = "sealease-logo";
	private static final Resource LOGO = new ClassPathResource("mail/logo.png");

	// The site's palette (frontend/src/app/globals.css, light theme).
	private static final String CANVAS = "#f6f5f2";
	private static final String SURFACE = "#ffffff";
	private static final String INK = "#121212";
	private static final String MUTED = "#66625b";
	private static final String BORDER = "#e3e1dc";
	private static final String GOLD = "#c9a24c";
	private static final String ON_GOLD = "#17130b";
	private static final String GOLD_TEXT = "#876521";
	private static final String FONT = "'Inter','Segoe UI',Helvetica,Arial,sans-serif";
	private static final String MONO = "'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace";

	private sealed interface Block permits Paragraph, Code, Button, Details, Note {
	}

	private record Paragraph(String text) implements Block {
	}

	private record Code(String code) implements Block {
	}

	private record Button(String label, String url) implements Block {
	}

	private record Details(Map<String, String> rows) implements Block {
	}

	private record Note(String text) implements Block {
	}

	private final String subject;
	private final String siteUrl;
	private final String preheader;
	private final String greeting;
	private final List<Block> blocks;
	private final String footer;
	private final String footerLinkLabel;
	private final String footerLinkUrl;

	private BrandedEmail(Builder b) {
		this.subject = b.subject;
		this.siteUrl = b.siteUrl;
		this.preheader = b.preheader;
		this.greeting = b.greeting;
		this.blocks = List.copyOf(b.blocks);
		this.footer = b.footer;
		this.footerLinkLabel = b.footerLinkLabel;
		this.footerLinkUrl = b.footerLinkUrl;
	}

	/**
	 * @param subject the subject line
	 * @param siteUrl the web app's public address, linked from the footer
	 */
	public static Builder builder(String subject, String siteUrl) {
		return new Builder(subject, siteUrl);
	}

	public String subject() {
		return subject;
	}

	/** The email for {@link org.springframework.mail.javamail.JavaMailSender#send(MimeMessagePreparator)}. */
	public MimeMessagePreparator message(String from, String to) {
		return (MimeMessage mime) -> {
			MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(text(), html());
			helper.addInline(LOGO_CID, LOGO, "image/png");
		};
	}

	/** The plain-text version, for mail apps that do not show HTML. */
	public String text() {
		StringBuilder out = new StringBuilder();
		if (greeting != null) {
			out.append(greeting).append("\n\n");
		}
		for (Block block : blocks) {
			switch (block) {
				case Paragraph p -> out.append(p.text()).append("\n\n");
				case Code c -> out.append("    ").append(c.code()).append("\n\n");
				case Button b -> out.append(b.label()).append(": ").append(b.url()).append("\n\n");
				case Details d -> {
					d.rows().forEach((label, value) -> out.append(label).append(": ").append(value).append('\n'));
					out.append('\n');
				}
				case Note n -> out.append(n.text()).append("\n\n");
			}
		}
		out.append("-- \nSeaLease\n").append(siteUrl).append('\n');
		if (footer != null) {
			out.append('\n').append(footer);
			if (footerLinkUrl != null) {
				out.append(' ').append(footerLinkUrl);
			}
			out.append('\n');
		}
		return out.toString();
	}

	/** The HTML version. */
	public String html() {
		StringBuilder body = new StringBuilder();
		if (greeting != null) {
			body.append(paragraphHtml(greeting));
		}
		for (Block block : blocks) {
			body.append(switch (block) {
				case Paragraph p -> paragraphHtml(p.text());
				case Code c -> codeHtml(c.code());
				case Button b -> buttonHtml(b.label(), b.url());
				case Details d -> detailsHtml(d.rows());
				case Note n -> """
						<p style="margin:24px 0 0;font-size:13px;line-height:20px;color:%s;">%s</p>
						""".formatted(MUTED, lines(n.text()));
			});
		}
		String footerHtml = footer == null ? "" : """
				<p style="margin:12px 0 0;">%s%s</p>
				""".formatted(htmlEscape(footer), footerLinkUrl == null ? "" : " <a href=\"%s\" style=\"color:%s;text-decoration:underline;\">%s</a>"
				.formatted(htmlEscape(footerLinkUrl), GOLD_TEXT, htmlEscape(footerLinkLabel)));
		return """
				<!doctype html>
				<html lang="en">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width,initial-scale=1">
				<meta name="color-scheme" content="light">
				<meta name="supported-color-schemes" content="light">
				<title>%1$s</title>
				</head>
				<body style="margin:0;padding:0;background:%2$s;">
				<div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">%3$s&#8203;&#847;&#8203;&#847;&#8203;&#847;&#8203;&#847;</div>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:%2$s;">
				<tr><td align="center" style="padding:32px 16px;">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">
				<tr><td style="padding:0 0 20px;">
				<table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
				<td style="vertical-align:middle;"><img src="cid:%4$s" width="40" height="40" alt="" style="display:block;border:0;"></td>
				<td style="vertical-align:middle;padding-left:12px;font-family:'Jost',%5$s;font-size:20px;font-weight:300;letter-spacing:6px;color:%6$s;">SEALEASE</td>
				</tr></table>
				</td></tr>
				<tr><td style="background:%7$s;border:1px solid %8$s;border-top:4px solid %9$s;">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
				<tr><td style="padding:36px 36px 32px;font-family:%5$s;font-size:15px;line-height:24px;color:%6$s;">
				%10$s</td></tr>
				</table>
				</td></tr>
				<tr><td style="padding:20px 4px 0;font-family:%5$s;font-size:12px;line-height:18px;color:%11$s;">
				<p style="margin:0;">SeaLease &middot; <a href="%12$s" style="color:%11$s;text-decoration:underline;">%13$s</a></p>
				%14$s</td></tr>
				</table>
				</td></tr>
				</table>
				</body>
				</html>
				""".formatted(htmlEscape(subject), CANVAS, htmlEscape(preheader == null ? "" : preheader), LOGO_CID, FONT,
				INK, SURFACE, BORDER, GOLD, body, MUTED, htmlEscape(siteUrl), htmlEscape(displayHost(siteUrl)), footerHtml);
	}

	private static String paragraphHtml(String text) {
		return "<p style=\"margin:0 0 16px;\">" + lines(text) + "</p>\n";
	}

	private static String codeHtml(String code) {
		return """
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px;">
				<tr><td align="center" style="background:%s;border:1px dashed %s;padding:18px 12px 18px 22px;font-family:%s;font-size:32px;line-height:40px;font-weight:700;letter-spacing:10px;color:%s;">%s</td></tr>
				</table>
				""".formatted(CANVAS, GOLD, MONO, INK, htmlEscape(code));
	}

	private static String buttonHtml(String label, String url) {
		return """
				<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px;">
				<tr><td style="background:%s;">
				<a href="%s" style="display:inline-block;padding:14px 28px;font-family:%s;font-size:15px;font-weight:600;line-height:20px;color:%s;text-decoration:none;">%s</a>
				</td></tr>
				</table>
				""".formatted(GOLD, htmlEscape(url), FONT, ON_GOLD, htmlEscape(label));
	}

	private static String detailsHtml(Map<String, String> rows) {
		StringBuilder html = new StringBuilder("""
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px;background:%s;border:1px solid %s;">
				""".formatted(CANVAS, BORDER));
		rows.forEach((label, value) -> html.append("""
				<tr><td style="padding:12px 16px 0;font-size:12px;line-height:16px;letter-spacing:1px;text-transform:uppercase;color:%s;">%s</td></tr>
				<tr><td style="padding:2px 16px 12px;font-family:%s;font-size:15px;line-height:22px;color:%s;word-break:break-all;">%s</td></tr>
				""".formatted(MUTED, htmlEscape(label), MONO, INK, htmlEscape(value))));
		return html.append("</table>\n").toString();
	}

	/** Escapes text and keeps its line breaks. */
	private static String lines(String text) {
		return htmlEscape(text).replace("\n", "<br>");
	}

	private static String displayHost(String url) {
		return url.replaceFirst("^https?://", "").replaceAll("/+$", "");
	}

	public static final class Builder {

		private final String subject;
		private final String siteUrl;
		private String preheader;
		private String greeting;
		private final List<Block> blocks = new ArrayList<>();
		private String footer;
		private String footerLinkLabel;
		private String footerLinkUrl;

		private Builder(String subject, String siteUrl) {
			this.subject = subject;
			this.siteUrl = siteUrl.replaceAll("/+$", "");
		}

		/** The preview line inbox lists show after the subject. */
		public Builder preheader(String text) {
			this.preheader = text;
			return this;
		}

		/** "Hello Asha," or, without a first name, "Hello,". */
		public Builder greeting(String firstName) {
			this.greeting = firstName == null || firstName.isBlank() ? "Hello," : "Hello " + firstName.strip() + ",";
			return this;
		}

		public Builder paragraph(String text) {
			blocks.add(new Paragraph(text));
			return this;
		}

		/** A one-time code, large and spaced out. */
		public Builder code(String code) {
			blocks.add(new Code(code));
			return this;
		}

		/** A gold button; the plain text shows "label: url". */
		public Builder button(String label, String url) {
			blocks.add(new Button(label, url));
			return this;
		}

		/** Labelled values (e.g. sign-in details); the plain text shows "label: value" lines. */
		public Builder details(Map<String, String> rows) {
			blocks.add(new Details(new LinkedHashMap<>(rows)));
			return this;
		}

		/** Small print under the content, e.g. "never share this code". */
		public Builder note(String text) {
			blocks.add(new Note(text));
			return this;
		}

		/** A line under the card, e.g. why the email was sent, with an optional link. */
		public Builder footer(String text, String linkLabel, String linkUrl) {
			this.footer = text;
			this.footerLinkLabel = linkLabel;
			this.footerLinkUrl = linkUrl;
			return this;
		}

		public BrandedEmail build() {
			return new BrandedEmail(this);
		}

	}

}
