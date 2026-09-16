package pl.michalbzowski.windband.application.dto.composition;

/**
 * One extracted file from a ZIP archive (US-2.3). Returned by
 * {@code POST /bands/{bandId}/compositions/{cid}/files/{fileId}/expand} so the
 * UI can display individual parts for instrument mapping in Epic 4.
 *
 * @param entryName   relative path of the file inside the ZIP (e.g. "trumpet/part-1.pdf")
 * @param mimeType    detected MIME type (from file extension heuristic)
 * @param sizeBytes   content length of this specific entry
 * @param isPdf       true when the entry is a PDF (usertype for page-range assignment in Epic 4)
 */
public record ZipEntryDto(
        String entryName,
        String mimeType,
        long sizeBytes,
        boolean isPdf) {
}
