package cn.edu.academic.support.storage;

public record UploadRequest(String kind,Long pairId,Long photoId,Long periodId,String stage,String label,
                            String title,String description,String filename,String mimeType,long size) {}
