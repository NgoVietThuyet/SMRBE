package vn.com.d2s.smr.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.entity.mt.MeetingAuditLog;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingMessage;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMappingTest {

    private static final Map<Class<?>, String> EXPECTED_TABLES = Map.of(
            AdAccount.class, "AdAccounts",
            CmFile.class, "CmFiles",
            MdOrganize.class, "MdOrganizes",
            MdTitle.class, "MdTitles",
            MeetingAuditLog.class, "MeetingAuditLogs",
            MeetingInfo.class, "MeetingInfos",
            MeetingMessage.class, "MeetingMessages",
            MeetingPersonal.class, "MeetingPersonals",
            MeetingTask.class, "MeetingTasks",
            MeetingTaskShare.class, "MeetingTaskShares"
    );

    @Test
    void baseEntityIsMappedSuperclass() {
        assertThat(BaseEntity.class).hasAnnotation(MappedSuperclass.class);
    }

    @Test
    void allDotNetEntitiesHaveExplicitJpaTableMappings() {
        assertThat(EXPECTED_TABLES).hasSize(10);

        EXPECTED_TABLES.forEach((type, tableName) -> {
            assertThat(type).hasAnnotation(Entity.class);
            assertThat(type.getAnnotation(Table.class).name()).isEqualTo(tableName);
        });
    }
}

