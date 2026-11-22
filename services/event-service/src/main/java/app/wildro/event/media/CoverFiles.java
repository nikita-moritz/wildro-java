package app.wildro.event.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The organisers' own pictures of their events, on the server's disk under
 * MEDIA_DIR, served by the proxy at MEDIA_BASE_URL (decided 2026-11-22). The
 * frontend's server has already cut the upload to 1600 by 900 and dropped
 * every metadata block; here the bytes are checked to be a WebP of modest size.
 * The name comes from the content, so a new picture is a new address and the
 * proxy may cache each one for good.
 *
 * The community service keeps its groups' pictures the same way, in its own
 * class: each service owns the files of its context. A third owner of pictures
 * is the point to move both into a media service.
 */
@Component
public class CoverFiles {

	/** A 1600 by 900 WebP of a photo is a few hundred kilobytes; this leaves room and no more. */
	public static final int MAX_BYTES = 900 * 1024;

	private static final Logger log = LoggerFactory.getLogger(CoverFiles.class);

	private final Path dir;

	private final String baseUrl;

	CoverFiles(@Value("${wildro.media.dir:}") String dir, @Value("${wildro.media.base-url:}") String baseUrl) {
		this.dir = dir.isBlank() ? null : Path.of(dir);
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	/** Without a folder and an address no picture is taken. */
	public boolean enabled() {
		return this.dir != null && !this.baseUrl.isBlank();
	}

	public String urlOf(String file) {
		return this.baseUrl + "/" + file;
	}

	/** Whether these bytes are a WebP this service takes. */
	public static boolean acceptable(byte[] webp) {
		return webp != null && webp.length >= 30 && webp.length <= MAX_BYTES && isWebp(webp);
	}

	/** Writes the picture of an event; @return its file name */
	public String store(long event, byte[] webp) {
		String name = event + "-" + HexFormat.of().formatHex(sha256(webp)).substring(0, 12) + ".webp";
		Path target = this.dir.resolve(name);
		try {
			Files.createDirectories(this.dir);
			if (!Files.exists(target)) {
				// Written beside and moved in, so the proxy never serves half a file.
				Path partial = Files.createTempFile(this.dir, ".upload-", ".part");
				Files.write(partial, webp);
				try {
					Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
				}
				catch (AtomicMoveNotSupportedException ex) {
					Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			return name;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Deletes a file once the current transaction has committed: a change that is
	 * rolled back must still find its picture. Best effort; a file left behind
	 * costs disk, never correctness.
	 */
	public void deleteAfterCommit(String file) {
		if (file == null || !enabled()) {
			return;
		}
		Runnable delete = () -> {
			try {
				Files.deleteIfExists(this.dir.resolve(file));
			}
			catch (IOException ex) {
				log.warn("Could not delete picture {}: {}", file, ex.getMessage());
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					delete.run();
				}
			});
		}
		else {
			delete.run();
		}
	}

	/** RIFF container, WEBP form, and one of the three chunk kinds a WebP starts with. */
	static boolean isWebp(byte[] bytes) {
		return bytes.length >= 16 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")
				&& (ascii(bytes, 12, "VP8 ") || ascii(bytes, 12, "VP8L") || ascii(bytes, 12, "VP8X"));
	}

	private static boolean ascii(byte[] bytes, int at, String text) {
		for (int i = 0; i < text.length(); i++) {
			if (bytes[at + i] != text.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
