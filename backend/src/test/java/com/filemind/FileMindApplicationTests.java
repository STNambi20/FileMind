package com.filemind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:filemind-context-loads;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/context-loads",
    "filemind.storage.uploads=target/test-storage/context-loads/uploads",
    "filemind.storage.chunks=target/test-storage/context-loads/chunks",
    "filemind.storage.manifests=target/test-storage/context-loads/manifests"
})
class FileMindApplicationTests {

    @Test
    void contextLoads() {
    }
}
