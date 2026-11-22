package app.wildro.community.community;

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

import app.wildro.community.community.CommunityRefusal.Code;

/**
 * The pictures of groups on the server's disk, under MEDIA_DIR, served by the
 * proxy at MEDIA_BASE_URL. Only WebP is taken: the frontend's server has already
 * scaled the upload to 512 px and dropped every metadata block (GPS included);
 * here the bytes are checked to be what they claim and small enough. The file
 * name is made from the content, so a changed picture is a new address and the
 * proxy may cache each one for good.
 */
@Component
public class LogoFiles {

	/** A 512 px WebP is a few dozen kilobytes; anything near this is not what the frontend sends. */
	static final int MAX_BYTES = 400 * 1024;

	private static final Logger log = LoggerFactory.getLogger(LogoFiles.class);

	private final Path dir;

	private final String baseUrl;

	LogoFiles(@Value("${wildro.media.dir:}") String dir, @Value("${wildro.media.base-url:}") String baseUrl) {
		this.dir = dir.isBlank() ? null : Path.of(dir);
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	/** Without a folder and an address no picture is taken, and none is shown. */
	boolean enabled() {
		return this.dir != null && !this.baseUrl.isBlank();
	}

	/** The public address of a stored picture, or null. */
	public String urlOf(String file) {
		return file == null || !enabled() ? null : this.baseUrl + "/" + file;
	}

	/** Checks and writes the picture; @return its file name */
	String store(long community, byte[] webp) {
		if (!enabled()) {
			throw new CommunityRefusal(Code.UNAVAILABLE, "pictures are not configured");
		}
		if (webp == null || webp.length < 30 || webp.length > MAX_BYTES || !isWebp(webp)) {
			throw new CommunityRefusal(Code.INVALID, "a WebP picture of at most " + MAX_BYTES + " bytes");
		}
		String name = community + "-" + HexFormat.of().formatHex(sha256(webp)).substring(0, 12) + ".webp";
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

	/** Best effort: a file left behind costs disk, never correctness. */
	void delete(String file) {
		if (file == null || !enabled()) {
			return;
		}
		try {
			Files.deleteIfExists(this.dir.resolve(file));
		}
		catch (IOException ex) {
			log.warn("Could not delete picture {}: {}", file, ex.getMessage());
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
