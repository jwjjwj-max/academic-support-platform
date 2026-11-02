package cn.edu.academic.support.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.Date;

@Component
@ConditionalOnProperty(name="app.storage.provider", havingValue="cos")
public class CosObjectStorage implements ObjectStorage {
    private final COSClient client;
    private final String bucket;
    private final boolean directRead;
    public CosObjectStorage(@Value("${app.storage.cos.secret-id}") String id,
                            @Value("${app.storage.cos.secret-key}") String secret,
                            @Value("${app.storage.cos.bucket}") String bucket,
                            @Value("${app.storage.cos.region}") String region,
                            @Value("${app.storage.direct-read:false}") boolean directRead) {
        if(id.isBlank() || secret.isBlank() || bucket.isBlank() || region.isBlank())
            throw new IllegalStateException("COS configuration is incomplete");
        ClientConfig config=new ClientConfig(new Region(region));
        config.setHttpProtocol(HttpProtocol.https);
        config.setConnectionTimeout(10000); config.setSocketTimeout(30000);
        client=new COSClient(new BasicCOSCredentials(id,secret),config);
        this.bucket=bucket; this.directRead=directRead;
    }
    public void put(String key, InputStream in, long size, String mime) throws IOException {
        StorageKeys.validate(key);
        ObjectMetadata metadata=new ObjectMetadata(); metadata.setContentLength(size); metadata.setContentType(mime);
        metadata.setCacheControl("private, no-store");
        // LHCOS: omit StorageClass; STANDARD is not supported by this product.
        try { client.putObject(new PutObjectRequest(bucket,key,in,metadata)); }
        catch(RuntimeException e) { throw new IOException("Object upload failed"); }
    }
    public InputStream open(String key) throws IOException {
        StorageKeys.validate(key);
        try { return client.getObject(bucket,key).getObjectContent(); }
        catch(RuntimeException e) { throw new IOException("Object read failed"); }
    }
    public long size(String key) throws IOException {
        StorageKeys.validate(key);
        try { return client.getObjectMetadata(bucket,key).getContentLength(); }
        catch(RuntimeException e) { throw new IOException("Object metadata failed"); }
    }
    public void delete(String key) {
        StorageKeys.validate(key);
        try { client.deleteObject(bucket,key); }
        catch(RuntimeException e) { throw new IllegalStateException("Object cleanup failed"); }
    }
    public String signedPut(String key,long size,String mime) {
        StorageKeys.validate(key);
        if (!key.startsWith("pending/")) throw new IllegalArgumentException("Upload target must be temporary");
        return client.generatePresignedUrl(bucket,key,new Date(System.currentTimeMillis()+300000),HttpMethodName.PUT,
                java.util.Map.of("content-type",mime,"content-length",Long.toString(size)),java.util.Map.of()).toString();
    }
    public String signedGet(String key) {
        StorageKeys.validate(key);
        return directRead ? client.generatePresignedUrl(bucket,key,new Date(System.currentTimeMillis()+60000),HttpMethodName.GET).toString() : null;
    }
    @PreDestroy public void close() { client.shutdown(); }
}
