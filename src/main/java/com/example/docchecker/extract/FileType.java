package com.example.docchecker.extract;

public enum FileType {
    PNG, JPEG, PDF, DOCX;

    public boolean isImage() {
        return this == PNG || this == JPEG;
    }
}
