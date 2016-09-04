package io.metamorphic.fileservices;

import io.metamorphic.analysiscommons.models.DatasetInfo;

/**
 * Created by markmo on 1/09/2015.
 */
public class FileDatasetInfo extends DatasetInfo {

    private String filename;
    private String fileType;
    private FileParameters fileParameters;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public FileParameters getFileParameters() {
        return fileParameters;
    }

    public void setFileParameters(FileParameters fileParameters) {
        this.fileParameters = fileParameters;
    }
}
