package com.noteshadow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class StorageLocationInfoTest {
    @Test public void defaultsDescribeExistingLayoutAndUsage() {
        StorageCategoryUsage usage = new StorageCategoryUsage(10L, 15L, 20L, 30L);
        List<StorageLocationInfo> locations = StorageLocationInfo.defaults(usage, true);

        assertEquals(4, locations.size());
        assertEquals(StorageLocationInfo.Kind.RECORDINGS, locations.get(0).kind());
        assertEquals("录音", locations.get(0).label());
        assertEquals(20L, locations.get(0).bytes());
        assertTrue(locations.get(0).systemOpenable());

        assertEquals("转写文本", locations.get(1).label());
        assertTrue(locations.get(1).pathDescription().contains("下载/NoteShadow"));
        assertEquals(10L, locations.get(1).bytes());
        assertTrue(locations.get(1).systemOpenable());

        assertEquals("课堂笔记", locations.get(2).label());
        assertEquals(15L, locations.get(2).bytes());
        assertTrue(locations.get(2).systemOpenable());

        assertEquals("笔记图片", locations.get(3).label());
        assertTrue(locations.get(3).pathDescription().contains("附件"));
        assertEquals(30L, locations.get(3).bytes());
        assertFalse(locations.get(3).systemOpenable());
    }

    @Test public void defaultsAreSafeForMissingMeasurementsAndNullDirectory() {
        List<StorageLocationInfo> locations = StorageLocationInfo.defaults(null, true);
        for (StorageLocationInfo location : locations) {
            assertEquals(0L, location.bytes());
            assertFalse(location.pathDescription().isEmpty());
        }
    }

    @Test public void returnedListCannotBeMutated() {
        List<StorageLocationInfo> locations = StorageLocationInfo.defaults(
                new StorageCategoryUsage(1L, 2L, 3L, 4L), true);
        try {
            locations.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("storage location list must be immutable");
    }

    @Test public void modelClampsNegativeBytes() {
        StorageLocationInfo location = new StorageLocationInfo(
                StorageLocationInfo.Kind.TRANSCRIPTS, "文本", "path", true, -1L);
        assertEquals(0L, location.getBytes());
    }

    @Test public void olderAndroidDoesNotPromiseFixedPublicFolders() {
        List<StorageLocationInfo> locations = StorageLocationInfo.defaults(
                new StorageCategoryUsage(1L, 2L, 3L, 4L), false);
        assertFalse(locations.get(0).systemOpenable());
        assertTrue(locations.get(0).pathDescription().contains("不创建固定位置"));
        assertFalse(locations.get(1).systemOpenable());
        assertTrue(locations.get(1).pathDescription().contains("不支持固定下载目录导出"));
        assertFalse(locations.get(2).systemOpenable());
    }
}
