package com.inventory.domain.port.out;

/**
 * Output port — Image storage abstraction.
 *
 * Implementations live in infrastructure/adapter/out/storage/.
 * Swap between local filesystem, S3, GCS, etc. without touching domain logic.
 */
public interface ImageStoragePort {

    /**
     * Stores an image and returns the URL/path where it can be accessed.
     *
     * @param filename   Original filename (used to derive the stored name).
     * @param content    Raw image bytes.
     * @param mimeType   MIME type of the image (e.g., "image/jpeg").
     * @return           Accessible URL or relative path.
     */
    String store(String filename, byte[] content, String mimeType);

    /**
     * Deletes an image by its stored URL/path.
     *
     * @param imageUrl  The URL/path previously returned by {@link #store}.
     */
    void delete(String imageUrl);
}
