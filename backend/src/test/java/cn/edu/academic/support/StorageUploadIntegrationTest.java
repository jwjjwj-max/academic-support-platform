package cn.edu.academic.support;

import cn.edu.academic.support.storage.*;
import cn.edu.academic.support.grade.service.GradeService;
import cn.edu.academic.support.material.service.*;
import cn.edu.academic.support.moral.service.MoralFileService;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="app.storage.direct-upload=true") @ActiveProfiles("test")
class StorageUploadIntegrationTest {
    @TestConfiguration static class Config {
        @Bean @Primary ObjectStorage testObjects(){return mock(CosObjectStorage.class);}
    }
    @Autowired UploadService service; @Autowired ObjectStorage objects; @Autowired JdbcTemplate jdbc;
    @MockitoBean MaterialService materials; @MockitoBean GradeService grades; @MockitoBean MoralFileService moral;
    private final AuthenticatedUser admin=new AuthenticatedUser(1L,"admin","admin","ADMIN",0);
    private final AuthenticatedUser student=new AuthenticatedUser(2L,"student","student","STUDENT",0);
    private UploadRequest request(long size){return new UploadRequest("GRADE",null,null,1L,"BEFORE","test",null,null,"test.csv","text/csv",size);}
    @BeforeEach void resetState(){jdbc.update("DELETE FROM storage_upload");reset(objects,grades,materials,moral);when(objects.signedPut(anyString(),anyLong(),anyString())).thenReturn("https://example.invalid/upload");}
    @Test void confirmationChecksObjectAndIsIdempotent()throws Exception {
        byte[] bytes="studentNo,gpa\n1,3.0\n".getBytes();
        when(objects.signedPut(anyString(),anyLong(),anyString())).thenReturn("https://example.invalid/test");
        when(objects.size(anyString())).thenReturn((long)bytes.length);
        when(objects.open(anyString())).thenAnswer(i->new ByteArrayInputStream(bytes));
        String id=(String)service.begin(admin,request(bytes.length)).get("id");
        service.finish(admin,id,new MockHttpServletRequest());service.finish(admin,id,new MockHttpServletRequest());
        verify(grades,times(1)).preview(eq(admin),eq(1L),eq("BEFORE"),eq("test"),any(),any());
        assertThat(jdbc.queryForObject("SELECT status FROM storage_upload WHERE id=?",String.class,id)).isEqualTo("DONE");
    }
    @Test void studentsCannotSignGradeUploads(){assertThatThrownBy(()->service.begin(student,request(3))).isInstanceOf(BusinessException.class);verifyNoInteractions(objects);}
    @Test void oversizeRejectedBeforeSigning(){assertThatThrownBy(()->service.begin(admin,request(20971521))).isInstanceOf(BusinessException.class);verifyNoInteractions(objects);}
    @Test void anotherUserCannotConfirm()throws Exception {
        String id=(String)service.begin(admin,request(3)).get("id");
        assertThatThrownBy(()->service.finish(student,id,new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
        verify(objects,never()).open(anyString());
    }
    @Test void expiredSessionCannotCreateBusinessRecord()throws Exception {
        String id=(String)service.begin(admin,request(3)).get("id");jdbc.update("UPDATE storage_upload SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),id);
        assertThatThrownBy(()->service.finish(admin,id,new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
        verify(grades,never()).preview(any(),any(),any(),any(),any(),any());
    }
    @Test void mismatchedObjectSizeCannotCreateBusinessRecord()throws Exception {
        String id=(String)service.begin(admin,request(3)).get("id");when(objects.size(anyString())).thenReturn(4L);
        assertThatThrownBy(()->service.finish(admin,id,new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
        verify(grades,never()).preview(any(),any(),any(),any(),any(),any());
    }
}
