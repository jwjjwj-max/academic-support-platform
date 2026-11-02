package cn.edu.academic.support.storage;

import org.springframework.scheduling.annotation.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;

@Configuration @EnableScheduling
public class UploadCleanup {
    private final JdbcTemplate jdbc; private final ObjectStorage objects;
    public UploadCleanup(JdbcTemplate jdbc,ObjectStorage objects){this.jdbc=jdbc;this.objects=objects;}
    @Scheduled(fixedDelay=3600000,initialDelay=3600000) @Transactional
    public void clean() {
        // Only temporary keys; no historical photos, original grades or soft-deleted moral files.
        var rows=jdbc.queryForList("SELECT id,object_key,status FROM storage_upload WHERE expires_at<? FOR UPDATE",Timestamp.from(Instant.now()));
        for(var row:rows) {
            try {
                objects.delete((String)row.get("object_key"));
                jdbc.update("DELETE FROM storage_upload WHERE id=? AND expires_at<?",row.get("id"),Timestamp.from(Instant.now().minusSeconds(7*86400)));
            } catch(RuntimeException e) { /* Keep the row for the next cleanup. Never log signed URLs or SDK exceptions. */ }
        }
    }
}
