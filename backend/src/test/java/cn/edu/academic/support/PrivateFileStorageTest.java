package cn.edu.academic.support;

import cn.edu.academic.support.storage.*;
import cn.edu.academic.support.material.service.PrivateFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateFileStorageTest {
    private final ObjectStorage objects=mock(ObjectStorage.class);
    private final PrivateFileStorage files=new PrivateFileStorage(objects,5242880,20971520,20971520);
    @Test void invalidImageWithValidMagicCannotBeStored(){assertThatThrownBy(()->files.storePhoto(new MockMultipartFile("file","fake.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,1,2,3}))).hasMessageContaining("照片");verifyNoInteractions(objects);}
    @Test void fakeExcelZipCannotBeStored(){assertThatThrownBy(()->files.storeMoral(new MockMultipartFile("file","fake.xlsx","application/octet-stream",new byte[]{80,75,3,4,0,1}))).hasMessageContaining("Excel");verifyNoInteractions(objects);}
    @Test void csvBinaryAfterHeaderCannotBeStored(){assertThatThrownBy(()->files.storeGrade(new MockMultipartFile("file","fake.csv","text/csv","studentNo,gpa\nabc,3.0\0".getBytes()))).hasMessageContaining("CSV");verifyNoInteractions(objects);}
    @Test void downloadedBodyMustMatchHead()throws Exception{when(objects.size("grades/test.csv")).thenReturn(5L);when(objects.open("grades/test.csv")).thenReturn(new ByteArrayInputStream(new byte[6]));assertThatThrownBy(()->files.read("grades/test.csv")).hasMessageContaining("读取失败");}
    @Test void localKeysCannotEscapeRoot()throws Exception{var root=java.nio.file.Files.createTempDirectory("private-test");var local=new LocalObjectStorage(root.toString());assertThatThrownBy(()->local.open("../secret")).isInstanceOf(IllegalArgumentException.class);java.nio.file.Files.delete(root);}
}
