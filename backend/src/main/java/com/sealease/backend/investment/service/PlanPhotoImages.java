package com.sealease.backend.investment.service;

import com.sealease.backend.document.service.DocumentContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Plan photos as the marketplace shows them: at most {@value #MAX_SIDE} px on the longer side, as JPEG. Uploads are
 * often multi-megabyte camera PNGs; the stored original is kept and the smaller copy is made on first view. Copies
 * are kept in memory (a photo never changes under its document id), so later views skip storage and decryption.
 */
@Component
public class PlanPhotoImages {

	static final int MAX_SIDE = 1600;
	private static final float QUALITY = 0.82f;
	/** About 15–30 MB at a few hundred KB each: every photo of a few dozen listings. */
	private static final int CACHED = 96;
	private static final Logger log = LoggerFactory.getLogger(PlanPhotoImages.class);

	private final Map<UUID, DocumentContent> cache = new LinkedHashMap<>(CACHED, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, DocumentContent> eldest) {
			return size() > CACHED;
		}
	};

	public DocumentContent get(UUID documentId, Supplier<DocumentContent> original) {
		synchronized (cache) {
			DocumentContent cached = cache.get(documentId);
			if (cached != null) {
				return cached;
			}
		}
		DocumentContent shown = shrink(original.get());
		synchronized (cache) {
			cache.put(documentId, shown);
		}
		return shown;
	}

	/** The photo as a JPEG no larger than {@value #MAX_SIDE} px, or unchanged if that would not make it smaller. */
	static DocumentContent shrink(DocumentContent photo) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo.bytes()));
			if (image == null) {
				return photo;
			}
			double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(image.getWidth(), image.getHeight()));
			int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
			int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
			// JPEG has no transparency: transparent PNG areas become white.
			BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = rgb.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, width, height);
				g.drawImage(image, 0, 0, width, height, null);
			}
			finally {
				g.dispose();
			}
			byte[] jpeg = jpeg(rgb);
			if (jpeg.length >= photo.bytes().length) {
				return photo;
			}
			return new DocumentContent(photo.id(), photo.ownerUserId(), photo.purpose(), "image/jpeg", jpeg);
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not shrink plan photo {}; serving the original", photo.id(), ex);
			return photo;
		}
	}

	private static byte[] jpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(QUALITY);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

}
