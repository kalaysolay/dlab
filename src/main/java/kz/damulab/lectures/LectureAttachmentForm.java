package kz.damulab.lectures;

public class LectureAttachmentForm {

    private String title;
    private String url;
    private String mediaType = "link";
    private String storageKey;
    private String originalFileName;
    private String fileContentType;
    private Long fileSizeBytes;

    public LectureAttachmentForm() {
    }

    public LectureAttachmentForm(String title, String url, String mediaType) {
        this.title = title;
        this.url = url;
        this.mediaType = mediaType;
    }

    public LectureAttachmentForm(
            String title,
            String url,
            String mediaType,
            String storageKey,
            String originalFileName,
            String fileContentType,
            Long fileSizeBytes
    ) {
        this.title = title;
        this.url = url;
        this.mediaType = mediaType;
        this.storageKey = storageKey;
        this.originalFileName = originalFileName;
        this.fileContentType = fileContentType;
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getFileContentType() {
        return fileContentType;
    }

    public void setFileContentType(String fileContentType) {
        this.fileContentType = fileContentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }
}
