package com.sealease.backend.investment.service;

import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.service.DocumentContent;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPhotoImagesTest {

	@Test
	void largePhotosAreServedAsSmallerJpegs() throws IOException {
		DocumentContent original = photo(png(4000, 3000));

		DocumentContent shown = PlanPhotoImages.shrink(original);

		assertThat(shown.contentType()).isEqualTo("image/jpeg");
		assertThat(shown.bytes().length).isLessThan(original.bytes().length);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(shown.bytes()));
		assertThat(image.getWidth()).isEqualTo(PlanPhotoImages.MAX_SIDE);
		assertThat(image.getHeight()).isEqualTo(1200);
	}

	@Test
	void photosThatWouldNotGetSmallerAndUnreadableFilesAreServedAsUploaded() throws IOException {
		DocumentContent tiny = photo(png(1, 1));
		assertThat(PlanPhotoImages.shrink(tiny)).isSameAs(tiny);
		DocumentContent broken = photo(new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
		assertThat(PlanPhotoImages.shrink(broken)).isSameAs(broken);
	}

	@Test
	void eachPhotoIsLoadedOnce() throws IOException {
		PlanPhotoImages images = new PlanPhotoImages();
		UUID id = UUID.randomUUID();
		DocumentContent original = photo(png(2000, 1000));
		AtomicInteger loads = new AtomicInteger();

		DocumentContent first = images.get(id, () -> {
			loads.incrementAndGet();
			return original;
		});
		DocumentContent second = images.get(id, () -> {
			loads.incrementAndGet();
			return original;
		});

		assertThat(loads).hasValue(1);
		assertThat(second).isSameAs(first);
	}

	/** A noisy image, so it compresses like a photo rather than a flat colour. */
	private static byte[] png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Random random = new Random(1);
		for (int y = 0; y < height; y += 4) {
			for (int x = 0; x < width; x += 4) {
				int rgb = random.nextInt(0xFFFFFF);
				for (int dy = 0; dy < 4 && y + dy < height; dy++) {
					for (int dx = 0; dx < 4 && x + dx < width; dx++) {
						image.setRGB(x + dx, y + dy, rgb);
					}
				}
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static DocumentContent photo(byte[] bytes) {
		return new DocumentContent(UUID.randomUUID(), UUID.randomUUID(), DocumentPurpose.PLAN_PHOTO, "image/png", bytes);
	}

}
