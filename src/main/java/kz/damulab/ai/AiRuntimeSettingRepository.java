package kz.damulab.ai;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRuntimeSettingRepository extends JpaRepository<AiRuntimeSetting, AiUsageType> {
}
