package com.sealease.backend.agreement.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** An accepted agreement's PDF ends with the investor's signature image, their name and when they accepted. */
class AgreementSignatureRenderingTest {

	private final AgreementRenderer renderer = new AgreementRenderer();

	@Test
	void anAcceptedAgreementCarriesTheInvestorsSignature() throws IOException {
		byte[] signed = renderer.pdf("Lease Agreement", "AGR-2026-000001", "# Terms\nThe terms.", "footnote",
				new AgreementRenderer.Signature(signatureImage(), "Asha Investor", "Accepted on 3 November 2026, 10:00 IST"));
		byte[] unsigned = renderer.pdf("Lease Agreement", "AGR-2026-000001", "# Terms\nThe terms.", "footnote");

		// The logo is an image on both; the signed one has a second.
		assertThat(images(signed)).isEqualTo(images(unsigned) + 1);
	}

	@Test
	void withoutASignatureOnFileItSaysSoInstead() {
		byte[] pdf = renderer.pdf("Lease Agreement", "AGR-2026-000001", "The terms.", "footnote",
				new AgreementRenderer.Signature(null, "Asha Investor", "Accepted on 3 November 2026, 10:00 IST"));
		byte[] unsigned = renderer.pdf("Lease Agreement", "AGR-2026-000001", "The terms.", "footnote");
		assertThat(images(pdf)).isEqualTo(images(unsigned));
		assertThat(pdf.length).isGreaterThan(unsigned.length);
	}

	private static int images(byte[] pdf) {
		String raw = new String(pdf, StandardCharsets.ISO_8859_1);
		int count = 0;
		for (int i = raw.indexOf("/Subtype/Image"); i >= 0; i = raw.indexOf("/Subtype/Image", i + 1)) {
			count++;
		}
		for (int i = raw.indexOf("/Subtype /Image"); i >= 0; i = raw.indexOf("/Subtype /Image", i + 1)) {
			count++;
		}
		return count;
	}

	private static byte[] signatureImage() throws IOException {
		BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 200, 60);
		g.setColor(Color.BLUE);
		g.drawLine(10, 40, 190, 20);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

}
