package cn.edu.academic.support.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;

final class StoredMultipartFile implements MultipartFile {
    private final UploadRequest request; private final byte[] bytes;
    StoredMultipartFile(UploadRequest request,byte[] bytes){this.request=request;this.bytes=bytes;}
    public String getName(){return "file";}
    public String getOriginalFilename(){return request.filename();}
    public String getContentType(){return request.mimeType();}
    public boolean isEmpty(){return bytes.length==0;}
    public long getSize(){return bytes.length;}
    public byte[] getBytes(){return bytes.clone();}
    public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
    public void transferTo(File target)throws IOException{Files.write(target.toPath(),bytes);}
}
